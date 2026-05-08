package com.examsolver.controller;

import com.examsolver.dto.response.ApiResponse;
import com.examsolver.dto.response.ExamSessionResponse;
import com.examsolver.dto.response.QuestionRecordResponse;
import com.examsolver.entity.Customer;
import com.examsolver.exception.ResourceNotFoundException;
import com.examsolver.repository.CustomerRepository;
import com.examsolver.service.impl.ExamSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class ExamSessionController {

    private final ExamSessionService examSessionService;
    private final CustomerRepository customerRepository;

    /** Customer xem danh sách sessions của mình. */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<ExamSessionResponse>>> mySessions(
            Authentication auth, Pageable pageable) {
        Customer customer = customerRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return ResponseEntity.ok(ApiResponse.ok(
                examSessionService.getSessionsByCustomer(customer.getId(), pageable)));
    }

    /** Xem tất cả câu hỏi đã giải trong một session. */
    @GetMapping("/{sessionId}/questions")
    public ResponseEntity<ApiResponse<List<QuestionRecordResponse>>> sessionQuestions(
            @PathVariable Long sessionId, Authentication auth) {
        List<QuestionRecordResponse> questions =
                examSessionService.getSessionQuestions(sessionId, auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(questions));
    }
}