package com.examsolver.kafka;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.entity.*;
import com.examsolver.repository.*;
import com.examsolver.service.ai.AiSolverService;
import com.examsolver.service.impl.PromptVersionService;
import com.examsolver.service.impl.QuestionBankService;
import com.examsolver.util.QuestionHashUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Kafka consumer xử lý câu hỏi bất đồng bộ.
 *
 * Luồng:
 *   1. Nhận event từ topic question.submitted.
 *   2. Load QuestionJob từ DB theo jobId.
 *   3. Mark PROCESSING.
 *   4. Tra cứu QuestionBank trước.
 *   5. Nếu miss và AI mode bật → gọi Claude AI.
 *   6. Lưu kết quả: cập nhật job, lưu QuestionBank, lưu QuestionRecord.
 *   7. ACK message.
 *
 * Nếu lỗi: retry theo cấu hình Kafka (backoff), sau đó mark FAILED trong DB.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionExecutorService {

    private final QuestionJobRepository questionJobRepository;
    private final CustomerRepository customerRepository;
    private final ExamSessionRepository examSessionRepository;
    private final QuestionRecordRepository questionRecordRepository;
    private final QuestionBankService questionBankService;
    private final PromptVersionService promptVersionService;
    private final AiSolverService aiSolverService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics        = "${kafka.topics.question-submitted:question.submitted}",
            groupId       = "${kafka.consumer.group-id:exam-executor}",
            concurrency   = "${kafka.consumer.concurrency:3}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleQuestionSubmitted(
            ConsumerRecord<String, QuestionSubmittedEvent> record,
            Acknowledgment ack) {

        QuestionSubmittedEvent event = record.value();
        log.info("Executor received jobId=[{}] questionId=[{}] from partition [{}]",
                event.getJobId(), event.getQuestionId(), record.partition());

        QuestionJob job = questionJobRepository.findById(event.getJobId()).orElse(null);
        if (job == null) {
            log.warn("Job [{}] not found in DB — already deleted or invalid event", event.getJobId());
            ack.acknowledge();
            return;
        }

        // Bỏ qua nếu đã xử lý (duplicate delivery)
        if (job.getStatus() == QuestionJob.JobStatus.DONE ||
                job.getStatus() == QuestionJob.JobStatus.SKIPPED) {
            log.debug("Job [{}] already in terminal state [{}], skipping", job.getId(), job.getStatus());
            ack.acknowledge();
            return;
        }

        try {
            markProcessing(job);
            processJob(job);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing jobId=[{}]: {}", job.getId(), e.getMessage(), e);
            handleFailure(job, e.getMessage());
            ack.acknowledge(); // Vẫn ACK — retry do DB scheduler, không để Kafka re-deliver liên tục
        }
    }

    // ─── Core processing ──────────────────────────────────────────────────────

    private void processJob(QuestionJob job) throws Exception {
        long start = System.currentTimeMillis();

        // Reconstruct SolveRequest từ dữ liệu đã lưu trong job
        SolveRequest request = buildSolveRequest(job);

        // 1. Tra cứu ngân hàng câu hỏi (luôn thực hiện, bất kể AI mode)
        Optional<QuestionBank> bankHit = questionBankService.findAnswer(request);

        if (bankHit.isPresent()) {
            QuestionBank bank = bankHit.get();
            long elapsed = System.currentTimeMillis() - start;
            log.info("Job [{}] resolved from bank in {}ms → [{}]", job.getId(), elapsed, bank.getAnswer());
            markDone(job, bank.getAnswer(), QuestionRecord.AnswerSource.BANK, null, elapsed);
            saveQuestionRecord(job, bank.getAnswer(), QuestionRecord.AnswerSource.BANK, bank, null, elapsed);
            return;
        }

        // 2. Không có trong bank — kiểm tra AI mode của customer
        Customer customer = customerRepository.findByEmail(job.getCustomerEmail()).orElse(null);
        if (customer == null || !customer.isAiModeEnabled()) {
            log.info("Job [{}] skipped — AI mode off or customer not found", job.getId());
            markSkipped(job, "Question not in bank and AI mode is disabled");
            return;
        }

        // 3. Gọi AI
        String qType = job.getQuestionType().name();
        PromptVersion promptVersion = promptVersionService.getActive(qType)
                .orElseGet(() -> promptVersionService.getActive("SYSTEM").orElse(null));

        var aiResponse = aiSolverService.solveQuestion(request, promptVersion);
        long elapsed = System.currentTimeMillis() - start;

        if (!aiResponse.isSuccess() || aiResponse.getAnswer() == null) {
            throw new RuntimeException("AI returned no answer: " + aiResponse.getMessage());
        }

        log.info("Job [{}] resolved by AI in {}ms → [{}]", job.getId(), elapsed, aiResponse.getAnswer());

        // 4. Lưu vào ngân hàng
        Long pvId = promptVersion != null ? promptVersion.getId() : null;
        QuestionBank savedBank = questionBankService.saveIfAbsent(request, aiResponse.getAnswer(), pvId);

        // 5. Cập nhật job và lưu record
        markDone(job, aiResponse.getAnswer(), QuestionRecord.AnswerSource.AI, pvId, elapsed);
        saveQuestionRecord(job, aiResponse.getAnswer(), QuestionRecord.AnswerSource.AI, savedBank, pvId, elapsed);
    }

    // ─── State transitions ────────────────────────────────────────────────────

    private void markProcessing(QuestionJob job) {
        job.setStatus(QuestionJob.JobStatus.PROCESSING);
        job.setProcessingStartedAt(LocalDateTime.now());
        questionJobRepository.save(job);
    }

    private void markDone(QuestionJob job, String answer, QuestionRecord.AnswerSource source,
                          Long promptVersionId, long processingMs) {
        job.setStatus(QuestionJob.JobStatus.DONE);
        job.setAnswer(answer);
        job.setAnswerSource(source);
        job.setProcessingFinishedAt(LocalDateTime.now());
        job.setProcessingTimeMs(processingMs);
        questionJobRepository.save(job);
    }

    private void markSkipped(QuestionJob job, String reason) {
        job.setStatus(QuestionJob.JobStatus.SKIPPED);
        job.setErrorMessage(reason);
        job.setProcessingFinishedAt(LocalDateTime.now());
        questionJobRepository.save(job);
    }

    private void handleFailure(QuestionJob job, String errorMessage) {
        job.setRetryCount(job.getRetryCount() + 1);
        if (job.canRetry()) {
            // Reset về PENDING để retry scheduler re-queue
            job.setStatus(QuestionJob.JobStatus.PENDING);
            log.warn("Job [{}] failed, retry {}/{}", job.getId(), job.getRetryCount(), job.getMaxRetries());
        } else {
            job.setStatus(QuestionJob.JobStatus.FAILED);
            log.error("Job [{}] exhausted retries, marking FAILED", job.getId());
        }
        job.setErrorMessage(errorMessage);
        job.setProcessingFinishedAt(LocalDateTime.now());
        questionJobRepository.save(job);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Rebuild SolveRequest từ dữ liệu đã lưu trong QuestionJob.
     * Cần thiết vì QuestionBankService và AiSolverService đều nhận SolveRequest.
     */
    private SolveRequest buildSolveRequest(QuestionJob job) throws Exception {
        List<SolveRequest.OptionData> options = objectMapper.readValue(
                job.getOptionsJson(), new TypeReference<>() {});

        SolveRequest.SessionInfo session = new SolveRequest.SessionInfo();
        session.setEmail(job.getCustomerEmail());
        session.setExamCode(job.getExamCode());
        session.setSubjectCode(job.getSubjectCode());
        session.setDeviceId(job.getDeviceId() != null ? job.getDeviceId() : "unknown");

        SolveRequest.QuestionData questionData = new SolveRequest.QuestionData();
        questionData.setNumber(job.getQuestionNumber());
        questionData.setQuestionType(job.getQuestionType().name());
        questionData.setText(job.getQuestionText());
        questionData.setOptions(options);
        questionData.setScreenshotBase64(job.getScreenshotBase64());

        SolveRequest request = new SolveRequest();
        request.setQuestionId(job.getQuestionId());
        request.setSession(session);
        request.setQuestion(questionData);
        request.setCapturedAt(job.getCapturedAt() != null
                ? job.getCapturedAt().toString() : LocalDateTime.now().toString());
        return request;
    }

    private void saveQuestionRecord(QuestionJob job, String answer,
                                    QuestionRecord.AnswerSource source,
                                    QuestionBank bankRef, Long promptVersionId,
                                    long processingMs) {
        try {
            // Lấy hoặc tạo ExamSession
            Customer customer = customerRepository.findByEmail(job.getCustomerEmail()).orElse(null);
            if (customer == null) return;

            ExamSession session = examSessionRepository
                    .findByCustomerIdAndExamCodeAndSubjectCodeAndDeviceId(
                            customer.getId(), job.getExamCode(), job.getSubjectCode(),
                            job.getDeviceId() != null ? job.getDeviceId() : "unknown")
                    .orElseGet(() -> examSessionRepository.save(ExamSession.builder()
                            .customer(customer)
                            .examCode(job.getExamCode())
                            .subjectCode(job.getSubjectCode())
                            .deviceId(job.getDeviceId())
                            .build()));

            QuestionRecord rec = QuestionRecord.builder()
                    .examSession(session)
                    .questionBank(bankRef)
                    .questionHash(job.getQuestionHash())
                    .questionNumber(job.getQuestionNumber())
                    .questionType(job.getQuestionType())
                    .questionText(job.getQuestionText())
                    .optionsJson(job.getOptionsJson())
                    .answer(answer)
                    .autoClick(true)
                    .answerSource(source)
                    .aiModelUsed(source == QuestionRecord.AnswerSource.AI
                            ? aiSolverService.getProviderName() : null)
                    .promptVersionId(promptVersionId)
                    .processingTimeMs(processingMs)
                    .capturedAt(job.getCapturedAt())
                    .hasScreenshot(job.getScreenshotBase64() != null)
                    .success(true)
                    .build();

            questionRecordRepository.save(rec);
        } catch (Exception e) {
            log.warn("Failed to save QuestionRecord for jobId=[{}]: {}", job.getId(), e.getMessage());
        }
    }
}