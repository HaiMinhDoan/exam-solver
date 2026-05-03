package com.examsolver.service.impl;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.SolveResponse;
import com.examsolver.entity.*;
import com.examsolver.exception.ApiKeyInvalidException;
import com.examsolver.repository.*;
import com.examsolver.service.ai.AiSolverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SolveServiceImpl {

    private final AiSolverService aiSolverService;
    private final ApiKeyRepository apiKeyRepository;
    private final ExamSessionRepository examSessionRepository;
    private final QuestionRecordRepository questionRecordRepository;
    private final ObjectMapper objectMapper;

    /**
     * Entry point chính: xác thực API key, kiểm tra cache, gọi AI, lưu kết quả.
     */
    @Transactional
    public SolveResponse solve(SolveRequest request) {
        long start = System.currentTimeMillis();

        // 1. Xác thực API key
        ApiKey apiKey = validateApiKey(request.getApiKey());
        Customer customer = apiKey.getCustomer();

        // 2. Kiểm tra cache (cùng questionId đã được giải thành công trước đó)
        Optional<QuestionRecord> cached = questionRecordRepository.findCachedAnswer(request.getQuestionId());
        if (cached.isPresent()) {
            log.info("Cache hit for question [{}]", request.getQuestionId());
            QuestionRecord cr = cached.get();
            apiKeyRepository.incrementUsageCount(apiKey.getId(), LocalDateTime.now());
            return SolveResponse.builder()
                    .success(true)
                    .message("Answer from cache")
                    .questionId(cr.getQuestionId())
                    .answer(cr.getAnswer())
                    .autoClick(true)
                    .build();
        }

        // 3. Lấy hoặc tạo ExamSession
        ExamSession session = getOrCreateSession(customer, request);

        // 4. Gọi AI giải bài
        SolveResponse aiResponse = aiSolverService.solveQuestion(request);
        long elapsed = System.currentTimeMillis() - start;

        // 5. Lưu kết quả vào DB
        saveQuestionRecord(session, request, aiResponse, elapsed);

        // 6. Tăng usage count cho API key
        apiKeyRepository.incrementUsageCount(apiKey.getId(), LocalDateTime.now());

        log.info("Question [{}] solved in {}ms, answer: [{}]",
                request.getQuestionId(), elapsed, aiResponse.getAnswer());

        return aiResponse;
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private ApiKey validateApiKey(String keyValue) {
        ApiKey apiKey = apiKeyRepository.findValidKey(keyValue, LocalDateTime.now())
                .orElseThrow(() -> new ApiKeyInvalidException("API key không hợp lệ hoặc đã hết hạn"));

        log.debug("API key validated for customer [{}]", apiKey.getCustomer().getEmail());
        return apiKey;
    }

    private ExamSession getOrCreateSession(Customer customer, SolveRequest request) {
        SolveRequest.SessionInfo si = request.getSession();
        return examSessionRepository
                .findByCustomerIdAndExamCodeAndSubjectCodeAndDeviceId(
                        customer.getId(), si.getExamCode(), si.getSubjectCode(), si.getDeviceId())
                .orElseGet(() -> {
                    ExamSession newSession = ExamSession.builder()
                            .customer(customer)
                            .examCode(si.getExamCode())
                            .subjectCode(si.getSubjectCode())
                            .deviceId(si.getDeviceId())
                            .build();
                    return examSessionRepository.save(newSession);
                });
    }

    private void saveQuestionRecord(ExamSession session, SolveRequest request,
                                    SolveResponse response, long processingMs) {
        try {
            String optionsJson = objectMapper.writeValueAsString(request.getQuestion().getOptions());

            QuestionRecord record = QuestionRecord.builder()
                    .examSession(session)
                    .questionId(request.getQuestionId())
                    .questionNumber(request.getQuestion().getNumber())
                    .questionType(QuestionRecord.QuestionType.valueOf(
                            request.getQuestion().getQuestionType().toUpperCase()))
                    .questionText(request.getQuestion().getText())
                    .optionsJson(optionsJson)
                    .answer(response.getAnswer())
                    .autoClick(response.isAutoClick())
                    .aiModelUsed(aiSolverService.getProviderName())
                    .processingTimeMs(processingMs)
                    .capturedAt(parseCapturedAt(request.getCapturedAt()))
                    .hasScreenshot(request.getQuestion().getScreenshotBase64() != null)
                    .success(response.isSuccess())
                    .errorMessage(response.isSuccess() ? null : response.getMessage())
                    .build();

            questionRecordRepository.save(record);
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
