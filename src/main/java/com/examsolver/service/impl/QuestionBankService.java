package com.examsolver.service.impl;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.entity.QuestionBank;
import com.examsolver.repository.QuestionBankRepository;
import com.examsolver.util.QuestionHashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Quản lý ngân hàng câu hỏi toàn hệ thống.
 *
 * Chiến lược tìm kiếm (theo thứ tự):
 *   1. Hash lookup (O(1), exact match)
 *   2. Fuzzy text similarity (pg_trgm, threshold cài đặt được)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionBankService {

    private final QuestionBankRepository questionBankRepository;
    private final ObjectMapper objectMapper;

    @Value("${question-bank.similarity-threshold:0.35}")
    private double similarityThreshold;

    @Value("${question-bank.fuzzy-enabled:true}")
    private boolean fuzzyEnabled;

    // ─── Lookup ──────────────────────────────────────────────────────────────

    /**
     * Tìm kiếm câu hỏi trong ngân hàng.
     * Trả về Optional.empty() nếu không tìm thấy.
     */
    @Transactional
    public Optional<QuestionBank> findAnswer(SolveRequest request) {
        String hash = QuestionHashUtil.computeHash(
                request.getQuestion().getText(), request.getQuestion().getOptions());

        // Bước 1: hash exact match
        Optional<QuestionBank> byHash = questionBankRepository.findByQuestionHash(hash);
        if (byHash.isPresent()) {
            log.debug("Bank HASH HIT for question [{}]", request.getQuestionId());
            questionBankRepository.incrementHitCount(byHash.get().getId());
            return byHash;
        }

        // Bước 2: fuzzy similarity (nếu bật)
        if (fuzzyEnabled) {
            String normalizedText = QuestionHashUtil.normalize(request.getQuestion().getText());
            String qType = request.getQuestion().getQuestionType().toUpperCase();

            List<QuestionBank> similar = questionBankRepository.findBySimilarText(
                    normalizedText, qType, similarityThreshold, 1);

            if (!similar.isEmpty()) {
                QuestionBank found = similar.get(0);
                log.debug("Bank FUZZY HIT for question [{}], bank id [{}]",
                        request.getQuestionId(), found.getId());
                questionBankRepository.incrementHitCount(found.getId());
                return Optional.of(found);
            }
        }

        log.debug("Bank MISS for question [{}]", request.getQuestionId());
        return Optional.empty();
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    /**
     * Lưu câu hỏi mới vào ngân hàng sau khi AI giải xong.
     * Nếu đã tồn tại (cùng hash) thì bỏ qua.
     */
    @Transactional
    public QuestionBank saveIfAbsent(SolveRequest request, String answer,
                                     Long promptVersionId) {
        String hash = QuestionHashUtil.computeHash(
                request.getQuestion().getText(), request.getQuestion().getOptions());

        return questionBankRepository.findByQuestionHash(hash).orElseGet(() -> {
            try {
                String optionsJson = objectMapper.writeValueAsString(
                        request.getQuestion().getOptions());

                QuestionBank entry = QuestionBank.builder()
                        .questionHash(hash)
                        .normalizedText(QuestionHashUtil.normalize(request.getQuestion().getText()))
                        .originalText(request.getQuestion().getText())
                        .questionType(QuestionBank.QuestionType.valueOf(
                                request.getQuestion().getQuestionType().toUpperCase()))
                        .optionsJson(optionsJson)
                        .answer(answer)
                        .subjectCode(request.getSession().getSubjectCode())
                        .promptVersionId(promptVersionId)
                        .hitCount(1L)
                        .verified(false)
                        .build();

                QuestionBank saved = questionBankRepository.save(entry);
                log.info("Saved new question to bank, hash [{}], type [{}]",
                        hash, request.getQuestion().getQuestionType());
                return saved;
            } catch (Exception e) {
                log.warn("Failed to save to question bank: {}", e.getMessage());
                return null;
            }
        });
    }

    // ─── Admin / Query ────────────────────────────────────────────────────────

    public Page<QuestionBank> search(String subjectCode, QuestionBank.QuestionType type, Pageable pageable) {
        if (subjectCode != null && !subjectCode.isBlank()) {
            return questionBankRepository.findBySubjectCodeContainingIgnoreCase(subjectCode, pageable);
        }
        if (type != null) {
            return questionBankRepository.findByQuestionType(type, pageable);
        }
        return questionBankRepository.findAll(pageable);
    }

    @Transactional
    public void verifyAnswer(Long bankId, String correctedAnswer) {
        questionBankRepository.findById(bankId).ifPresent(q -> {
            q.setAnswer(correctedAnswer);
            q.setVerified(true);
            questionBankRepository.save(q);
        });
    }
}