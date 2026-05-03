package com.examsolver.controller;

import com.examsolver.dto.request.ApiKeyRequest;
import com.examsolver.dto.response.ApiKeyResponse;
import com.examsolver.dto.response.ApiResponse;
import com.examsolver.service.impl.ApiKeyServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only endpoints để quản lý API keys và khách hàng.
 * Yêu cầu JWT với role ADMIN.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ApiKeyServiceImpl apiKeyService;

    /**
     * Tạo API key mới cho khách hàng.
     * Mỗi khách hàng chỉ có 1 key active tại một thời điểm.
     */
    @PostMapping("/api-keys")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> createApiKey(@Valid @RequestBody ApiKeyRequest.Create req) {
        ApiKeyResponse response = apiKeyService.createApiKey(req);
        return ResponseEntity.ok(ApiResponse.ok("API key đã được tạo", response));
    }

    /**
     * Thu hồi API key.
     */
    @DeleteMapping("/api-keys/{keyId}")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(@PathVariable Long keyId) {
        apiKeyService.revokeApiKey(keyId);
        return ResponseEntity.ok(ApiResponse.ok("API key đã bị thu hồi", null));
    }

    /**
     * Xem tất cả API keys của một khách hàng.
     */
    @GetMapping("/customers/{customerId}/api-keys")
    public ResponseEntity<ApiResponse<List<ApiKeyResponse>>> getCustomerKeys(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.ok(apiKeyService.getCustomerKeys(customerId)));
    }
}
