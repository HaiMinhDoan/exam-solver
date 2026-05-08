package com.examsolver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class ExamSessionResponse {
    private Long id;
    @JsonProperty("exam_code")    private String examCode;
    @JsonProperty("subject_code") private String subjectCode;
    @JsonProperty("device_id")    private String deviceId;
    @JsonProperty("created_at")   private LocalDateTime createdAt;
}