package com.examsolver.kafka;

import com.examsolver.entity.QuestionJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, QuestionSubmittedEvent> kafkaTemplate;

    @Value("${kafka.topics.question-submitted:question.submitted}")
    private String topic;

    /**
     * Publish event sau khi QuestionJob đã được commit vào DB.
     * Key = customerEmail để các câu hỏi cùng customer vào cùng partition
     * → giữ thứ tự xử lý theo customer.
     */
    public void publishQuestionSubmitted(QuestionJob job) {
        QuestionSubmittedEvent event = QuestionSubmittedEvent.builder()
                .jobId(job.getId())
                .questionId(job.getQuestionId())
                .customerEmail(job.getCustomerEmail())
                .questionType(job.getQuestionType().name())
                .subjectCode(job.getSubjectCode())
                .submittedAt(LocalDateTime.now().toString())
                .build();

        CompletableFuture<SendResult<String, QuestionSubmittedEvent>> future =
                kafkaTemplate.send(topic, job.getCustomerEmail(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event for jobId=[{}]: {}", job.getId(), ex.getMessage());
                // Job vẫn PENDING trong DB — retry scheduler sẽ re-queue
            } else {
                log.debug("Published jobId=[{}] to partition [{}] offset [{}]",
                        job.getId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}