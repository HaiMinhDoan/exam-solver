package com.examsolver.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Ngân hàng câu hỏi toàn hệ thống — dùng để tra cứu trước khi gọi AI.
 *
 * Logic tìm kiếm:
 *   1. Tìm theo questionHash (MD5 của normalized text) — exact match.
 *   2. Nếu không có hash match, tìm full-text (GIN index trên normalized_text).
 *
 * questionHash được tính từ text đã normalize (lowercase, bỏ dấu cách thừa, bỏ dấu câu).
 * Vì vậy cùng một câu hỏi ở nhiều môn khác nhau sẽ có cùng hash → dùng lại đáp án.
 */
@Entity
@Table(name = "question_bank",
        indexes = {
                @Index(name = "idx_qb_hash",    columnList = "question_hash", unique = true),
                @Index(name = "idx_qb_type",    columnList = "question_type"),
                @Index(name = "idx_qb_subject", columnList = "subject_code")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestionBank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Hash duy nhất của câu hỏi (MD5 của normalized question text + options text).
     * Dùng để lookup nhanh O(1).
     */
    @Column(name = "question_hash", nullable = false, unique = true, length = 64)
    @JsonProperty("question_hash")
    private String questionHash;

    /**
     * Text câu hỏi đã normalize: lowercase, strip extra spaces, strip punctuation.
     * Dùng để full-text search (pg_trgm hoặc to_tsvector).
     */
    @Column(name = "normalized_text", nullable = false, columnDefinition = "TEXT")
    @JsonProperty("normalized_text")
    private String normalizedText;

    /** Text gốc để hiển thị. */
    @Column(name = "original_text", nullable = false, columnDefinition = "TEXT")
    @JsonProperty("original_text")
    private String originalText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    @JsonProperty("question_type")
    private QuestionType questionType;

    /** JSONB: [{"label":"A","text":"..."},...] */
    @Column(name = "options", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @JsonProperty("options")
    private String optionsJson;

    /** Đáp án đúng: "A", "A,C", text tự luận... */
    @Column(nullable = false, columnDefinition = "TEXT")
    @JsonProperty("answer")
    private String answer;

    /**
     * Môn học đầu tiên câu hỏi được giải (tham khảo).
     * Không dùng để lookup — câu hỏi giống nhau ở nhiều môn vẫn dùng chung entry.
     */
    @Column(name = "subject_code", length = 50)
    @JsonProperty("subject_code")
    private String subjectCode;

    /** Số lần câu hỏi này được sử dụng (tra cứu thành công). */
    @Column(name = "hit_count", nullable = false)
    @JsonProperty("hit_count")
    @Builder.Default
    private long hitCount = 0;

    /** Đáp án được xác nhận đúng (admin verify hoặc nhiều lần đồng nhất). */
    @Column(name = "is_verified", nullable = false)
    @JsonProperty("is_verified")
    @Builder.Default
    private boolean verified = false;

    /** Prompt version đã dùng để giải lần đầu. */
    @Column(name = "prompt_version_id")
    @JsonProperty("prompt_version_id")
    private Long promptVersionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public enum QuestionType {
        SINGLECHOICE, MULTIPLECHOICE, TRUEFALSE, ESSAY
    }
}