//package com.examsolver.service.ai;
//
//import com.examsolver.dto.request.SolveRequest;
//import com.examsolver.dto.response.SolveResponse;
//import com.examsolver.entity.PromptVersion;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.reactive.function.client.WebClientResponseException;
//
//import java.time.Duration;
//import java.util.List;
//import java.util.Map;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class ClaudeAiSolverService implements AiSolverService {
//
//    private final WebClient claudeWebClient;
//    private final ObjectMapper objectMapper;
//
//    @Value("${claude.api.model:claude-sonnet-4-20250514}")
//    private String model;
//
//    @Value("${claude.api.max-tokens:1024}")
//    private int maxTokens;
//
//    @Value("${claude.api.timeout:30}")
//    private int timeoutSeconds;
//
//    @Override
//    public String getProviderName() {
//        return "Claude AI (Anthropic)";
//    }
//
//    @Override
//    public SolveResponse solveQuestion(SolveRequest request, PromptVersion promptVersion) {
//        try {
//            String prompt = buildPrompt(request, promptVersion);
//            log.debug("Calling Claude for question [{}] type [{}]",
//                    request.getQuestionId(), request.getQuestion().getQuestionType());
//
//            String raw = callClaudeApi(prompt, request.getQuestion().getScreenshotBase64());
//            return parseClaudeResponse(raw, request.getQuestionId());
//
//        } catch (WebClientResponseException e) {
//            log.error("Claude HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
//            return errorResponse(request.getQuestionId(), "AI HTTP error: " + e.getStatusCode());
//        } catch (Exception e) {
//            log.error("Claude error for [{}]: {}", request.getQuestionId(), e.getMessage(), e);
//            return errorResponse(request.getQuestionId(), "AI error: " + e.getMessage());
//        }
//    }
//
//    // ─── Prompt Builder ──────────────────────────────────────────────────────
//
//    private String buildPrompt(SolveRequest request, PromptVersion promptVersion) {
//        // Nếu có template từ DB, dùng template đó (thay thế placeholders)
//        if (promptVersion != null && promptVersion.getPromptTemplate() != null
//                && !promptVersion.getPromptTemplate().isBlank()) {
//            return applyTemplate(promptVersion.getPromptTemplate(), request);
//        }
//        // Fallback: dùng default hardcoded prompt
//        return buildDefaultPrompt(request);
//    }
//
//    /**
//     * Áp dụng prompt template từ DB — thay thế các placeholder:
//     *   {question_text}, {options}, {question_type}, {subject_code},
//     *   {question_number}, {question_id}, {answer_format}
//     */
//    private String applyTemplate(String template, SolveRequest request) {
//        SolveRequest.QuestionData q = request.getQuestion();
//        return template
//                .replace("{question_text}",   q.getText())
//                .replace("{options}",          buildOptionsText(q.getOptions()))
//                .replace("{question_type}",    q.getQuestionType())
//                .replace("{subject_code}",     request.getSession().getSubjectCode())
//                .replace("{question_number}",  q.getNumber())
//                .replace("{question_id}",      request.getQuestionId())
//                .replace("{answer_format}",    buildAnswerFormatGuide(q.getQuestionType()));
//    }
//
//    private String buildDefaultPrompt(SolveRequest request) {
//        SolveRequest.QuestionData q = request.getQuestion();
//        return """
//                Bạn là AI hỗ trợ giáo viên kiểm tra và soạn đề thi.
//                Phân tích câu hỏi và trả về JSON CHÍNH XÁC theo định dạng yêu cầu.
//
//                === CÂU HỎI ===
//                Loại: %s | Môn: %s | Số: %s
//
//                %s
//
//                %s
//
//                === YÊU CẦU ===
//                %s
//
//                === ĐỊNH DẠNG TRẢ VỀ (JSON DUY NHẤT, KHÔNG CÓ TEXT KHÁC) ===
//                {
//                  "success": true,
//                  "message": "Answer processed",
//                  "question_id": "%s",
//                  "answer": "<ĐÁP ÁN>",
//                  "auto_click": true
//                }
//
//                Quy tắc <ĐÁP ÁN>: %s
//                """.formatted(
//                q.getQuestionType(),
//                request.getSession().getSubjectCode(),
//                q.getNumber(),
//                q.getText(),
//                buildOptionsText(q.getOptions()),
//                buildTypeInstruction(q.getQuestionType()),
//                request.getQuestionId(),
//                buildAnswerFormatGuide(q.getQuestionType())
//        );
//    }
//
//    private String buildOptionsText(List<SolveRequest.OptionData> options) {
//        if (options == null || options.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder("Các lựa chọn:\n");
//        options.forEach(o -> sb.append("  ").append(o.getLabel()).append(". ").append(o.getText()).append("\n"));
//        return sb.toString();
//    }
//
//    private String buildTypeInstruction(String type) {
//        return switch (type.toUpperCase()) {
//            case "SINGLECHOICE"   -> "Chọn MỘT đáp án đúng nhất.";
//            case "MULTIPLECHOICE" -> "Chọn TẤT CẢ đáp án đúng (có thể nhiều hơn một).";
//            case "TRUEFALSE"      -> "A = Đúng, B = Sai. Xác định nhận định đúng hay sai.";
//            case "ESSAY"          -> "Viết câu trả lời tự luận đầy đủ, súc tích bằng tiếng Việt.";
//            default               -> "Trả lời câu hỏi một cách chính xác.";
//        };
//    }
//
//    private String buildAnswerFormatGuide(String type) {
//        return switch (type.toUpperCase()) {
//            case "SINGLECHOICE"   -> "Một chữ cái: \"A\", \"B\", \"C\" hoặc \"D\"";
//            case "MULTIPLECHOICE" -> "Các chữ cái phân cách bởi dấu phẩy: \"A,C\" hoặc \"A,B,D\"";
//            case "TRUEFALSE"      -> "\"A\" nếu Đúng, \"B\" nếu Sai";
//            case "ESSAY"          -> "Nội dung câu trả lời đầy đủ";
//            default               -> "Đáp án phù hợp";
//        };
//    }
//
//    // ─── API Call ────────────────────────────────────────────────────────────
//
//    private String callClaudeApi(String prompt, String screenshotBase64) {
//        List<Map<String, Object>> content = buildContent(prompt, screenshotBase64);
//        Map<String, Object> body = Map.of(
//                "model", model,
//                "max_tokens", maxTokens,
//                "messages", List.of(Map.of("role", "user", "content", content))
//        );
//
//        return claudeWebClient.post()
//                .uri("/v1/messages")
//                .bodyValue(body)
//                .retrieve()
//                .bodyToMono(String.class)
//                .timeout(Duration.ofSeconds(timeoutSeconds))
//                .block();
//    }
//
//    private List<Map<String, Object>> buildContent(String prompt, String screenshot) {
//        if (screenshot != null && !screenshot.isBlank()) {
//            String b64 = screenshot.contains(",") ? screenshot.split(",")[1] : screenshot;
//            return List.of(
//                    Map.of("type", "image", "source", Map.of(
//                            "type", "base64", "media_type", "image/png", "data", b64)),
//                    Map.of("type", "text", "text", prompt)
//            );
//        }
//        return List.of(Map.of("type", "text", "text", prompt));
//    }
//
//    // ─── Response Parser ─────────────────────────────────────────────────────
//
//    private SolveResponse parseClaudeResponse(String raw, String questionId) {
//        try {
//            String text = objectMapper.readTree(raw)
//                    .path("content").get(0).path("text").asText();
//            log.debug("Claude text for [{}]: {}", questionId, text);
//
//            JsonNode node = objectMapper.readTree(extractJson(text));
//            return SolveResponse.builder()
//                    .success(node.path("success").asBoolean(true))
//                    .message(node.path("message").asText("Answer processed"))
//                    .questionId(node.path("question_id").asText(questionId))
//                    .answer(node.path("answer").asText())
//                    .autoClick(node.path("auto_click").asBoolean(true))
//                    .build();
//        } catch (Exception e) {
//            log.error("Parse error for [{}]: {}", questionId, e.getMessage());
//            return errorResponse(questionId, "Failed to parse AI response");
//        }
//    }
//
//    private String extractJson(String text) {
//        if (text == null) throw new IllegalArgumentException("Empty Claude response");
//        String t = text.trim();
//        if (t.startsWith("```json")) t = t.substring(7);
//        else if (t.startsWith("```")) t = t.substring(3);
//        if (t.endsWith("```")) t = t.substring(0, t.lastIndexOf("```"));
//        int s = t.indexOf('{'), e = t.lastIndexOf('}');
//        if (s >= 0 && e > s) return t.substring(s, e + 1);
//        return t.trim();
//    }
//
//    private SolveResponse errorResponse(String questionId, String message) {
//        return SolveResponse.builder()
//                .success(false).message(message)
//                .questionId(questionId).autoClick(false).build();
//    }
//}