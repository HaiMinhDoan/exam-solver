package com.examsolver.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Kafka message được publish sau khi lưu QuestionJob vào DB.
 *
 * Chỉ chứa jobId — executor sẽ load đầy đủ dữ liệu từ DB.
 * Tránh gửi payload lớn (ảnh base64...) qua Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSubmittedEvent implements Serializable {

    /** ID của QuestionJob trong PostgreSQL. */
    private Long jobId;

    /** Dùng để log / trace, không dùng để xử lý. */
    private String questionId;
    private String customerEmail;
    private String questionType;
    private String subjectCode;

    /** Timestamp client gửi lên, ISO string. */
    private String submittedAt;
}