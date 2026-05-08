package com.examsolver.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "exam_sessions",
        indexes = {
                @Index(name = "idx_es_customer",  columnList = "customer_id"),
                @Index(name = "idx_es_lookup",    columnList = "customer_id, exam_code, subject_code, device_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "exam_code", nullable = false, length = 100)
    private String examCode;

    @Column(name = "subject_code", nullable = false, length = 50)
    private String subjectCode;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "examSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuestionRecord> questions;
}