package com.examsolver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionRecordResponse {
    private Long id;
    @JsonProperty("question_hash")     private String questionHash;
    @JsonProperty("question_number")   private String questionNumber;
    @JsonProperty("question_type")     private String questionType;
    @JsonProperty("question_text")     private String questionText;
    private String answer;
    @JsonProperty("answer_source")     private String answerSource;
    private boolean success;
    @JsonProperty("processing_time_ms") private Long processingTimeMs;
    @JsonProperty("created_at")        private LocalDateTime createdAt;
}