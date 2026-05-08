package com.examsolver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

/** Admin cập nhật thông tin customer. */
@Data
public class CustomerUpdateRequest {

    @JsonProperty("access_expires_at")
    private LocalDateTime accessExpiresAt;

    @JsonProperty("ai_mode_enabled")
    private Boolean aiModeEnabled;

    @JsonProperty("active")
    private Boolean active;
}