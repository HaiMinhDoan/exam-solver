package com.examsolver.service.impl;

import com.examsolver.dto.request.ApiKeyRequest;
import com.examsolver.dto.response.ApiKeyResponse;
import com.examsolver.entity.ApiKey;
import com.examsolver.entity.Customer;
import com.examsolver.exception.ResourceNotFoundException;
import com.examsolver.repository.ApiKeyRepository;
import com.examsolver.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyServiceImpl {

    private final ApiKeyRepository apiKeyRepository;
    private final CustomerRepository customerRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Admin tạo API key cho khách hàng với số ngày hiệu lực tùy chỉnh.
     */
    @Transactional
    public ApiKeyResponse createApiKey(ApiKeyRequest.Create req) {
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + req.getCustomerId()));

        // Vô hiệu hóa các key cũ còn active (mỗi khách chỉ có 1 key active)
        List<ApiKey> existingKeys = apiKeyRepository.findByCustomerIdAndActiveTrue(customer.getId());
        existingKeys.forEach(k -> k.setActive(false));
        apiKeyRepository.saveAll(existingKeys);

        String keyValue = generateApiKey();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(req.getValidityDays());

        ApiKey apiKey = ApiKey.builder()
                .keyValue(keyValue)
                .customer(customer)
                .expiresAt(expiresAt)
                .validityDays(req.getValidityDays())
                .description(req.getDescription())
                .active(true)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("Created API key for customer [{}], expires [{}]", customer.getEmail(), expiresAt);
        return toResponse(saved);
    }

    /**
     * Admin thu hồi API key.
     */
    @Transactional
    public void revokeApiKey(Long keyId) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + keyId));
        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);
        log.info("Revoked API key [{}]", keyId);
    }

    public List<ApiKeyResponse> getCustomerKeys(Long customerId) {
        return apiKeyRepository.findByCustomerId(customerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private String generateApiKey() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return "esk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiKeyResponse toResponse(ApiKey k) {
        return ApiKeyResponse.builder()
                .id(k.getId())
                .keyValue(k.getKeyValue())
                .customerId(k.getCustomer().getId())
                .customerEmail(k.getCustomer().getEmail())
                .expiresAt(k.getExpiresAt())
                .active(k.isActive())
                .validityDays(k.getValidityDays())
                .description(k.getDescription())
                .usageCount(k.getUsageCount())
                .createdAt(k.getCreatedAt())
                .lastUsedAt(k.getLastUsedAt())
                .build();
    }
}
