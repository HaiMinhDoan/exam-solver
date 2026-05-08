package com.examsolver.service.impl;

import com.examsolver.dto.request.PromptVersionRequest;
import com.examsolver.dto.response.PromptVersionResponse;
import com.examsolver.entity.PromptVersion;
import com.examsolver.exception.BusinessException;
import com.examsolver.exception.ResourceNotFoundException;
import com.examsolver.repository.PromptVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Quản lý prompt versions.
 *
 * - Tạo version mới (versionNumber tự tăng per promptType).
 * - Activate một version → tất cả version còn lại của type đó bị deactivate.
 * - Rollback = activate một version cũ hơn.
 * - getActive() được ClaudeAiSolverService gọi mỗi lần giải bài.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PromptVersionService {

    private final PromptVersionRepository promptVersionRepository;

    /** Lấy version đang active cho một loại câu hỏi (hoặc SYSTEM). */
    public Optional<PromptVersion> getActive(String promptType) {
        return promptVersionRepository.findByPromptTypeAndActiveTrue(promptType);
    }

    /** Lấy tất cả versions của một type, mới nhất trước. */
    public List<PromptVersionResponse> listByType(String promptType) {
        return promptVersionRepository
                .findByPromptTypeOrderByVersionNumberDesc(promptType)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Tạo version mới (chưa active). */
    @Transactional
    public PromptVersionResponse createVersion(PromptVersionRequest req, String createdBy) {
        int nextVersion = promptVersionRepository
                .findMaxVersionNumber(req.getPromptType())
                .map(v -> v + 1)
                .orElse(1);

        PromptVersion pv = PromptVersion.builder()
                .promptType(req.getPromptType().toUpperCase())
                .versionNumber(nextVersion)
                .versionLabel(req.getVersionLabel())
                .promptTemplate(req.getPromptTemplate())
                .notes(req.getNotes())
                .createdBy(createdBy)
                .active(false)
                .build();

        PromptVersion saved = promptVersionRepository.save(pv);
        log.info("Created prompt version [{} v{}] by [{}]",
                req.getPromptType(), nextVersion, createdBy);
        return toResponse(saved);
    }

    /**
     * Activate một version cụ thể.
     * Deactivate tất cả versions còn lại của cùng promptType.
     * Đây cũng là cách rollback: truyền vào id của version cũ hơn.
     */
    @Transactional
    public PromptVersionResponse activateVersion(Long versionId, String activatedBy) {
        PromptVersion pv = promptVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt version not found: " + versionId));

        // Deactivate tất cả versions của type này
        promptVersionRepository.deactivateAllByType(pv.getPromptType());

        // Activate version được chọn
        pv.setActive(true);
        pv.setActivatedAt(LocalDateTime.now());
        PromptVersion saved = promptVersionRepository.save(pv);

        log.info("Activated prompt version [{} v{}] by [{}]",
                pv.getPromptType(), pv.getVersionNumber(), activatedBy);
        return toResponse(saved);
    }

    /** Xem chi tiết một version. */
    public PromptVersionResponse getById(Long id) {
        return toResponse(promptVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt version not found: " + id)));
    }

    private PromptVersionResponse toResponse(PromptVersion pv) {
        return PromptVersionResponse.builder()
                .id(pv.getId())
                .promptType(pv.getPromptType())
                .versionNumber(pv.getVersionNumber())
                .versionLabel(pv.getVersionLabel())
                .promptTemplate(pv.getPromptTemplate())
                .active(pv.isActive())
                .createdBy(pv.getCreatedBy())
                .notes(pv.getNotes())
                .createdAt(pv.getCreatedAt())
                .activatedAt(pv.getActivatedAt())
                .build();
    }
}