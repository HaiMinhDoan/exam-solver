package com.examsolver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyResponse {

    private Long id;

    @JsonProperty("key_value")
    private String keyValue;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("customer_email")
    private String customerEmail;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @JsonProperty("is_active")
    private boolean active;

    @JsonProperty("validity_days")
    private int validityDays;

    private String description;

    @JsonProperty("usage_count")
    private long usageCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("last_used_at")
    private LocalDateTime lastUsedAt;
}
