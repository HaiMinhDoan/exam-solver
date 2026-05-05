package com.examsolver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Payload gửi từ client (Rust app) lên server để giải câu hỏi.
 * Bao gồm thông tin phiên thi, câu hỏi, và API key xác thực.
 */
@Data
public class SolveRequest {

    @JsonProperty("question_id")
    @NotBlank(message = "question_id is required")
    private String questionId;

    @Valid
    @NotNull(message = "session is required")
    private SessionInfo session;

    @Valid
    @NotNull(message = "question is required")
    private QuestionData question;

    @JsonProperty("captured_at")
    @NotBlank(message = "captured_at is required")
    private String capturedAt;

    @JsonProperty("ai_answer")
    private String aiAnswer; // Luôn null từ client

//    /**
//     * API key của khách hàng - bắt buộc khi gọi /api/solve
//     */
//    @JsonProperty("api_key")
//    @NotBlank(message = "api_key is required")
//    private String apiKey;

    // ─── Nested DTOs ─────────────────────────────────────────────────────────

    @Data
    public static class SessionInfo {
        @NotBlank(message = "email is required")
        private String email;

        @JsonProperty("exam_code")
        @NotBlank(message = "exam_code is required")
        private String examCode;

        @JsonProperty("subject_code")
        @NotBlank(message = "subject_code is required")
        private String subjectCode;

        @JsonProperty("device_id")
        @NotBlank(message = "device_id is required")
        private String deviceId;
    }

    @Data
    public static class QuestionData {
        @NotBlank(message = "question number is required")
        private String number;

        @JsonProperty("question_type")
        @NotBlank(message = "question_type is required")
        private String questionType;

        @NotBlank(message = "question text is required")
        private String text;

        @NotNull(message = "options are required")
        private List<OptionData> options;

        @JsonProperty("screenshot_base64")
        private String screenshotBase64;
    }

    @Data
    public static class OptionData {
        @NotBlank
        private String label;

        @NotBlank
        private String text;
    }
}
