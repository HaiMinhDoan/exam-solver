package com.examsolver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class SolveResponse {
    private boolean success;
    private String message;
    @JsonProperty("question_id") private String questionId;
    private String answer;
    @JsonProperty("auto_click") private boolean autoClick;
}