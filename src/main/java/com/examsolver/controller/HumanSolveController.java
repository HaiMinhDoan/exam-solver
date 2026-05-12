package com.examsolver.controller;

import com.examsolver.dto.request.HumanAnswerRequest;
import com.examsolver.dto.response.ApiResponse;
import com.examsolver.dto.response.ExamSessionResponse;
import com.examsolver.dto.response.JobStatusResponse;
import com.examsolver.repository.CustomerRepository;
import com.examsolver.service.impl.HumanSolveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/human")
@RequiredArgsConstructor
public class HumanSolveController {

    private final HumanSolveService humanSolveService;
    private final CustomerRepository customerRepository;

    /**
     * Danh sách bài thi (exam sessions) có câu hỏi chờ giáo viên giải (HUMAN resolver).
     * GET /api/human/sessions?page=0&size=20
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<Page<ExamSessionResponse>>> listSessions(
            Authentication auth,
            Pageable pageable) {
        Long customerId = customerRepository.findByEmail(auth.getName())
                .map(c -> c.getId())
                .orElseThrow(() -> new RuntimeException("Customer không tồn tại"));
        return ResponseEntity.ok(ApiResponse.ok(
                humanSolveService.getHumanSessions(customerId, pageable)));
    }

    /**
     * Danh sách câu hỏi chờ giáo viên giải trong một bài thi cụ thể.
     * GET /api/human/sessions/{sessionId}/jobs?page=0&size=20
     */
    @GetMapping("/sessions/{sessionId}/jobs")
    public ResponseEntity<ApiResponse<Page<JobStatusResponse>>> getSessionJobs(
            Authentication auth,
            @PathVariable Long sessionId,
            Pageable pageable) {
        Long customerId = customerRepository.findByEmail(auth.getName())
                .map(c -> c.getId())
                .orElseThrow(() -> new RuntimeException("Customer không tồn tại"));
        return ResponseEntity.ok(ApiResponse.ok(
                humanSolveService.getSessionJobs(customerId, sessionId, pageable)));
    }

    /**
     * Lấy chi tiết một câu hỏi để hiển thị.
     * GET /api/human/jobs/{jobId}
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<JobStatusResponse>> getJobDetail(
            @PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.ok(
                humanSolveService.getJob(jobId)));
    }

    /**
     * Giáo viên submit đáp án cho một câu hỏi.
     * POST /api/human/jobs/{jobId}/answer
     * Body: { "answer": "A" }
     * 
     * Cho phép múi người cùng giải một câu hỏi trong cùng một session.
     * Đáp án cuối sẽ được lưu, không cần validate quyền sở hữu.
     */
    @PostMapping("/jobs/{jobId}/answer")
    public ResponseEntity<ApiResponse<JobStatusResponse>> submitAnswer(
            @PathVariable Long jobId,
            @Valid @RequestBody HumanAnswerRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Đã lưu đáp án",
                humanSolveService.submitAnswer(jobId, auth.getName(), req)));
    }

}