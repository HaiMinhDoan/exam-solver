package com.examsolver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class ApiKeyRequest {

    @Data
    public static class Create {
        @NotNull(message = "customerId is required")
        @JsonProperty("customer_id")
        private Long customerId;

        @Min(value = 1, message = "validity_days must be >= 1")
        @JsonProperty("validity_days")
        private int validityDays = 30;

        private String description;
    }

    @Data
    public static class Revoke {
        @NotNull
        @JsonProperty("key_id")
        private Long keyId;
    }
}
