package com.examsolver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Tạo / cập nhật prompt version. */
@Data
public class PromptVersionRequest {

    @NotBlank(message = "promptType is required")
    @JsonProperty("prompt_type")
    private String promptType; // SINGLECHOICE | MULTIPLECHOICE | TRUEFALSE | ESSAY | SYSTEM

    @JsonProperty("version_label")
    private String versionLabel;

    @NotBlank(message = "promptTemplate is required")
    @Size(max = 10000)
    @JsonProperty("prompt_template")
    private String promptTemplate;

    private String notes;
}