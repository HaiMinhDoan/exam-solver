package com.examsolver.config;

import com.examsolver.kafka.QuestionSubmittedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id:exam-executor}")
    private String groupId;

    @Value("${kafka.topics.question-submitted:question.submitted}")
    private String questionSubmittedTopic;

    @Value("${kafka.topics.partitions:6}")
    private int partitions;

    @Value("${kafka.topics.replication-factor:1}")
    private short replicationFactor;

    // ─── Topic ───────────────────────────────────────────────────────────────

    @Bean
    public NewTopic questionSubmittedTopic() {
        return TopicBuilder.name(questionSubmittedTopic)
                .partitions(partitions)          // 6 partitions = 6 concurrent consumers
                .replicas(replicationFactor)
                .build();
    }

    // ─── Producer ────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, QuestionSubmittedEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Đảm bảo message được ghi xuống tất cả replicas trước khi ACK
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        // Retry tự động nếu Kafka tạm không available
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        // Đảm bảo thứ tự trong partition
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, QuestionSubmittedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ─── Consumer ────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, QuestionSubmittedEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Manual ACK — chỉ commit offset sau khi xử lý xong
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Mỗi lần poll tối đa 5 records — tránh gọi AI batch quá nhiều cùng lúc
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 5);
        // Thời gian tối đa giữa 2 lần poll (đủ để gọi AI + lưu DB)
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5 phút
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.examsolver.kafka");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, QuestionSubmittedEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, QuestionSubmittedEvent>
    kafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, QuestionSubmittedEvent>();
        factory.setConsumerFactory(consumerFactory());
        // Manual ACK mode: Acknowledgment.acknowledge() sau khi xử lý
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // Số luồng consumer song song (≤ số partitions)
        factory.setConcurrency(3);
        return factory;
    }
}