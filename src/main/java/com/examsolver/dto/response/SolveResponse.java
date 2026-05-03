package com.examsolver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response trả về cho client sau khi giải câu hỏi.
 * Tuân theo đúng cấu trúc ServerResponse trong tài liệu API.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SolveResponse {

    private boolean success;
    private String message;

    @JsonProperty("question_id")
    private String questionId;

    private String answer;

    @JsonProperty("auto_click")
    private boolean autoClick;
}

