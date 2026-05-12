package com.examsolver.dto.response;

import com.examsolver.entity.QuestionBank;
import com.examsolver.entity.QuestionJob;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.examsolver.dto.request.SolveRequest;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kết quả trả về khi client poll GET /api/jobs/{id}.
 * Khi status = DONE, answer và auto_click được điền đầy đủ.
 * Rust client dùng answer + auto_click để tự động click đáp án.
 */
@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatusResponse {
    @JsonProperty("job_id")           private Long jobId;
    @JsonProperty("question_id")      private String questionId;
    @JsonProperty("question_number")  private String questionNumber;
    @JsonProperty("question_type")    private QuestionBank.QuestionType questionType;
    @JsonProperty("question_text")    private String questionText;
    private QuestionJob.JobStatus status;            // PENDING | PROCESSING | DONE | FAILED | SKIPPED
    private String answer;            // null cho đến khi DONE
    @JsonProperty("answer_source")    private String answerSource; // BANK | AI | HUMAN
    @JsonProperty("auto_click")       private boolean autoClick;
    @JsonProperty("error_message")    private String errorMessage;
    private List<SolveRequest.OptionData> options; // Danh sách lựa chọn
    @JsonProperty("processing_time_ms") private Long processingTimeMs;
    @JsonProperty("created_at")       private LocalDateTime createdAt;
    @JsonProperty("updated_at")       private LocalDateTime updatedAt;
}