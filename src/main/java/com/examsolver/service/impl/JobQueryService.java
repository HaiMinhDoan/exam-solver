package com.examsolver.service.impl;

import com.examsolver.dto.response.JobStatusResponse;
import com.examsolver.entity.QuestionJob;
import com.examsolver.exception.BusinessException;
import com.examsolver.exception.ResourceNotFoundException;
import com.examsolver.repository.QuestionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobQueryService {

    private final QuestionJobRepository questionJobRepository;

    public JobStatusResponse getJobStatus(Long jobId, String requesterEmail) {
        QuestionJob job = questionJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        // Chỉ chủ sở hữu mới xem được (admin check ở controller level)
        if (!job.getCustomerEmail().equals(requesterEmail)) {
            throw new BusinessException("Bạn không có quyền xem job này.");
        }

        return toResponse(job);
    }

    public Page<JobStatusResponse> getMyJobs(String email, String status, Pageable pageable) {
        if (status != null) {
            QuestionJob.JobStatus jobStatus = QuestionJob.JobStatus.valueOf(status.toUpperCase());
            return questionJobRepository
                    .findByCustomerEmailAndStatusOrderByCreatedAtDesc(email, jobStatus, pageable)
                    .map(this::toResponse);
        }
        return questionJobRepository
                .findByCustomerEmailOrderByCreatedAtDesc(email, pageable)
                .map(this::toResponse);
    }

    private JobStatusResponse toResponse(QuestionJob job) {
        return JobStatusResponse.builder()
                .jobId(job.getId())
                .questionId(job.getQuestionId())
                .status(job.getStatus().name())
                .answer(job.getAnswer())
                .answerSource(job.getAnswerSource() != null ? job.getAnswerSource().name() : null)
                .autoClick(job.getStatus() == QuestionJob.JobStatus.DONE)
                .errorMessage(job.getErrorMessage())
                .processingTimeMs(job.getProcessingTimeMs())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}