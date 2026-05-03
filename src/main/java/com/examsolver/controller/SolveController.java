package com.examsolver.controller;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.SolveResponse;
import com.examsolver.service.impl.SolveServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller chính xử lý yêu cầu giải câu hỏi từ client (Rust app).
 * Endpoint: POST /api/solve
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SolveController {

    private final SolveServiceImpl solveService;

    /**
     * Nhận câu hỏi từ client, xác thực API key, gọi AI giải và trả về đáp án.
     *
     * Request phải có thêm field "api_key" so với cấu trúc gốc trong API doc.
     */
    @PostMapping("/solve")
    public ResponseEntity<SolveResponse> solve(@Valid @RequestBody SolveRequest request) {
        log.info("Received solve request: questionId=[{}], type=[{}], subject=[{}]",
                request.getQuestionId(),
                request.getQuestion().getQuestionType(),
                request.getSession().getSubjectCode());

        SolveResponse response = solveService.solve(request);
        return ResponseEntity.ok(response);
    }
}
