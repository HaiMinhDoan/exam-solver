package com.examsolver.service.impl;

import com.examsolver.dto.request.HumanAnswerRequest;
import com.examsolver.dto.response.JobStatusResponse;
import com.examsolver.entity.QuestionBank;
import com.examsolver.entity.QuestionJob;
import com.examsolver.entity.QuestionRecord;
import com.examsolver.exception.BusinessException;
import com.examsolver.exception.ResourceNotFoundException;
import com.examsolver.repository.QuestionJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examsolver.dto.request.SolveRequest;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class HumanSolveService {

    private final QuestionJobRepository questionJobRepository;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;

    /**
     * Giáo viên xem danh sách câu hỏi đang chờ mình giải.
     * Có thể filter theo examCode và subjectCode.
     */
    public Page<JobStatusResponse> getPendingJobs(
            String examCode, String subjectCode, Pageable pageable) {
        return questionJobRepository
                .findWaitingForHuman(examCode, subjectCode, pageable)
                .map(this::toResponse);
    }

    /**
     * Giáo viên "nhận" một câu hỏi — gán assignedTo để tránh 2 người giải cùng lúc.
     * Trả về chi tiết câu hỏi để giáo viên đọc và nhập đáp án.
     */
    @Transactional
    public JobStatusResponse claimJob(Long jobId, String teacherEmail) {
        QuestionJob job = getWaitingJob(jobId);

        // Nếu đã có người nhận, chỉ người đó mới được tiếp tục
        if (job.getAssignedTo() != null && !job.getAssignedTo().equals(teacherEmail)) {
            throw new BusinessException("Câu hỏi này đang được giải bởi " + job.getAssignedTo());
        }

        job.setAssignedTo(teacherEmail);
        questionJobRepository.save(job);
        log.info("Job [{}] claimed by [{}]", jobId, teacherEmail);
        return toResponse(job);
    }

    /**
     * Giáo viên submit đáp án thủ công.
     * Lưu vào QuestionBank để các lần sau tìm thấy, cập nhật job DONE.
     */
    @Transactional
    public JobStatusResponse submitAnswer(Long jobId, String teacherEmail,
                                          HumanAnswerRequest req) {
        QuestionJob job = getWaitingJob(jobId);

        // Chỉ người đã nhận mới được submit (hoặc chưa có ai nhận thì ai cũng được)
        if (job.getAssignedTo() != null && !job.getAssignedTo().equals(teacherEmail)) {
            throw new BusinessException("Bạn không có quyền submit đáp án cho câu hỏi này.");
        }

        long start = System.currentTimeMillis();

        // Lưu vào question_bank (để tra cứu sau — kể cả cho AI mode)
        try {
            List<SolveRequest.OptionData> options = objectMapper.readValue(
                    job.getOptionsJson(), new TypeReference<>() {});

            SolveRequest fakeRequest = buildFakeRequest(job, options);
            questionBankService.saveIfAbsent(fakeRequest, req.getAnswer(), null);
        } catch (Exception e) {
            log.warn("Failed to save human answer to bank for job [{}]: {}", jobId, e.getMessage());
        }

        // Cập nhật job DONE
        long elapsed = System.currentTimeMillis() - start;
        job.setStatus(QuestionJob.JobStatus.DONE);
        job.setAnswer(req.getAnswer());
        job.setAnswerSource(QuestionRecord.AnswerSource.HUMAN);
        job.setAssignedTo(teacherEmail);
        job.setProcessingFinishedAt(LocalDateTime.now());
        job.setProcessingTimeMs(elapsed);
        questionJobRepository.save(job);

        log.info("Job [{}] answered by human [{}] → [{}]", jobId, teacherEmail, req.getAnswer());
        return toResponse(job);
    }

    /**
     * Giáo viên "trả lại" câu hỏi — bỏ assignedTo, cho người khác nhận.
     */
    @Transactional
    public void releaseJob(Long jobId, String teacherEmail) {
        QuestionJob job = getWaitingJob(jobId);
        if (!teacherEmail.equals(job.getAssignedTo())) {
            throw new BusinessException("Bạn không sở hữu câu hỏi này.");
        }
        job.setAssignedTo(null);
        questionJobRepository.save(job);
        log.info("Job [{}] released by [{}]", jobId, teacherEmail);
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private QuestionJob getWaitingJob(Long jobId) {
        QuestionJob job = questionJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job không tồn tại: " + jobId));
        if (job.getStatus() != QuestionJob.JobStatus.WAITING_HUMAN) {
            throw new BusinessException(
                    "Job [" + jobId + "] không ở trạng thái chờ giải (status: " + job.getStatus() + ")");
        }
        return job;
    }

    private SolveRequest buildFakeRequest(QuestionJob job,
                                          List<SolveRequest.OptionData> options) {
        SolveRequest.SessionInfo session = new SolveRequest.SessionInfo();
        session.setEmail(job.getCustomerEmail());
        session.setExamCode(job.getExamCode());
        session.setSubjectCode(job.getSubjectCode());
        session.setDeviceId(job.getDeviceId() != null ? job.getDeviceId() : "human");

        SolveRequest.QuestionData qd = new SolveRequest.QuestionData();
        qd.setNumber(job.getQuestionNumber());
        qd.setQuestionType(job.getQuestionType().name());
        qd.setText(job.getQuestionText());
        qd.setOptions(options);

        SolveRequest req = new SolveRequest();
        req.setQuestionId(job.getQuestionId());
        req.setSession(session);
        req.setQuestion(qd);
        req.setCapturedAt(LocalDateTime.now().toString());
        return req;
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