package com.examsolver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/** Trả về ngay sau khi ingest thành công (202 Accepted). */
@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class JobSubmittedResponse {
    @JsonProperty("job_id")       private Long jobId;
    @JsonProperty("question_id")  private String questionId;
    private String status;        // PENDING
    private String message;
}