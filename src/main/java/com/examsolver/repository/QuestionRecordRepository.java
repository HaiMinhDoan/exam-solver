package com.examsolver.repository;

import com.examsolver.entity.QuestionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionRecordRepository extends JpaRepository<QuestionRecord, Long> {

    Optional<QuestionRecord> findByQuestionId(String questionId);

    Page<QuestionRecord> findByExamSessionId(Long sessionId, Pageable pageable);

    @Query("SELECT q FROM QuestionRecord q WHERE q.questionId = :questionId AND q.success = true ORDER BY q.createdAt DESC")
    Optional<QuestionRecord> findCachedAnswer(@Param("questionId") String questionId);

    @Query("SELECT COUNT(q) FROM QuestionRecord q WHERE q.examSession.customer.id = :customerId")
    long countByCustomerId(@Param("customerId") Long customerId);
}
