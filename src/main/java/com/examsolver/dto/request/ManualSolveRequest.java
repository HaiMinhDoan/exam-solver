package com.examsolver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request từ web UI — người dùng tự nhập câu hỏi.
 * Đơn giản hơn SolveRequest: không cần session info đầy đủ,
 * email lấy từ JWT token của người đang đăng nhập.
 */
@Data
public class ManualSolveRequest {

    @NotBlank(message = "Loại câu hỏi là bắt buộc")
    @JsonProperty("question_type")
    private String questionType; // SINGLECHOICE | MULTIPLECHOICE | TRUEFALSE | ESSAY

    @NotBlank(message = "Nội dung câu hỏi là bắt buộc")
    private String text;

    @NotNull(message = "Danh sách đáp án là bắt buộc")
    @NotEmpty
    private List<SolveRequest.OptionData> options;

    @JsonProperty("subject_code")
    private String subjectCode = "MANUAL";

    @JsonProperty("exam_code")
    private String examCode = "MANUAL";

    @JsonProperty("question_number")
    private String questionNumber = "1";

    /** Ảnh câu hỏi (optional, base64). */
    @JsonProperty("screenshot_base64")
    private String screenshotBase64;

    /**
     * Chuyển đổi sang SolveRequest chuẩn để tái dụng IngestService.
     * email được inject từ bên ngoài (JWT principal).
     */
    public SolveRequest toSolveRequest(String email) {
        SolveRequest.SessionInfo session = new SolveRequest.SessionInfo();
        session.setEmail(email);
        session.setExamCode(examCode != null ? examCode : "MANUAL");
        session.setSubjectCode(subjectCode != null ? subjectCode : "MANUAL");
        session.setDeviceId("web-ui");

        SolveRequest.QuestionData question = new SolveRequest.QuestionData();
        question.setNumber(questionNumber != null ? questionNumber : "1");
        question.setQuestionType(questionType);
        question.setText(text);
        question.setOptions(options);
        question.setScreenshotBase64(screenshotBase64);

        SolveRequest req = new SolveRequest();
        req.setQuestionId(UUID.randomUUID().toString());
        req.setSession(session);
        req.setQuestion(question);
        req.setCapturedAt(java.time.LocalDateTime.now().toString());
        req.setAiAnswer(null);
        return req;
    }
}