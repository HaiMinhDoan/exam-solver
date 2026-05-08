package com.examsolver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class PromptVersionResponse {
    private Long id;
    @JsonProperty("prompt_type")    private String promptType;
    @JsonProperty("version_number") private int versionNumber;
    @JsonProperty("version_label")  private String versionLabel;
    @JsonProperty("prompt_template") private String promptTemplate;
    @JsonProperty("is_active")      private boolean active;
    @JsonProperty("created_by")     private String createdBy;
    private String notes;
    @JsonProperty("created_at")     private LocalDateTime createdAt;
    @JsonProperty("activated_at")   private LocalDateTime activatedAt;
}