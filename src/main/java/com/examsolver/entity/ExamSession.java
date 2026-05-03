package com.examsolver.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "exam_sessions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ExamSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "exam_code", nullable = false)
    private String examCode;

    @Column(name = "subject_code", nullable = false)
    private String subjectCode;

    @Column(name = "device_id")
    private String deviceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "examSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuestionRecord> questions;
}
