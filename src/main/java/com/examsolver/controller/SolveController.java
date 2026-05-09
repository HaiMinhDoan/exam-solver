package com.examsolver.controller;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.ApiResponse;
import com.examsolver.dto.response.JobStatusResponse;
import com.examsolver.dto.response.JobSubmittedResponse;
import com.examsolver.service.impl.IngestService;
import com.examsolver.service.impl.JobQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SolveController {

    private final IngestService ingestService;
    private final JobQueryService jobQueryService;

    /**
     * Phase 1: nhận câu hỏi → lưu DB → fire Kafka → 202 Accepted.
     * Rust client poll /api/jobs/{jobId} để lấy đáp án sau.
     */
    @PostMapping("/solve")
    public ResponseEntity<JobSubmittedResponse> solve(@Valid @RequestBody SolveRequest request) {
        log.info("Ingest: email=[{}] questionId=[{}] type=[{}]",
                request.getSession().getEmail(),
                request.getQuestionId(),
                request.getQuestion().getQuestionType());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestService.ingest(request));
    }

    /**
     * Rust client poll kết quả — không cần JWT.
     * GET /api/jobs/123?email=teacher@school.edu.vn
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @PathVariable Long jobId,
            @RequestParam String email) {
        return ResponseEntity.ok(jobQueryService.getJobStatus(jobId, email));
    }

    /**
     * Customer đã login xem danh sách jobs.
     * GET /api/jobs/my?status=PENDING
     */
    @GetMapping("/jobs/my")
    public ResponseEntity<ApiResponse<Page<JobStatusResponse>>> myJobs(
            Authentication auth,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobQueryService.getMyJobs(auth.getName(), status, pageable)));
    }
}