package com.examsolver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Kết quả trả về khi client poll GET /api/jobs/{id}.
 * Khi status = DONE, answer và auto_click được điền đầy đủ.
 * Rust client dùng answer + auto_click để tự động click đáp án.
 */
@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatusResponse {
    @JsonProperty("job_id")           private Long jobId;
    @JsonProperty("question_id")      private String questionId;
    private String status;            // PENDING | PROCESSING | DONE | FAILED | SKIPPED
    private String answer;            // null cho đến khi DONE
    @JsonProperty("answer_source")    private String answerSource; // BANK | AI
    @JsonProperty("auto_click")       private boolean autoClick;
    @JsonProperty("error_message")    private String errorMessage;
    @JsonProperty("processing_time_ms") private Long processingTimeMs;
    @JsonProperty("created_at")       private LocalDateTime createdAt;
    @JsonProperty("updated_at")       private LocalDateTime updatedAt;
}