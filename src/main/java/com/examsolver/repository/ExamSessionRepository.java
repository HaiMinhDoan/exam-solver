package com.examsolver.repository;

import com.examsolver.entity.ExamSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    Page<ExamSession> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Optional<ExamSession> findByCustomerIdAndExamCodeAndSubjectCodeAndDeviceId(
            Long customerId, String examCode, String subjectCode, String deviceId);

    /**
     * Lấy danh sách exam sessions mà có câu hỏi đang chờ giáo viên giải (HUMAN resolver).
     * Order by most recent created_at descending.
     */
    @Query("SELECT DISTINCT es FROM ExamSession es " +
            "JOIN QuestionJob qj ON qj.examCode = es.examCode " +
            "  AND qj.subjectCode = es.subjectCode " +
            "  AND (qj.deviceId = es.deviceId OR (qj.deviceId IS NULL AND es.deviceId IS NULL)) " +
            "  AND qj.customerEmail = es.customer.email " +
            "WHERE es.customer.id = :customerId " +
            "  AND qj.resolverType = 'HUMAN' " +
            "  AND qj.status = 'WAITING_HUMAN' " +
            "ORDER BY es.createdAt DESC")
    Page<ExamSession> findHumanSessions(@Param("customerId") Long customerId, Pageable pageable);


    /**
     * Lấy danh sách exam sessions mà có câu hỏi đang chờ giáo viên giải (HUMAN resolver).
     * Order by most recent created_at descending.
     */
    @Query("SELECT DISTINCT es FROM ExamSession es " +
            "JOIN QuestionJob qj ON qj.examCode = es.examCode " +
            "  AND qj.subjectCode = es.subjectCode " +
            "  AND (qj.deviceId = es.deviceId OR (qj.deviceId IS NULL AND es.deviceId IS NULL)) " +
            "  AND qj.customerEmail = es.customer.email " +
            "WHERE es.customer.id = :customerId " +
            "  AND qj.resolverType = 'HUMAN' " +
            "ORDER BY es.createdAt DESC")
    Page<ExamSession> findAllOfHumanSessions(@Param("customerId") Long customerId, Pageable pageable);
}