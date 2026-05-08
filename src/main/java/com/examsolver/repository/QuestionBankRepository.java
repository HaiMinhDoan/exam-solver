package com.examsolver.repository;

import com.examsolver.entity.QuestionBank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    /** Lookup chính — hash match O(1). */
    Optional<QuestionBank> findByQuestionHash(String questionHash);

    /**
     * Tìm kiếm full-text dùng pg_trgm similarity.
     * Trả về các câu hỏi có normalized_text tương tự nhất.
     * Threshold 0.3 có thể điều chỉnh qua @param threshold.
     */
    @Query(value = """
            SELECT * FROM question_bank
            WHERE similarity(normalized_text, :text) > :threshold
              AND question_type = :questionType
            ORDER BY similarity(normalized_text, :text) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<QuestionBank> findBySimilarText(
            @Param("text") String normalizedText,
            @Param("questionType") String questionType,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );

    /** Tăng hit_count khi câu hỏi được sử dụng thành công. */
    @Modifying
    @Query("UPDATE QuestionBank q SET q.hitCount = q.hitCount + 1 WHERE q.id = :id")
    void incrementHitCount(@Param("id") Long id);

    /** Phân trang để tra cứu / quản lý. */
    Page<QuestionBank> findBySubjectCodeContainingIgnoreCase(String subjectCode, Pageable pageable);

    Page<QuestionBank> findByQuestionType(QuestionBank.QuestionType type, Pageable pageable);

    @Query("SELECT COUNT(q) FROM QuestionBank q WHERE q.verified = true")
    long countVerified();
}