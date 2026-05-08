package com.examsolver.service.impl;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.SolveResponse;
import com.examsolver.entity.*;
import com.examsolver.exception.BusinessException;
import com.examsolver.repository.*;
import com.examsolver.service.ai.AiSolverService;
import com.examsolver.util.QuestionHashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Orchestrator chính cho luồng giải bài.
 *
 * Luồng xử lý:
 *   1. Xác thực email — kiểm tra customer tồn tại, active và chưa hết hạn.
 *   2. Tra cứu ngân hàng câu hỏi (LUÔN thực hiện).
 *   3. Nếu tìm thấy trong bank → trả về ngay.
 *   4. Nếu không và AI mode bật → gọi Claude AI.
 *   5. Lưu kết quả AI vào ngân hàng câu hỏi và question_records.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SolveService {

    private final AiSolverService aiSolverService;
    private final QuestionBankService questionBankService;
    private final PromptVersionService promptVersionService;
    private final CustomerRepository customerRepository;
    private final ExamSessionRepository examSessionRepository;
    private final QuestionRecordRepository questionRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SolveResponse solve(SolveRequest request) {
        long start = System.currentTimeMillis();

        // 1. Xác thực customer bằng email
        Customer customer = validateCustomer(request.getSession().getEmail());

        // 2. Lấy / tạo ExamSession
        ExamSession session = getOrCreateSession(customer, request);

        // 3. Tra cứu ngân hàng câu hỏi (toàn bộ hệ thống, bất kể môn)
        Optional<QuestionBank> bankHit = questionBankService.findAnswer(request);

        if (bankHit.isPresent()) {
            QuestionBank bankEntry = bankHit.get();
            long elapsed = System.currentTimeMillis() - start;
            saveRecord(session, request, bankEntry.getAnswer(), true,
                    QuestionRecord.AnswerSource.BANK, bankEntry, null, null, elapsed);

            return SolveResponse.builder()
                    .success(true)
                    .message("Answer from question bank")
                    .questionId(request.getQuestionId())
                    .answer(bankEntry.getAnswer())
                    .autoClick(true)
                    .build();
        }

        // 4. Không tìm thấy trong bank — kiểm tra AI mode
        if (!customer.isAiModeEnabled()) {
            long elapsed = System.currentTimeMillis() - start;
            saveRecord(session, request, null, false,
                    QuestionRecord.AnswerSource.NONE, null, null, null, elapsed);
            return SolveResponse.builder()
                    .success(false)
                    .message("Question not found in bank. AI mode is disabled for this account.")
                    .questionId(request.getQuestionId())
                    .autoClick(false)
                    .build();
        }

        // 5. Gọi AI — lấy prompt version active
        String qType = request.getQuestion().getQuestionType().toUpperCase();
        PromptVersion promptVersion = promptVersionService.getActive(qType)
                .orElseGet(() -> promptVersionService.getActive("SYSTEM").orElse(null));

        SolveResponse aiResponse = aiSolverService.solveQuestion(request, promptVersion);
        long elapsed = System.currentTimeMillis() - start;

        // 6. Lưu vào ngân hàng nếu AI trả về thành công
        QuestionBank savedBank = null;
        if (aiResponse.isSuccess() && aiResponse.getAnswer() != null) {
            Long pvId = promptVersion != null ? promptVersion.getId() : null;
            savedBank = questionBankService.saveIfAbsent(request, aiResponse.getAnswer(), pvId);
        }

        // 7. Lưu question record
        Long pvId = promptVersion != null ? promptVersion.getId() : null;
        saveRecord(session, request, aiResponse.getAnswer(), aiResponse.isSuccess(),
                QuestionRecord.AnswerSource.AI, savedBank, aiResponse.getMessage(), pvId, elapsed);

        log.info("Solved [{}] via AI in {}ms → [{}]",
                request.getQuestionId(), elapsed, aiResponse.getAnswer());
        return aiResponse;
    }

    // ─── Validate ─────────────────────────────────────────────────────────────

    private Customer validateCustomer(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Email không tồn tại trong hệ thống: " + email));

        if (!customer.hasValidAccess()) {
            if (!customer.isActive()) {
                throw new BusinessException("Tài khoản đã bị vô hiệu hóa.");
            }
            throw new BusinessException("Tài khoản đã hết hạn sử dụng. Vui lòng liên hệ admin.");
        }
        return customer;
    }

    // ─── Session ──────────────────────────────────────────────────────────────

    private ExamSession getOrCreateSession(Customer customer, SolveRequest request) {
        SolveRequest.SessionInfo si = request.getSession();
        return examSessionRepository
                .findByCustomerIdAndExamCodeAndSubjectCodeAndDeviceId(
                        customer.getId(), si.getExamCode(), si.getSubjectCode(), si.getDeviceId())
                .orElseGet(() -> examSessionRepository.save(ExamSession.builder()
                        .customer(customer)
                        .examCode(si.getExamCode())
                        .subjectCode(si.getSubjectCode())
                        .deviceId(si.getDeviceId())
                        .build()));
    }

    // ─── Record ───────────────────────────────────────────────────────────────

    private void saveRecord(ExamSession session, SolveRequest request,
                            String answer, boolean success,
                            QuestionRecord.AnswerSource source,
                            QuestionBank bankRef, String errorMsg,
                            Long promptVersionId, long processingMs) {
        try {
            String hash = QuestionHashUtil.computeHash(
                    request.getQuestion().getText(), request.getQuestion().getOptions());
            String optionsJson = objectMapper.writeValueAsString(request.getQuestion().getOptions());

            QuestionRecord rec = QuestionRecord.builder()
                    .examSession(session)
                    .questionBank(bankRef)
                    .questionHash(hash)
                    .questionNumber(request.getQuestion().getNumber())
                    .questionType(QuestionBank.QuestionType.valueOf(
                            request.getQuestion().getQuestionType().toUpperCase()))
                    .questionText(request.getQuestion().getText())
                    .optionsJson(optionsJson)
                    .answer(answer)
                    .autoClick(success)
                    .answerSource(source)
                    .aiModelUsed(source == QuestionRecord.AnswerSource.AI
                            ? aiSolverService.getProviderName() : null)
                    .promptVersionId(promptVersionId)
                    .processingTimeMs(processingMs)
                    .capturedAt(parseCapturedAt(request.getCapturedAt()))
                    .hasScreenshot(request.getQuestion().getScreenshotBase64() != null)
                    .success(success)
                    .errorMessage(success ? null : errorMsg)
                    .build();

            questionRecordRepository.save(rec);
        } catch (Exception e) {
            log.warn("Failed to save question record for [{}]: {}", request.getQuestionId(), e.getMessage());
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