package com.examsolver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerResponse {
    private Long id;
    private String email;
    @JsonProperty("full_name")          private String fullName;
    @JsonProperty("phone_number")       private String phoneNumber;
    private String role;
    private boolean active;
    @JsonProperty("ai_mode_enabled")    private boolean aiModeEnabled;
    @JsonProperty("access_expires_at")  private LocalDateTime accessExpiresAt;
    @JsonProperty("created_at")         private LocalDateTime createdAt;
}