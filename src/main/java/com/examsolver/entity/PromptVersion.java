package com.examsolver.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Lưu trữ các phiên bản prompt gửi cho Claude AI.
 *
 * Mỗi lần admin thay đổi cách prompt, tạo một version mới.
 * Chỉ có MỘT version là active tại mỗi thời điểm (per promptType).
 * Cho phép rollback về version cũ chỉ bằng cách đổi isActive.
 */
@Entity
@Table(name = "prompt_versions",
        indexes = {
                @Index(name = "idx_pv_active", columnList = "prompt_type, is_active"),
                @Index(name = "idx_pv_version", columnList = "version_number")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PromptVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Loại prompt: SINGLECHOICE, MULTIPLECHOICE, TRUEFALSE, ESSAY, SYSTEM
     * SYSTEM = system prompt dùng chung cho tất cả loại câu hỏi.
     */
    @Column(name = "prompt_type", nullable = false, length = 30)
    private String promptType;

    /** Số thứ tự version, tự tăng theo promptType. */
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "version_label", length = 100)
    private String versionLabel;

    /** Nội dung template prompt. Dùng placeholder: {question}, {options}, {subject_code} ... */
    @Column(name = "prompt_template", nullable = false, columnDefinition = "TEXT")
    private String promptTemplate;

    /** Chỉ một version active trên mỗi promptType. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = false;

    @Column(name = "created_by")
    private String createdBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
}