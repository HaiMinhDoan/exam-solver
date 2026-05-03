package com.examsolver.service.ai;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.SolveResponse;
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

    @Override
    public String getProviderName() {
        return "Google Gemini AI";
    }

    @Override
    public SolveResponse solveQuestion(SolveRequest request) {
        try {
            String prompt = buildPrompt(request);
            log.debug("Sending question [{}] to Gemini", request.getQuestionId());

            String rawResponse = callGeminiApi(prompt, request.getQuestion().getScreenshotBase64());
            return parseGeminiResponse(rawResponse, request.getQuestionId());

        } catch (WebClientResponseException e) {
            log.error("Gemini API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return errorResponse(request.getQuestionId(), "AI service error: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage());
            return errorResponse(request.getQuestionId(), "AI service error: " + e.getMessage());
        }
    }

    private String callGeminiApi(String prompt, String screenshotBase64) {
        List<Map<String, Object>> contentParts = new ArrayList<>();

        if (screenshotBase64 != null && !screenshotBase64.isBlank()) {
            String base64Data = screenshotBase64.contains(",") ? screenshotBase64.split(",")[1] : screenshotBase64;
            contentParts.add(Map.of(
                    "inline_data", Map.of(
                            "mime_type", "image/png",
                            "data", base64Data
                    )
            ));
        }
        contentParts.add(Map.of("text", prompt));

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", contentParts)),
                "generationConfig", Map.of(
                        "maxOutputTokens", maxTokens,
                        "responseMimeType", "application/json" // Gemini hỗ trợ ép kiểu JSON
                )
        );

        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/models/{model}:generateContent").build(model))
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    private SolveResponse parseGeminiResponse(String rawApiResponse, String questionId) {
        try {
            JsonNode apiRoot = objectMapper.readTree(rawApiResponse);

            // Đường dẫn response của Gemini: candidates -> content -> parts -> text
            String geminiText = apiRoot.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            JsonNode answerNode = objectMapper.readTree(geminiText);

            return SolveResponse.builder()
                    .success(answerNode.path("success").asBoolean(true))
                    .message(answerNode.path("message").asText("Answer processed"))
                    .questionId(answerNode.path("question_id").asText(questionId))
                    .answer(answerNode.path("answer").asText())
                    .autoClick(answerNode.path("auto_click").asBoolean(true))
                    .build();
        } catch (Exception e) {
            log.error("Parse error for question {}: {}", questionId, e.getMessage());
            return errorResponse(questionId, "Failed to parse AI response");
        }
    }

    // --- Các phương thức hỗ trợ buildPrompt, buildOptionsText, buildTypeInstruction giữ nguyên như cũ ---
    // (Vì logic prompt không thay đổi, chỉ có cách gọi API thay đổi)

    private SolveResponse errorResponse(String questionId, String message) {
        return SolveResponse.builder()
                .success(false)
                .message(message)
                .questionId(questionId)
                .autoClick(false)
                .build();
    }

    // Đảm bảo bạn copy các hàm private: buildPrompt, buildOptionsText, buildTypeInstruction, buildAnswerFormatGuide
    // từ code cũ của bạn vào đây.


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
}
