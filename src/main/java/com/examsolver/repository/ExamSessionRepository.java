package com.examsolver.repository;

import com.examsolver.entity.ExamSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    Page<ExamSession> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Optional<ExamSession> findByCustomerIdAndExamCodeAndSubjectCodeAndDeviceId(
            Long customerId, String examCode, String subjectCode, String deviceId);
}