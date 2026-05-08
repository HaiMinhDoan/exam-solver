package com.examsolver.config;

import com.examsolver.entity.Customer;
import com.examsolver.entity.PromptVersion;
import com.examsolver.repository.CustomerRepository;
import com.examsolver.repository.PromptVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:admin@examsolver.vn}")
    private String adminEmail;
    @Value("${admin.password:Admin@12345}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        createDefaultAdmin();
        createDefaultPromptVersions();
    }

    private void createDefaultAdmin() {
        if (!customerRepository.existsByEmail(adminEmail)) {
            customerRepository.save(Customer.builder()
                    .email(adminEmail).fullName("System Admin")
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(Customer.Role.ADMIN).active(true).aiModeEnabled(true)
                    .build());
            log.info("Default admin created: {}", adminEmail);
        }
    }

    /**
     * Tạo default prompt version SYSTEM nếu chưa có version nào.
     * Admin có thể tạo thêm version mới và activate qua API.
     */
    private void createDefaultPromptVersions() {
        for (String type : new String[]{"SYSTEM", "SINGLECHOICE", "MULTIPLECHOICE", "TRUEFALSE", "ESSAY"}) {
            if (promptVersionRepository.findByPromptTypeAndActiveTrue(type).isEmpty()) {
                PromptVersion pv = PromptVersion.builder()
                        .promptType(type)
                        .versionNumber(1)
                        .versionLabel("Default v1")
                        .promptTemplate(buildDefaultTemplate(type))
                        .active(true)
                        .createdBy("system")
                        .notes("Auto-generated default. Replace via Admin API.")
                        .build();
                promptVersionRepository.save(pv);
                log.info("Created default prompt version for type: {}", type);
            }
        }
    }

    private String buildDefaultTemplate(String type) {
        String typeInstruction = switch (type) {
            case "SINGLECHOICE"   -> "Chọn MỘT đáp án đúng nhất.";
            case "MULTIPLECHOICE" -> "Chọn TẤT CẢ đáp án đúng, phân cách bằng dấu phẩy (VD: A,C).";
            case "TRUEFALSE"      -> "A = Đúng, B = Sai.";
            case "ESSAY"          -> "Viết câu trả lời tự luận đầy đủ bằng tiếng Anh(English).";
            default               -> "Phân tích và trả lời chính xác.";
        };

        return """
                Bạn là AI hỗ trợ giáo viên kiểm tra và soạn đề thi.
                Phân tích câu hỏi và trả về JSON CHÍNH XÁC, KHÔNG có text nào khác.
                
                Câu hỏi [Loại: {question_type}] [Môn: {subject_code}] [Số: {question_number}]:
                {question_text}
                
                {options}
                
                Yêu cầu: """ + typeInstruction + """
                
                Định dạng đáp án: {answer_format}
                
                Trả về JSON:
                {
                  "success": true,
                  "message": "Answer processed",
                  "question_id": "{question_id}",
                  "answer": "<ĐÁP ÁN>",
                  "auto_click": true
                }
                """;
    }
}