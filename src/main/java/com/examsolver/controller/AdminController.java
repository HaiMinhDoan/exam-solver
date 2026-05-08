package com.examsolver.controller;

import com.examsolver.dto.request.CustomerUpdateRequest;
import com.examsolver.dto.request.PromptVersionRequest;
import com.examsolver.dto.response.*;
import com.examsolver.entity.QuestionBank;
import com.examsolver.service.impl.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final CustomerService customerService;
    private final PromptVersionService promptVersionService;
    private final QuestionBankService questionBankService;

    // ─── Customer Management ──────────────────────────────────────────────────

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> listCustomers(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.listCustomers(pageable)));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getCustomer(id)));
    }

    /** Cập nhật: thời hạn truy cập, AI mode, trạng thái. */
    @PatchMapping("/customers/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable Long id, @RequestBody CustomerUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Updated", customerService.updateCustomer(id, req)));
    }

    /**
     * Gia hạn nhanh: thêm N ngày từ ngày hết hạn hiện tại (hoặc từ hôm nay).
     * POST /api/admin/customers/5/extend?days=30
     */
    @PostMapping("/customers/{id}/extend")
    public ResponseEntity<ApiResponse<CustomerResponse>> extendAccess(
            @PathVariable Long id, @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Gia hạn +" + days + " ngày", customerService.extendAccess(id, days)));
    }

    // ─── Prompt Version Management ────────────────────────────────────────────

    /** Xem tất cả versions của một prompt type. */
    @GetMapping("/prompts/{promptType}/versions")
    public ResponseEntity<ApiResponse<List<PromptVersionResponse>>> listVersions(
            @PathVariable String promptType) {
        return ResponseEntity.ok(ApiResponse.ok(promptVersionService.listByType(promptType.toUpperCase())));
    }

    /** Xem version đang active. */
    @GetMapping("/prompts/{promptType}/active")
    public ResponseEntity<ApiResponse<PromptVersionResponse>> getActive(@PathVariable String promptType) {
        return promptVersionService.getActive(promptType.toUpperCase())
                .map(pv -> ResponseEntity.ok(ApiResponse.ok(
                        PromptVersionResponse.builder()
                                .id(pv.getId()).promptType(pv.getPromptType())
                                .versionNumber(pv.getVersionNumber())
                                .versionLabel(pv.getVersionLabel())
                                .promptTemplate(pv.getPromptTemplate())
                                .active(pv.isActive())
                                .createdBy(pv.getCreatedBy())
                                .notes(pv.getNotes())
                                .createdAt(pv.getCreatedAt())
                                .activatedAt(pv.getActivatedAt())
                                .build())))
                .orElse(ResponseEntity.ok(ApiResponse.error("Không có active version cho: " + promptType)));
    }

    /** Tạo version mới (chưa active). */
    @PostMapping("/prompts/versions")
    public ResponseEntity<ApiResponse<PromptVersionResponse>> createVersion(
            @Valid @RequestBody PromptVersionRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Version created",
                promptVersionService.createVersion(req, auth.getName())));
    }

    /** Xem chi tiết một version. */
    @GetMapping("/prompts/versions/{id}")
    public ResponseEntity<ApiResponse<PromptVersionResponse>> getVersion(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(promptVersionService.getById(id)));
    }

    /**
     * Activate / kéo lên một version cụ thể.
     * Đây cũng là cách ROLLBACK: activate lại version cũ hơn.
     */
    @PostMapping("/prompts/versions/{id}/activate")
    public ResponseEntity<ApiResponse<PromptVersionResponse>> activateVersion(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Version activated",
                promptVersionService.activateVersion(id, auth.getName())));
    }

    // ─── Question Bank ────────────────────────────────────────────────────────

    @GetMapping("/question-bank")
    public ResponseEntity<ApiResponse<Page<QuestionBank>>> searchBank(
            @RequestParam(required = false) String subjectCode,
            @RequestParam(required = false) String type,
            Pageable pageable) {
        QuestionBank.QuestionType qType = type != null
                ? QuestionBank.QuestionType.valueOf(type.toUpperCase()) : null;
        return ResponseEntity.ok(ApiResponse.ok(questionBankService.search(subjectCode, qType, pageable)));
    }

    /** Admin xác nhận / sửa đáp án trong ngân hàng. */
    @PatchMapping("/question-bank/{id}/verify")
    public ResponseEntity<ApiResponse<Void>> verifyAnswer(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        questionBankService.verifyAnswer(id, body.get("answer"));
        return ResponseEntity.ok(ApiResponse.ok("Answer verified", null));
    }
}