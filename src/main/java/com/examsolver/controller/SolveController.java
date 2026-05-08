package com.examsolver.controller;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.SolveResponse;
import com.examsolver.service.impl.SolveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SolveController {

    private final SolveService solveService;

    /**
     * Endpoint chính nhận câu hỏi từ Rust client.
     * Xác thực qua email trong session.session.email — không cần JWT hay API key.
     */
    @PostMapping("/solve")
    public ResponseEntity<SolveResponse> solve(@Valid @RequestBody SolveRequest request) {
        log.info("Solve request: questionId=[{}] email=[{}] type=[{}] subject=[{}]",
                request.getQuestionId(), request.getSession().getEmail(),
                request.getQuestion().getQuestionType(), request.getSession().getSubjectCode());
        return ResponseEntity.ok(solveService.solve(request));
    }
}