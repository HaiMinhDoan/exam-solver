package com.examsolver.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Khách hàng / giáo viên sử dụng hệ thống.
 *
 * Xác thực bằng email + JWT. Không còn API key.
 * accessExpiresAt  — admin cài thời hạn sử dụng; null = chưa cấp hoặc không giới hạn (ADMIN).
 * aiModeEnabled    — per-customer, bật/tắt việc gọi Claude khi không tìm thấy trong bank.
 */
@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.CUSTOMER;

    /** Thời điểm hết hạn quyền sử dụng. ADMIN = null (không giới hạn). */
    @Column(name = "access_expires_at")
    private LocalDateTime accessExpiresAt;

    /**
     * Bật: ưu tiên ngân hàng câu hỏi → nếu miss mới gọi Claude AI.
     * Tắt: chỉ tra ngân hàng, không gọi AI.
     */
    @Column(name = "ai_mode_enabled", nullable = false)
    @Builder.Default
    private boolean aiModeEnabled = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ExamSession> examSessions;

    public boolean hasValidAccess() {
        if (!active) return false;
        if (role == Role.ADMIN) return true;
        if (accessExpiresAt == null) return false;
        return LocalDateTime.now().isBefore(accessExpiresAt);
    }

    public enum Role { CUSTOMER, ADMIN }
}