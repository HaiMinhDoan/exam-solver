package com.examsolver.service.ai;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.SolveResponse;
import com.examsolver.entity.PromptVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiAiSolverService implements AiSolverService {

    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.model:gemini-1.5-flash}")
    private String model;

    @Value("${gemini.api.max-tokens:1024}")
    private int maxTokens;

    @Value("${gemini.api.timeout:30}")
    private int timeoutSeconds;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Override
    public String getProviderName() {
        return "Google Gemini AI";
    }

    @Override
    public SolveResponse solveQuestion(SolveRequest request, PromptVersion promptVersion) {
        try {
            // Logic build prompt linh hoạt (Template từ DB hoặc Default)
            String prompt = buildPrompt(request, promptVersion);

            log.debug("Calling Gemini for question [{}] type [{}]",
                    request.getQuestionId(), request.getQuestion().getQuestionType());

            String rawResponse = callGeminiApi(prompt, request.getQuestion().getScreenshotBase64());
            return parseGeminiResponse(rawResponse, request.getQuestionId());

        } catch (WebClientResponseException e) {
            log.error("Gemini HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return errorResponse(request.getQuestionId(), "Gemini HTTP error: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Gemini error for [{}]: {}", request.getQuestionId(), e.getMessage(), e);
            return errorResponse(request.getQuestionId(), "Gemini error: " + e.getMessage());
        }
    }

    // ─── API Call ────────────────────────────────────────────────────────────

    private String callGeminiApi(String prompt, String screenshotBase64) {
        List<Map<String, Object>> parts = new ArrayList<>();

        // Thêm hình ảnh nếu có
        if (screenshotBase64 != null && !screenshotBase64.isBlank()) {
            String base64Data = screenshotBase64.contains(",") ? screenshotBase64.split(",")[1] : screenshotBase64;
            parts.add(Map.of(
                    "inline_data", Map.of(
                            "mime_type", "image/png",
                            "data", base64Data
                    )
            ));
        }

        // Thêm text prompt
        parts.add(Map.of("text", prompt));

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of(
                        "maxOutputTokens", maxTokens,
                        "responseMimeType", "application/json" // Gemini sẽ trả về JSON thuần, không kèm markdown
                )
        );

        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(model))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    // ─── Prompt Builder (Tương thích với Claude logic) ────────────────────────

    private String buildPrompt(SolveRequest request, PromptVersion promptVersion) {
        if (promptVersion != null && promptVersion.getPromptTemplate() != null
                && !promptVersion.getPromptTemplate().isBlank()) {
            return applyTemplate(promptVersion.getPromptTemplate(), request);
        }
        return buildDefaultPrompt(request);
    }

    private String applyTemplate(String template, SolveRequest request) {
        SolveRequest.QuestionData q = request.getQuestion();
        return template
                .replace("{question_text}",   q.getText())
                .replace("{options}",         buildOptionsText(q.getOptions()))
                .replace("{question_type}",    q.getQuestionType())
                .replace("{subject_code}",     request.getSession().getSubjectCode())
                .replace("{question_number}",  q.getNumber())
                .replace("{question_id}",      request.getQuestionId())
                .replace("{answer_format}",    buildAnswerFormatGuide(q.getQuestionType()));
    }

    private String buildDefaultPrompt(SolveRequest request) {
        SolveRequest.QuestionData q = request.getQuestion();
        return """
                Bạn là AI hỗ trợ giáo viên kiểm tra và soạn đề thi.
                Phân tích câu hỏi và trả lời JSON CHÍNH XÁC.

                === CÂU HỎI ===
                Loại: %s | Môn: %s | Số: %s
                Nội dung: %s
                %s

                === ĐỊNH DẠNG JSON BẮT BUỘC ===
                {
                  "success": true,
                  "message": "Answer processed",
                  "question_id": "%s",
                  "answer": "<ĐÁP ÁN>",
                  "auto_click": true
                }
                Quy tắc <ĐÁP ÁN>: %s
                """.formatted(
                q.getQuestionType(), request.getSession().getSubjectCode(), q.getNumber(),
                q.getText(), buildOptionsText(q.getOptions()),
                request.getQuestionId(), buildAnswerFormatGuide(q.getQuestionType())
        );
    }

    // ─── Response Parser ─────────────────────────────────────────────────────

    private SolveResponse parseGeminiResponse(String raw, String questionId) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            // Cấu trúc của Gemini: candidates[0].content.parts[0].text
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            JsonNode node = objectMapper.readTree(text);
            return SolveResponse.builder()
                    .success(node.path("success").asBoolean(true))
                    .message(node.path("message").asText("Answer processed"))
                    .questionId(node.path("question_id").asText(questionId))
                    .answer(node.path("answer").asText())
                    .autoClick(node.path("auto_click").asBoolean(true))
                    .build();
        } catch (Exception e) {
            log.error("Parse error for [{}]: {}", questionId, e.getMessage());
            return errorResponse(questionId, "Failed to parse Gemini response");
        }
    }

    // ─── Helper Methods (Giữ nguyên logic format) ───────────────────────────

    private String buildOptionsText(List<SolveRequest.OptionData> options) {
        if (options == null || options.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nCác lựa chọn:\n");
        options.forEach(o -> sb.append("  ").append(o.getLabel()).append(". ").append(o.getText()).append("\n"));
        return sb.toString();
    }

    private String buildAnswerFormatGuide(String type) {
        return switch (type.toUpperCase()) {
            case "SINGLECHOICE"   -> "Một chữ cái: \"A\", \"B\", \"C\" hoặc \"D\"";
            case "MULTIPLECHOICE" -> "Các chữ cái phân cách bởi dấu phẩy: \"A,C\"";
            case "TRUEFALSE"      -> "\"A\" nếu Đúng, \"B\" nếu Sai";
            case "ESSAY"          -> "Nội dung câu trả lời đầy đủ";
            default               -> "Đáp án phù hợp";
        };
    }

    private SolveResponse errorResponse(String questionId, String message) {
        return SolveResponse.builder()
                .success(false).message(message)
                .questionId(questionId).autoClick(false).build();
    }
}