package com.examsolver.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_records")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class QuestionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id", nullable = false)
    private ExamSession examSession;

    @Column(name = "question_id", nullable = false, length = 256)
    private String questionId;

    @Column(name = "question_number")
    private String questionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /**
     * Stored as JSONB: [{"label":"A","text":"..."},...]
     */
    @Column(name = "options", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String optionsJson;

    @Column(name = "answer", length = 512)
    private String answer;

    @Column(name = "auto_click", nullable = false)
    @Builder.Default
    private boolean autoClick = true;

    @Column(name = "ai_model_used")
    private String aiModelUsed;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "has_screenshot", nullable = false)
    @Builder.Default
    private boolean hasScreenshot = false;

    @Column(name = "success", nullable = false)
    @Builder.Default
    private boolean success = false;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum QuestionType {
        SINGLECHOICE, MULTIPLECHOICE, TRUEFALSE, ESSAY
    }
}
