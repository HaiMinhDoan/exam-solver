package com.examsolver.repository;

import com.examsolver.entity.QuestionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRecordRepository extends JpaRepository<QuestionRecord, Long> {

    Page<QuestionRecord> findByExamSessionIdOrderByCreatedAtAsc(Long sessionId, Pageable pageable);

    List<QuestionRecord> findByExamSessionId(Long sessionId);

    /** Câu hỏi đã được giải trong 1 session — dùng để check trùng. */
    @Query("SELECT q FROM QuestionRecord q WHERE q.examSession.id = :sessionId " +
            "AND q.questionHash = :hash AND q.success = true")
    List<QuestionRecord> findSolvedInSession(@Param("sessionId") Long sessionId,
                                             @Param("hash") String hash);

    @Query("SELECT COUNT(q) FROM QuestionRecord q WHERE q.examSession.customer.id = :customerId")
    long countByCustomerId(@Param("customerId") Long customerId);

    /** Thống kê nguồn đáp án cho 1 session. */
    @Query("SELECT q.answerSource, COUNT(q) FROM QuestionRecord q " +
            "WHERE q.examSession.id = :sessionId GROUP BY q.answerSource")
    List<Object[]> countByAnswerSourceInSession(@Param("sessionId") Long sessionId);
}