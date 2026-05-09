package com.examsolver.util;

import com.examsolver.entity.QuestionJob;
import com.examsolver.kafka.KafkaProducerService;
import com.examsolver.repository.QuestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler tìm các job bị stuck và re-queue vào Kafka.
 *
 * Trường hợp cần re-queue:
 *   - PENDING quá lâu (Kafka publish fail hoặc executor chưa nhận).
 *   - PROCESSING quá lâu (executor crash giữa chừng).
 *
 * Thời gian "quá lâu" cấu hình qua application.yml.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobRetryScheduler {

    private final QuestionJobRepository questionJobRepository;
    private final KafkaProducerService kafkaProducerService;

    @Value("${job.retry.pending-timeout-minutes:10}")
    private int pendingTimeoutMinutes;

    @Value("${job.retry.processing-timeout-minutes:5}")
    private int processingTimeoutMinutes;

    /** Chạy mỗi 2 phút kiểm tra stuck PENDING jobs. */
    @Scheduled(fixedDelayString = "${job.retry.check-interval-ms:120000}")
    @Transactional
    public void requeueStuckJobs() {
        requeueStuckPending();
        requeueStuckProcessing();
    }

    private void requeueStuckPending() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        List<QuestionJob> stuck = questionJobRepository.findStuckPendingJobs(cutoff);

        if (!stuck.isEmpty()) {
            log.warn("Found {} stuck PENDING jobs (older than {}min), re-queuing...",
                    stuck.size(), pendingTimeoutMinutes);
            stuck.forEach(job -> {
                kafkaProducerService.publishQuestionSubmitted(job);
                log.info("Re-queued stuck PENDING jobId=[{}]", job.getId());
            });
        }
    }

    private void requeueStuckProcessing() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(processingTimeoutMinutes);
        List<QuestionJob> stuck = questionJobRepository.findStuckProcessingJobs(cutoff);

        if (!stuck.isEmpty()) {
            log.warn("Found {} stuck PROCESSING jobs (started >{}min ago), resetting...",
                    stuck.size(), processingTimeoutMinutes);
            stuck.forEach(job -> {
                questionJobRepository.resetToPending(job.getId());
                kafkaProducerService.publishQuestionSubmitted(job);
                log.info("Reset and re-queued stuck PROCESSING jobId=[{}]", job.getId());
            });
        }
    }
}