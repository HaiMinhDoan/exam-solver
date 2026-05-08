package com.examsolver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * Payload từ Rust client. KHÔNG còn api_key — xác thực dựa vào email trong session.
 * Email phải khớp với customer đã được admin cấp quyền và chưa hết hạn.
 */
@Data
public class SolveRequest {

    @JsonProperty("question_id")
    @NotBlank(message = "question_id is required")
    private String questionId;

    @Valid @NotNull
    private SessionInfo session;

    @Valid @NotNull
    private QuestionData question;

    @JsonProperty("captured_at")
    @NotBlank
    private String capturedAt;

    @JsonProperty("ai_answer")
    private String aiAnswer;

    @Data
    public static class SessionInfo {
        @NotBlank(message = "email is required")
        private String email;

        @JsonProperty("exam_code")
        @NotBlank
        private String examCode;

        @JsonProperty("subject_code")
        @NotBlank
        private String subjectCode;

        @JsonProperty("device_id")
        @NotBlank
        private String deviceId;
    }

    @Data
    public static class QuestionData {
        @NotBlank
        private String number;

        @JsonProperty("question_type")
        @NotBlank
        private String questionType;

        @NotBlank
        private String text;

        @NotNull
        private List<OptionData> options;

        @JsonProperty("screenshot_base64")
        private String screenshotBase64;
    }

    @Data
    public static class OptionData {
        @NotBlank private String label;
        @NotBlank private String text;
    }
}