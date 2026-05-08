package com.examsolver.service.impl;

import com.examsolver.dto.response.ExamSessionResponse;
import com.examsolver.dto.response.QuestionRecordResponse;
import com.examsolver.entity.Customer;
import com.examsolver.entity.ExamSession;
import com.examsolver.entity.QuestionRecord;
import com.examsolver.exception.BusinessException;
import com.examsolver.exception.ResourceNotFoundException;
import com.examsolver.repository.CustomerRepository;
import com.examsolver.repository.ExamSessionRepository;
import com.examsolver.repository.QuestionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamSessionService {

    private final ExamSessionRepository examSessionRepository;
    private final QuestionRecordRepository questionRecordRepository;
    private final CustomerRepository customerRepository;

    /** Lấy tất cả sessions của một customer. */
    public Page<ExamSessionResponse> getSessionsByCustomer(Long customerId, Pageable pageable) {
        return examSessionRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(this::toSessionResponse);
    }

    /** Lấy tất cả câu hỏi đã giải trong một ExamSession, chỉ trả về nếu customer là chủ. */
    public List<QuestionRecordResponse> getSessionQuestions(Long sessionId, String requesterEmail) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        Customer requester = customerRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        // Chỉ chủ sở hữu hoặc admin mới xem được
        boolean isAdmin = requester.getRole() == Customer.Role.ADMIN;
        boolean isOwner = session.getCustomer().getId().equals(requester.getId());
        if (!isAdmin && !isOwner) {
            throw new BusinessException("Bạn không có quyền xem session này.");
        }

        return questionRecordRepository.findByExamSessionId(sessionId)
                .stream().map(this::toRecordResponse).collect(Collectors.toList());
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private ExamSessionResponse toSessionResponse(ExamSession s) {
        return ExamSessionResponse.builder()
                .id(s.getId())
                .examCode(s.getExamCode())
                .subjectCode(s.getSubjectCode())
                .deviceId(s.getDeviceId())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private QuestionRecordResponse toRecordResponse(QuestionRecord q) {
        return QuestionRecordResponse.builder()
                .id(q.getId())
                .questionHash(q.getQuestionHash())
                .questionNumber(q.getQuestionNumber())
                .questionType(q.getQuestionType() != null ? q.getQuestionType().name() : null)
                .questionText(q.getQuestionText())
                .answer(q.getAnswer())
                .answerSource(q.getAnswerSource() != null ? q.getAnswerSource().name() : null)
                .success(q.isSuccess())
                .processingTimeMs(q.getProcessingTimeMs())
                .createdAt(q.getCreatedAt())
                .build();
    }
}