package com.examsolver.service.ai;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.SolveResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Claude AI implementation của AiSolverService.
 *
 * Sử dụng Anthropic Claude API để phân tích câu hỏi và trả về đáp án
 * theo đúng định dạng response của hệ thống.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaudeAiSolverService implements AiSolverService {

    private final WebClient claudeWebClient;
    private final ObjectMapper objectMapper;

    @Value("${claude.api.model:claude-sonnet-4-20250514}")
    private String model;

    @Value("${claude.api.max-tokens:1024}")
    private int maxTokens;

    @Value("${claude.api.timeout:30}")
    private int timeoutSeconds;

    @Override
    public String getProviderName() {
        return "Claude AI (Anthropic)";
    }

    @Override
    public SolveResponse solveQuestion(SolveRequest request) {
        try {
            String prompt = buildPrompt(request);
            log.debug("Sending question [{}] type [{}] to Claude", 
                    request.getQuestionId(), request.getQuestion().getQuestionType());

            String rawResponse = callClaudeApi(prompt, request.getQuestion().getScreenshotBase64());
            return parseClaudeResponse(rawResponse, request.getQuestionId());

        } catch (WebClientResponseException e) {
            log.error("Claude API HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return errorResponse(request.getQuestionId(), "AI service HTTP error: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Error calling Claude API for question {}: {}", request.getQuestionId(), e.getMessage(), e);
            return errorResponse(request.getQuestionId(), "AI service error: " + e.getMessage());
        }
    }

    // ─── Prompt Builder ──────────────────────────────────────────────────────

    /**
     * Xây dựng prompt theo từng loại câu hỏi.
     * Prompt được thiết kế để Claude trả về JSON thuần túy.
     */
    private String buildPrompt(SolveRequest request) {
        SolveRequest.QuestionData q = request.getQuestion();
        String questionType = q.getQuestionType();

        String optionsText = buildOptionsText(q.getOptions());
        String typeInstruction = buildTypeInstruction(questionType, q.getOptions());

        return """
                Bạn là một AI hỗ trợ giải bài tập học thuật cho giáo viên. 
                Hãy phân tích câu hỏi và trả lời CHÍNH XÁC theo định dạng JSON được yêu cầu.
                
                === THÔNG TIN CÂU HỎI ===
                Loại câu hỏi: %s
                Số thứ tự: %s
                Môn học: %s
                
                Câu hỏi:
                %s
                
                %s
                
                === YÊU CẦU TRẢ LỜI ===
                %s
                
                === ĐỊNH DẠNG JSON BẮT BUỘC ===
                Chỉ trả về JSON, KHÔNG có bất kỳ text nào khác, KHÔNG có markdown, KHÔNG có giải thích bên ngoài JSON:
                
                {
                  "success": true,
                  "message": "Answer processed",
                  "question_id": "%s",
                  "answer": "<ĐÁP ÁN>",
                  "auto_click": true
                }
                
                Trong đó <ĐÁP ÁN> phải là:
                %s
                """.formatted(
                questionType,
                q.getNumber(),
                request.getSession().getSubjectCode(),
                q.getText(),
                optionsText,
                typeInstruction,
                request.getQuestionId(),
                buildAnswerFormatGuide(questionType)
        );
    }

    private String buildOptionsText(List<SolveRequest.OptionData> options) {
        if (options == null || options.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Các lựa chọn:\n");
        for (SolveRequest.OptionData opt : options) {
            sb.append("  ").append(opt.getLabel()).append(". ").append(opt.getText()).append("\n");
        }
        return sb.toString();
    }

    private String buildTypeInstruction(String questionType, List<SolveRequest.OptionData> options) {
        return switch (questionType.toUpperCase()) {
            case "SINGLECHOICE" -> """
                    Hãy chọn MỘT đáp án đúng nhất trong các lựa chọn trên.
                    Phân tích từng lựa chọn và xác định đáp án chính xác nhất.
                    """;
            case "MULTIPLECHOICE" -> """
                    Hãy chọn TẤT CẢ đáp án đúng trong các lựa chọn trên.
                    Có thể có từ 2 đáp án trở lên là đúng.
                    """;
            case "TRUEFALSE" -> """
                    Đây là câu hỏi Đúng/Sai.
                    A = Đúng (True), B = Sai (False).
                    Hãy xác định nhận định trong câu hỏi là Đúng hay Sai.
                    """;
            case "ESSAY" -> """
                    Đây là câu hỏi tự luận.
                    Hãy viết câu trả lời đầy đủ, chính xác và súc tích bằng tiếng Việt.
                    """;
            default -> "Hãy phân tích và trả lời câu hỏi một cách chính xác.";
        };
    }

    private String buildAnswerFormatGuide(String questionType) {
        return switch (questionType.toUpperCase()) {
            case "SINGLECHOICE" -> "Một chữ cái duy nhất, ví dụ: \"A\" hoặc \"B\" hoặc \"C\" hoặc \"D\"";
            case "MULTIPLECHOICE" -> "Các chữ cái phân cách bằng dấu phẩy, ví dụ: \"A,C\" hoặc \"A,B,D\"";
            case "TRUEFALSE" -> "\"A\" nếu Đúng, \"B\" nếu Sai";
            case "ESSAY" -> "Nội dung câu trả lời đầy đủ bằng tiếng Việt";
            default -> "Đáp án phù hợp với loại câu hỏi";
        };
    }

    // ─── Claude API Call ─────────────────────────────────────────────────────

    private String callClaudeApi(String prompt, String screenshotBase64) {
        List<Map<String, Object>> content = buildMessageContent(prompt, screenshotBase64);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "user", "content", content)
                )
        );

        return claudeWebClient.post()
                .uri("/v1/messages")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    private List<Map<String, Object>> buildMessageContent(String prompt, String screenshotBase64) {
        if (screenshotBase64 != null && !screenshotBase64.isBlank()) {
            // Có ảnh - gửi multimodal (text + image)
            String base64Data = screenshotBase64.contains(",")
                    ? screenshotBase64.split(",")[1]
                    : screenshotBase64;

            return List.of(
                    Map.of(
                            "type", "image",
                            "source", Map.of(
                                    "type", "base64",
                                    "media_type", "image/png",
                                    "data", base64Data
                            )
                    ),
                    Map.of("type", "text", "text", prompt)
            );
        }
        // Chỉ text
        return List.of(Map.of("type", "text", "text", prompt));
    }

    // ─── Response Parser ─────────────────────────────────────────────────────

    /**
     * Parse response từ Claude API thành SolveResponse.
     * Claude được prompt để trả về JSON thuần túy.
     */
    private SolveResponse parseClaudeResponse(String rawApiResponse, String questionId) {
        try {
            JsonNode apiRoot = objectMapper.readTree(rawApiResponse);

            // Lấy text content từ Claude response
            String claudeText = apiRoot
                    .path("content")
                    .get(0)
                    .path("text")
                    .asText();

            log.debug("Claude raw text for question {}: {}", questionId, claudeText);

            // Claude được prompt trả về JSON - parse nó
            String cleanJson = extractJson(claudeText);
            JsonNode answerNode = objectMapper.readTree(cleanJson);

            return SolveResponse.builder()
                    .success(answerNode.path("success").asBoolean(true))
                    .message(answerNode.path("message").asText("Answer processed"))
                    .questionId(answerNode.path("question_id").asText(questionId))
                    .answer(answerNode.path("answer").asText())
                    .autoClick(answerNode.path("auto_click").asBoolean(true))
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Claude response for question {}: {}", questionId, e.getMessage());
            return errorResponse(questionId, "Failed to parse AI response");
        }
    }

    /**
     * Trích xuất JSON từ text, xử lý trường hợp Claude vô tình wrap trong markdown.
     */
    private String extractJson(String text) {
        if (text == null) throw new IllegalArgumentException("Empty response from Claude");

        String trimmed = text.trim();

        // Loại bỏ markdown code block nếu có
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
            int end = trimmed.lastIndexOf("```");
            if (end > 0) trimmed = trimmed.substring(0, end);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
            int end = trimmed.lastIndexOf("```");
            if (end > 0) trimmed = trimmed.substring(0, end);
        }

        // Tìm JSON object
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed.trim();
    }

    private SolveResponse errorResponse(String questionId, String message) {
        return SolveResponse.builder()
                .success(false)
                .message(message)
                .questionId(questionId)
                .autoClick(false)
                .build();
    }
}
