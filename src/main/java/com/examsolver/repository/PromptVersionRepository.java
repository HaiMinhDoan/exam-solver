package com.examsolver.repository;

import com.examsolver.entity.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {

    /** Active prompt cho một loại câu hỏi. */
    Optional<PromptVersion> findByPromptTypeAndActiveTrue(String promptType);

    /** Tất cả versions của một type, sắp xếp mới nhất trước. */
    List<PromptVersion> findByPromptTypeOrderByVersionNumberDesc(String promptType);

    @Query("SELECT MAX(p.versionNumber) FROM PromptVersion p WHERE p.promptType = :type")
    Optional<Integer> findMaxVersionNumber(@Param("type") String type);

    /** Deactivate tất cả versions của một type trước khi activate version mới. */
    @Modifying
    @Query("UPDATE PromptVersion p SET p.active = false WHERE p.promptType = :type")
    void deactivateAllByType(@Param("type") String type);
}