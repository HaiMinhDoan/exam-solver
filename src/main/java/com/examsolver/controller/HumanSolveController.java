package com.examsolver.controller;

import com.examsolver.dto.request.HumanAnswerRequest;
import com.examsolver.dto.response.ApiResponse;
import com.examsolver.dto.response.JobStatusResponse;
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

    /**
     * Danh sách câu hỏi đang chờ giáo viên giải.
     * GET /api/human/queue?examCode=HADA12&subjectCode=MLN131&page=0&size=20
     */
    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<Page<JobStatusResponse>>> getQueue(
            @RequestParam(required = false) String examCode,
            @RequestParam(required = false) String subjectCode,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                humanSolveService.getPendingJobs(examCode, subjectCode, pageable)));
    }

    /**
     * Giáo viên nhận câu hỏi — tránh 2 người giải cùng lúc.
     * POST /api/human/jobs/{jobId}/claim
     */
    @PostMapping("/jobs/{jobId}/claim")
    public ResponseEntity<ApiResponse<JobStatusResponse>> claim(
            @PathVariable Long jobId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                humanSolveService.claimJob(jobId, auth.getName())));
    }

    /**
     * Giáo viên submit đáp án.
     * POST /api/human/jobs/{jobId}/answer
     * Body: { "answer": "A" }
     */
    @PostMapping("/jobs/{jobId}/answer")
    public ResponseEntity<ApiResponse<JobStatusResponse>> submitAnswer(
            @PathVariable Long jobId,
            @Valid @RequestBody HumanAnswerRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Đã lưu đáp án",
                humanSolveService.submitAnswer(jobId, auth.getName(), req)));
    }

    /**
     * Giáo viên trả lại câu hỏi (bỏ không giải nữa).
     * POST /api/human/jobs/{jobId}/release
     */
    @PostMapping("/jobs/{jobId}/release")
    public ResponseEntity<ApiResponse<Void>> release(
            @PathVariable Long jobId, Authentication auth) {
        humanSolveService.releaseJob(jobId, auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Đã trả lại câu hỏi", null));
    }
}