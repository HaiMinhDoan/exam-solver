package com.examsolver.service.impl;

import com.examsolver.dto.request.HumanAnswerRequest;
import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.ExamSessionResponse;
import com.examsolver.dto.response.JobStatusResponse;
import com.examsolver.entity.QuestionJob;
import com.examsolver.entity.QuestionRecord;
import com.examsolver.exception.BusinessException;
import com.examsolver.exception.ResourceNotFoundException;
import com.examsolver.repository.ExamSessionRepository;
import com.examsolver.repository.QuestionJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class HumanSolveService {

    private final QuestionJobRepository questionJobRepository;
    private final ExamSessionRepository examSessionRepository;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;

    /**
     * Lấy danh sách bài thi (exam sessions) có câu hỏi chờ giáo viên giải (HUMAN resolver).
     */
    public Page<ExamSessionResponse> getHumanSessions(Long customerId, Pageable pageable) {
//        return examSessionRepository.findHumanSessions(customerId, pageable)
        return examSessionRepository.findAllOfHumanSessions(customerId, pageable)
                .map(session -> {
                    long count = questionJobRepository.countSessionJobs(
                            session.getExamCode(),
                            session.getSubjectCode(),
                            session.getDeviceId()
                    );
                    return ExamSessionResponse.builder()
                            .id(session.getId())
                            .examCode(session.getExamCode())
                            .subjectCode(session.getSubjectCode())
                            .deviceId(session.getDeviceId())
                            .pendingCount((int) count)
                            .createdAt(session.getCreatedAt())
                            .build();
                });
    }

    /**
     * Lấy danh sách câu hỏi chờ giáo viên giải trong một exam session cụ thể.
     */
    @Transactional(readOnly = true)
    public Page<JobStatusResponse> getSessionJobs(
            Long customerId, Long sessionId, Pageable pageable) {

        var session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session không tồn tại: " + sessionId));

        if (!session.getCustomer().getId().equals(customerId)) {
            throw new BusinessException("Bạn không có quyền xem session này");
        }

        return questionJobRepository.findSessionJobs(
                session.getCustomer().getEmail(),
                session.getExamCode(),
                session.getSubjectCode(),
                session.getDeviceId(),
                pageable
        ).map(this::toResponseUnchecked); // dùng wrapper không throws
    }

    /**
     * Lấy chi tiết một câu hỏi đang chờ giải.
     */
    public JobStatusResponse getJob(Long jobId) {
        QuestionJob job = getWaitingJob(jobId);
        log.info("Retrieved job [{}]", jobId);
        return toResponseUnchecked(job);
    }

    /**
     * Nhận đáp án từ con người cho một câu hỏi.
     */
    @Transactional
    public JobStatusResponse submitAnswer(Long jobId, String teacherEmail,
                                          HumanAnswerRequest req) {
        QuestionJob job = getWaitingJob(jobId);

        long start = System.currentTimeMillis();

        // Lưu vào question_bank
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
        job.setProcessingFinishedAt(LocalDateTime.now());
        job.setProcessingTimeMs(elapsed);
        questionJobRepository.save(job);

        log.info("Job [{}] answered by human [{}] → [{}]", jobId, teacherEmail, req.getAnswer());
        return toResponseUnchecked(job);
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private QuestionJob getWaitingJob(Long jobId) {
        QuestionJob job = questionJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job không tồn tại: " + jobId));
//        if (job.getStatus() != QuestionJob.JobStatus.WAITING_HUMAN) {
//            throw new BusinessException(
//                    "Job [" + jobId + "] không ở trạng thái chờ giải (status: " + job.getStatus() + ")");
//        }
        return job;
    }

    /**
     * Wrapper không throws — dùng được trong .map() của Stream/Page.
     * JsonProcessingException được wrap thành RuntimeException để không làm vỡ functional interface.
     */
    private JobStatusResponse toResponseUnchecked(QuestionJob job) {
        try {
            return toResponse(job);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse options JSON for job [" + job.getId() + "]", e);
        }
    }

    private JobStatusResponse toResponse(QuestionJob job) throws Exception {
        List<SolveRequest.OptionData> options = null;

        // options có thể null với câu tự luận (ESSAY)
        if (job.getOptionsJson() != null) {
            options = objectMapper.readValue(job.getOptionsJson(), new TypeReference<>() {});
        }

        return JobStatusResponse.builder()
                .jobId(job.getId())
                .questionId(job.getQuestionId())
                .questionNumber(job.getQuestionNumber())
                .questionType(job.getQuestionType())
                .questionText(job.getQuestionText())
                .status(job.getStatus())
                .answer(job.getAnswer())
                .answerSource(job.getAnswerSource() != null ? job.getAnswerSource().name() : null)
                .autoClick(job.getStatus() == QuestionJob.JobStatus.DONE)
                .errorMessage(job.getErrorMessage())
                .options(options)
                .processingTimeMs(job.getProcessingTimeMs())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
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
}