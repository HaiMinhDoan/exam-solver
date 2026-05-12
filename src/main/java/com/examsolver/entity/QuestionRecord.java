package com.examsolver.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Lịch sử câu hỏi trong từng ExamSession cụ thể.
 * Khác với QuestionBank (ngân hàng toàn hệ thống),
 * bảng này lưu context của từng lần giải: ai giải, session nào, dùng bank hay AI...
 */
@Entity
@Table(name = "question_records",
        indexes = {
                @Index(name = "idx_qr_session",      columnList = "exam_session_id"),
                @Index(name = "idx_qr_hash",          columnList = "question_hash"),
                @Index(name = "idx_qr_bank_ref",      columnList = "question_bank_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id", nullable = false)
    private ExamSession examSession;

    /** Tham chiếu vào QuestionBank (nullable nếu câu hỏi chưa vào bank). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_bank_id")
    private QuestionBank questionBank;

    @Column(name = "question_hash", length = 64)
    private String questionHash;

    @Column(name = "question_number", length = 20)
    private String questionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionBank.QuestionType questionType;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "options", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String optionsJson;

    @Column(length = 512)
    private String answer;

    @Column(name = "auto_click", nullable = false)
    @Builder.Default
    private boolean autoClick = true;

    /** BANK = lấy từ ngân hàng câu hỏi; AI = gọi Claude; NONE = không tìm được */
    @Enumerated(EnumType.STRING)
    @Column(name = "answer_source", length = 10)
    private AnswerSource answerSource;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @Column(name = "prompt_version_id")
    private Long promptVersionId;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "has_screenshot", nullable = false)
    @Builder.Default
    private boolean hasScreenshot = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean success = false;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum AnswerSource { BANK, AI, NONE , HUMAN }
}