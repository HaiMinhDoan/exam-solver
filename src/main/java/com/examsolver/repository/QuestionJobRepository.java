package com.examsolver.repository;

import com.examsolver.entity.QuestionJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionJobRepository extends JpaRepository<QuestionJob, Long> {

    Optional<QuestionJob> findByQuestionId(String questionId);

    Page<QuestionJob> findByCustomerEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    Page<QuestionJob> findByCustomerEmailAndStatusOrderByCreatedAtDesc(
            String email, QuestionJob.JobStatus status, Pageable pageable);

    /** Lấy PENDING jobs cũ bị stuck (executor crash mà chưa xử lý) để re-queue. */
    @Query("SELECT j FROM QuestionJob j WHERE j.status = 'PENDING' AND j.createdAt < :before")
    List<QuestionJob> findStuckPendingJobs(@Param("before") LocalDateTime before);

    /** Lấy PROCESSING jobs bị stuck (executor crash giữa chừng). */
    @Query("SELECT j FROM QuestionJob j WHERE j.status = 'PROCESSING' " +
            "AND j.processingStartedAt < :before AND j.retryCount < j.maxRetries")
    List<QuestionJob> findStuckProcessingJobs(@Param("before") LocalDateTime before);

    @Modifying
    @Query("UPDATE QuestionJob j SET j.status = 'PENDING', j.retryCount = j.retryCount + 1 " +
            "WHERE j.id = :id")
    void resetToPending(@Param("id") Long id);

    /** Câu hỏi đang chờ giáo viên giải — có thể filter theo examCode. */
    @Query("SELECT j FROM QuestionJob j WHERE j.resolverType = 'HUMAN' " +
            "AND j.status = 'WAITING_HUMAN' " +
            "AND (:examCode IS NULL OR j.examCode = :examCode) " +
            "AND (:subjectCode IS NULL OR j.subjectCode = :subjectCode) " +
            "ORDER BY j.createdAt ASC")
    org.springframework.data.domain.Page<QuestionJob> findWaitingForHuman(
            @Param("examCode") String examCode,
            @Param("subjectCode") String subjectCode,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(j) FROM QuestionJob j WHERE j.resolverType = 'HUMAN' " +
            "AND j.status = 'WAITING_HUMAN'")
    long countWaitingForHuman();

    @Query("SELECT COUNT(j) FROM QuestionJob j WHERE j.customerEmail = :email AND j.status = :status")
    long countByEmailAndStatus(@Param("email") String email,
                               @Param("status") QuestionJob.JobStatus status);
}