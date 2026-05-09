package com.examsolver.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Mỗi câu hỏi gửi lên tạo ra một QuestionJob.
 *
 * Vòng đời:
 *   PENDING  → vừa nhận, đã lưu DB, đã fire Kafka, chưa xử lý.
 *   PROCESSING → executor đang xử lý (bank lookup hoặc AI).
 *   DONE     → có đáp án, lưu vào question_records và question_bank.
 *   FAILED   → xử lý thất bại (AI error, timeout...), có error_message.
 *   SKIPPED  → câu hỏi bị bỏ qua (AI mode off, không tìm thấy trong bank).
 */
@Entity
@Table(name = "question_jobs",
        indexes = {
                @Index(name = "idx_qj_status",      columnList = "status"),
                @Index(name = "idx_qj_customer",     columnList = "customer_email"),
                @Index(name = "idx_qj_question_id",  columnList = "question_id"),
                @Index(name = "idx_qj_created",      columnList = "created_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hash ID câu hỏi từ client (Rust). */
    @Column(name = "question_id", nullable = false, length = 256)
    private String questionId;

    /** Normalized hash để lookup bank. */
    @Column(name = "question_hash", length = 64)
    private String questionHash;

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    @Column(name = "exam_code",    nullable = false, length = 100)
    private String examCode;

    @Column(name = "subject_code", nullable = false, length = 50)
    private String subjectCode;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "question_number", length = 20)
    private String questionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionBank.QuestionType questionType;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** JSONB: [{"label":"A","text":"..."},...] */
    @Column(name = "options", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String optionsJson;

    /** base64 ảnh (nullable). Lưu tạm để executor dùng khi gọi AI. */
    @Column(name = "screenshot_base64", columnDefinition = "TEXT")
    private String screenshotBase64;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    /** Đáp án sau khi xử lý xong. */
    @Column(columnDefinition = "TEXT")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_source", length = 10)
    private QuestionRecord.AnswerSource answerSource;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Số lần đã retry (executor retry khi FAILED). */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 3;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processing_finished_at")
    private LocalDateTime processingFinishedAt;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    public enum JobStatus {
        PENDING,     // Đã nhận, chờ executor
        PROCESSING,  // Executor đang xử lý
        DONE,        // Có đáp án
        FAILED,      // Xử lý lỗi, hết retry
        SKIPPED      // AI off + không có trong bank
    }
}