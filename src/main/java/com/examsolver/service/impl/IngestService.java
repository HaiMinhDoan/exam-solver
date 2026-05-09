package com.examsolver.service.impl;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.JobSubmittedResponse;
import com.examsolver.entity.Customer;
import com.examsolver.entity.QuestionBank;
import com.examsolver.entity.QuestionJob;
import com.examsolver.exception.BusinessException;
import com.examsolver.kafka.KafkaProducerService;
import com.examsolver.repository.CustomerRepository;
import com.examsolver.repository.QuestionJobRepository;
import com.examsolver.util.QuestionHashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Xử lý phase 1: nhận câu hỏi, lưu DB, publish Kafka, trả 202 ngay.
 *
 * KHÔNG gọi AI ở đây. KHÔNG block chờ kết quả.
 * Toàn bộ logic giải bài nằm trong QuestionExecutorService (Kafka consumer).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestService {

    private final CustomerRepository customerRepository;
    private final QuestionJobRepository questionJobRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    /**
     * Nhận câu hỏi → validate → lưu QuestionJob → fire Kafka → return jobId.
     * Transaction commit TRƯỚC KHI fire Kafka để đảm bảo job luôn tồn tại trong DB
     * khi executor nhận được event.
     */
    @Transactional
    public JobSubmittedResponse ingest(SolveRequest request) {
        // 1. Validate customer
        Customer customer = validateCustomer(request.getSession().getEmail());

        // 2. Tính hash (dùng để tra bank trong executor)
        String questionHash = QuestionHashUtil.computeHash(
                request.getQuestion().getText(), request.getQuestion().getOptions());

        // 3. Serialize options sang JSON để lưu
        String optionsJson = serializeOptions(request);

        // 4. Tạo và lưu QuestionJob
        QuestionJob job = QuestionJob.builder()
                .questionId(request.getQuestionId())
                .questionHash(questionHash)
                .customerEmail(customer.getEmail())
                .examCode(request.getSession().getExamCode())
                .subjectCode(request.getSession().getSubjectCode())
                .deviceId(request.getSession().getDeviceId())
                .questionNumber(request.getQuestion().getNumber())
                .questionType(QuestionBank.QuestionType.valueOf(
                        request.getQuestion().getQuestionType().toUpperCase()))
                .questionText(request.getQuestion().getText())
                .optionsJson(optionsJson)
                .screenshotBase64(request.getQuestion().getScreenshotBase64())
                .capturedAt(parseCapturedAt(request.getCapturedAt()))
                .status(QuestionJob.JobStatus.PENDING)
                .build();

        QuestionJob saved = questionJobRepository.save(job);
        log.info("Job [{}] saved for customer [{}] question [{}]",
                saved.getId(), customer.getEmail(), request.getQuestionId());

        // 5. Publish Kafka SAU KHI transaction commit (gọi sau @Transactional return)
        //    Dùng TransactionSynchronization để đảm bảo thứ tự
        publishAfterCommit(saved);

        return JobSubmittedResponse.builder()
                .jobId(saved.getId())
                .questionId(request.getQuestionId())
                .status(saved.getStatus().name())
                .message("Question received. Processing asynchronously.")
                .build();
    }

    /**
     * Publish Kafka trong cùng thread nhưng sau khi transaction đã commit.
     * Spring @Transactional commit cuối method → event fire sau khi DB đã có job.
     *
     * Nếu Kafka fail: job vẫn PENDING, retry scheduler sẽ re-queue trong vài phút.
     */
    private void publishAfterCommit(QuestionJob job) {
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kafkaProducerService.publishQuestionSubmitted(job);
                    }
                });
    }

    private Customer validateCustomer(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Email không tồn tại: " + email));
        if (!customer.hasValidAccess()) {
            if (!customer.isActive()) throw new BusinessException("Tài khoản đã bị vô hiệu hóa.");
            throw new BusinessException("Tài khoản hết hạn. Vui lòng liên hệ admin.");
        }
        return customer;
    }

    private String serializeOptions(SolveRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getQuestion().getOptions());
        } catch (Exception e) {
            log.warn("Failed to serialize options: {}", e.getMessage());
            return "[]";
        }
    }

    private LocalDateTime parseCapturedAt(String capturedAt) {
        try {
            return LocalDateTime.parse(capturedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}