//package com.examsolver.controller;
//
//import com.examsolver.dto.request.ManualSolveRequest;
//import com.examsolver.dto.response.ApiResponse;
//import com.examsolver.dto.response.JobStatusResponse;
//import com.examsolver.dto.response.JobSubmittedResponse;
//import com.examsolver.service.impl.IngestService;
//import com.examsolver.service.impl.JobQueryService;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.Authentication;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/questions")
//@RequiredArgsConstructor
//@Slf4j
//public class ManualSolveController {
//
//    private final IngestService ingestService;
//    private final JobQueryService jobQueryService;
//
//    /**
//     * Người dùng tự nhập câu hỏi từ web UI → tạo job → trả jobId.
//     * Yêu cầu JWT (người dùng phải đăng nhập).
//     * Tái dụng IngestService → Kafka → Executor — không viết thêm logic.
//     *
//     * POST /api/questions/solve
//     */
//    @PostMapping("/solve")
//    public ResponseEntity<JobSubmittedResponse> manualSolve(
//            @Valid @RequestBody ManualSolveRequest req,
//            Authentication auth) {
//
//        String email = auth.getName();
//        log.info("Manual solve: email=[{}] type=[{}] subject=[{}]",
//                email, req.getQuestionType(), req.getSubjectCode());
//
//        JobSubmittedResponse response = ingestService.ingest(req.toSolveRequest(email));
//        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
//    }
//
//    /**
//     * Batch: gửi nhiều câu hỏi cùng lúc.
//     * Mỗi câu tạo một job độc lập.
//     *
//     * POST /api/questions/solve/batch
//     */
//    @PostMapping("/solve/batch")
//    public ResponseEntity<ApiResponse<List<JobSubmittedResponse>>> batchSolve(
//            @Valid @RequestBody List<ManualSolveRequest> requests,
//            Authentication auth) {
//
//        String email = auth.getName();
//        log.info("Batch solve: email=[{}] count=[{}]", email, requests.size());
//
//        List<JobSubmittedResponse> jobs = requests.stream()
//                .map(req -> ingestService.ingest(req.toSolveRequest(email)))
//                .toList();
//
//        return ResponseEntity.status(HttpStatus.ACCEPTED)
//                .body(ApiResponse.ok("Đã gửi " + jobs.size() + " câu hỏi", jobs));
//    }
//
//    /**
//     * Poll kết quả job theo jobId — dành cho web UI.
//     * Chỉ lấy được job của chính mình (auth.getName() = email).
//     *
//     * GET /api/questions/jobs/{jobId}
//     */
//    @GetMapping("/jobs/{jobId}")
//    public ResponseEntity<JobStatusResponse> getJobStatus(
//            @PathVariable Long jobId,
//            Authentication auth) {
//        return ResponseEntity.ok(jobQueryService.getJobStatus(jobId, auth.getName()));
//    }
//
//    /**
//     * Lịch sử câu hỏi đã gửi — có filter theo status.
//     *
//     * GET /api/questions/jobs?status=DONE&page=0&size=20
//     */
//    @GetMapping("/jobs")
//    public ResponseEntity<ApiResponse<Page<JobStatusResponse>>> myJobs(
//            @RequestParam(required = false) String status,
//            Authentication auth,
//            Pageable pageable) {
//        return ResponseEntity.ok(ApiResponse.ok(
//                jobQueryService.getMyJobs(auth.getName(), status, pageable)));
//    }
//}