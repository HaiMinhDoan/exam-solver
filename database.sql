-- ============================================================
-- EXAM SOLVER DATABASE SCHEMA
-- PostgreSQL
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- 1. customers
-- ============================================================
CREATE TABLE customers (
                           id                 BIGSERIAL PRIMARY KEY,
                           email              VARCHAR(255) NOT NULL UNIQUE,
                           phone_number       VARCHAR(50)  UNIQUE,
                           full_name          VARCHAR(255),
                           password_hash      VARCHAR(255) NOT NULL,
                           role               VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER'
                               CHECK (role IN ('CUSTOMER', 'ADMIN')),
                           access_expires_at  TIMESTAMP,
                           ai_mode_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
                           is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
                           created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
                           updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 2. exam_sessions
-- ============================================================
CREATE TABLE exam_sessions (
                               id           BIGSERIAL PRIMARY KEY,
                               customer_id  BIGINT       NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
                               exam_code    VARCHAR(100) NOT NULL,
                               subject_code VARCHAR(50)  NOT NULL,
                               device_id    VARCHAR(255),
                               created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_es_customer ON exam_sessions(customer_id);
CREATE INDEX idx_es_lookup   ON exam_sessions(customer_id, exam_code, subject_code, device_id);

-- ============================================================
-- 3. question_bank
-- ============================================================
CREATE TABLE question_bank (
                               id                BIGSERIAL PRIMARY KEY,
                               question_hash     VARCHAR(64)  NOT NULL UNIQUE,
                               normalized_text   TEXT         NOT NULL,
                               original_text     TEXT         NOT NULL,
                               question_type     VARCHAR(20)  NOT NULL
                                   CHECK (question_type IN ('SINGLECHOICE', 'MULTIPLECHOICE', 'TRUEFALSE', 'ESSAY')),
                               options           JSONB,
                               answer            TEXT         NOT NULL,
                               subject_code      VARCHAR(50),
                               hit_count         BIGINT       NOT NULL DEFAULT 0,
                               is_verified       BOOLEAN      NOT NULL DEFAULT FALSE,
                               prompt_version_id BIGINT,
                               created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
                               updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qb_hash    ON question_bank(question_hash);
CREATE INDEX idx_qb_type    ON question_bank(question_type);
CREATE INDEX idx_qb_subject ON question_bank(subject_code);
CREATE INDEX idx_qb_normalized_trgm ON question_bank USING GIN (normalized_text gin_trgm_ops);

-- ============================================================
-- 4. prompt_versions
-- ============================================================
CREATE TABLE prompt_versions (
                                 id               BIGSERIAL PRIMARY KEY,
                                 prompt_type      VARCHAR(30)  NOT NULL,
                                 version_number   INT          NOT NULL,
                                 version_label    VARCHAR(100),
                                 prompt_template  TEXT         NOT NULL,
                                 is_active        BOOLEAN      NOT NULL DEFAULT FALSE,
                                 created_by       VARCHAR(255),
                                 notes            TEXT,
                                 created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
                                 activated_at     TIMESTAMP
);

CREATE INDEX idx_pv_active  ON prompt_versions(prompt_type, is_active);
CREATE INDEX idx_pv_version ON prompt_versions(version_number);
CREATE UNIQUE INDEX idx_pv_unique_active ON prompt_versions(prompt_type) WHERE is_active = TRUE;

-- ============================================================
-- 5. question_records
-- ============================================================
CREATE TABLE question_records (
                                  id                 BIGSERIAL PRIMARY KEY,
                                  exam_session_id    BIGINT       NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
                                  question_bank_id   BIGINT       REFERENCES question_bank(id) ON DELETE SET NULL,
                                  question_hash      VARCHAR(64),
                                  question_number    VARCHAR(20),
                                  question_type      VARCHAR(20)  NOT NULL
                                      CHECK (question_type IN ('SINGLECHOICE', 'MULTIPLECHOICE', 'TRUEFALSE', 'ESSAY')),
                                  question_text      TEXT         NOT NULL,
                                  options            JSONB,
                                  answer             VARCHAR(512),
                                  auto_click         BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Thêm 'HUMAN' theo AnswerSource enum
                                  answer_source      VARCHAR(10)
                                      CHECK (answer_source IN ('BANK', 'AI', 'NONE', 'HUMAN')),
                                  ai_model_used      VARCHAR(100),
                                  prompt_version_id  BIGINT,
                                  processing_time_ms BIGINT,
                                  captured_at        TIMESTAMP,
                                  has_screenshot     BOOLEAN      NOT NULL DEFAULT FALSE,
                                  success            BOOLEAN      NOT NULL DEFAULT FALSE,
                                  error_message      TEXT,
                                  created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qr_session  ON question_records(exam_session_id);
CREATE INDEX idx_qr_hash     ON question_records(question_hash);
CREATE INDEX idx_qr_bank_ref ON question_records(question_bank_id);

-- ============================================================
-- 6. question_jobs
-- ============================================================
CREATE TABLE question_jobs (
                               id                     BIGSERIAL PRIMARY KEY,
                               question_id            VARCHAR(256) NOT NULL,
                               question_hash          VARCHAR(64),
                               customer_email         VARCHAR(255) NOT NULL,
                               exam_code              VARCHAR(100) NOT NULL,
                               subject_code           VARCHAR(50)  NOT NULL,
                               device_id              VARCHAR(255),
                               question_number        VARCHAR(20),
                               question_type          VARCHAR(20)  NOT NULL
                                   CHECK (question_type IN ('SINGLECHOICE', 'MULTIPLECHOICE', 'TRUEFALSE', 'ESSAY')),
                               question_text          TEXT         NOT NULL,
                               options                JSONB,
                               screenshot_base64      TEXT,
                               captured_at            TIMESTAMP,
    -- Thêm 'WAITING_HUMAN' theo JobStatus enum
                               status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                   CHECK (status IN ('WAITING_HUMAN', 'PENDING', 'PROCESSING', 'DONE', 'FAILED', 'SKIPPED')),
                               resolver_type          VARCHAR(20)  NOT NULL DEFAULT 'HUMAN'
                                   CHECK (resolver_type IN ('AI', 'HUMAN')),
                               answer                 TEXT,
    -- Thêm 'HUMAN' theo AnswerSource enum (dùng chung với QuestionRecord)
                               answer_source          VARCHAR(10)
                                   CHECK (answer_source IN ('BANK', 'AI', 'NONE', 'HUMAN')),
                               error_message          TEXT,
                               retry_count            INT          NOT NULL DEFAULT 0,
                               max_retries            INT          NOT NULL DEFAULT 3,
                               processing_started_at  TIMESTAMP,
                               processing_finished_at TIMESTAMP,
                               processing_time_ms     BIGINT,
                               created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
                               updated_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qj_status      ON question_jobs(status);
CREATE INDEX idx_qj_customer    ON question_jobs(customer_email);
CREATE INDEX idx_qj_question_id ON question_jobs(question_id);
CREATE INDEX idx_qj_created     ON question_jobs(created_at);

-- ============================================================
-- Auto-update updated_at trigger
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_question_bank_updated_at
    BEFORE UPDATE ON question_bank
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_question_jobs_updated_at
    BEFORE UPDATE ON question_jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();







-- ============================================================
-- SAMPLE DATA: EXAM SESSIONS + QUESTION RECORDS + QUESTION JOBS
-- Customer ID = 2 | Chưa có đáp án
-- ============================================================

-- ============================================================
-- 1. EXAM SESSIONS
-- ============================================================
INSERT INTO exam_sessions (customer_id, exam_code, subject_code, device_id, created_at)
VALUES
    (2, 'HADA_SP25_001', 'MLN131', 'DEVICE-A1B2C3D4', NOW() - INTERVAL '3 hours'),
    (2, 'HADA_SP25_002', 'SWD392', 'DEVICE-A1B2C3D4', NOW() - INTERVAL '2 hours'),
    (2, 'Exam_FU_DN_05',  'VNR202', 'DEVICE-A1B2C3D4', NOW() - INTERVAL '1 hour');


-- ============================================================
-- 2. QUESTION RECORDS (answer = NULL, success = FALSE)
-- ============================================================
INSERT INTO question_records (
    exam_session_id,
    question_bank_id,
    question_hash,
    question_number,
    question_type,
    question_text,
    options,
    answer,
    auto_click,
    answer_source,
    ai_model_used,
    prompt_version_id,
    processing_time_ms,
    captured_at,
    has_screenshot,
    success,
    error_message,
    created_at
)
VALUES
-- ──────────────────────────────────────────
-- Session 1: MLN131 — Triết học Mác-Lênin
-- ──────────────────────────────────────────
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'MLN131' LIMIT 1),
    NULL, 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
    '1', 'SINGLECHOICE',
    'Theo quan điểm của chủ nghĩa duy vật biện chứng, vật chất là gì?',
    '[
      {"label":"A","text":"Vật chất là những vật thể hữu hình mà con người có thể cầm nắm được"},
      {"label":"B","text":"Vật chất là phạm trù triết học dùng để chỉ thực tại khách quan, tồn tại độc lập với ý thức"},
      {"label":"C","text":"Vật chất là toàn bộ thế giới tự nhiên bao gồm đất, nước, lửa và không khí"},
      {"label":"D","text":"Vật chất là khái niệm do con người tạo ra để mô tả thế giới xung quanh"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '3 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '3 hours'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'MLN131' LIMIT 1),
    NULL, 'b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3',
    '2', 'SINGLECHOICE',
    'Quy luật nào sau đây là quy luật cơ bản nhất của phép biện chứng duy vật?',
    '[
      {"label":"A","text":"Quy luật lượng — chất"},
      {"label":"B","text":"Quy luật phủ định của phủ định"},
      {"label":"C","text":"Quy luật mâu thuẫn (thống nhất và đấu tranh của các mặt đối lập)"},
      {"label":"D","text":"Quy luật nhân quả"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '3 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '3 hours'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'MLN131' LIMIT 1),
    NULL, 'c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4',
    '3', 'TRUEFALSE',
    'Ý thức có thể tác động trở lại vật chất và thúc đẩy hoặc kìm hãm sự phát triển của vật chất.',
    '[
      {"label":"A","text":"Đúng"},
      {"label":"B","text":"Sai"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '3 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '3 hours'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'MLN131' LIMIT 1),
    NULL, 'd4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5',
    '4', 'MULTIPLECHOICE',
    'Những luận điểm nào sau đây phản ánh đúng nguyên lý về sự phát triển?',
    '[
      {"label":"A","text":"Phát triển là quá trình vận động từ thấp đến cao, từ đơn giản đến phức tạp"},
      {"label":"B","text":"Phát triển diễn ra theo đường thẳng, không có bước thụt lùi"},
      {"label":"C","text":"Phát triển mang tính khách quan, không phụ thuộc vào ý chí con người"},
      {"label":"D","text":"Phát triển gắn liền với sự ra đời của cái mới thay thế cái cũ"},
      {"label":"E","text":"Phát triển chỉ xảy ra trong giới tự nhiên, không xảy ra trong xã hội"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '3 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '3 hours'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'MLN131' LIMIT 1),
    NULL, 'e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6',
    '5', 'ESSAY',
    'Phân tích mối quan hệ biện chứng giữa lực lượng sản xuất và quan hệ sản xuất. Liên hệ với thực tiễn phát triển kinh tế ở Việt Nam hiện nay.',
    NULL,
    NULL, FALSE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '3 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '3 hours'
),

-- ──────────────────────────────────────────
-- Session 2: SWD392 — Software Design
-- ──────────────────────────────────────────
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'SWD392' LIMIT 1),
    NULL, 'f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1',
    '1', 'SINGLECHOICE',
    'Which design pattern provides a simplified interface to a complex subsystem?',
    '[
      {"label":"A","text":"Adapter"},
      {"label":"B","text":"Facade"},
      {"label":"C","text":"Decorator"},
      {"label":"D","text":"Proxy"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '2 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '2 hours'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'SWD392' LIMIT 1),
    NULL, 'a1f6b2e5c3d4a1f6b2e5c3d4a1f6b2e5c3d4a1f6b2e5c3d4a1f6b2e5c3d4a1f6',
    '2', 'SINGLECHOICE',
    'In the MVC pattern, which component is responsible for handling user input and updating the Model?',
    '[
      {"label":"A","text":"Model"},
      {"label":"B","text":"View"},
      {"label":"C","text":"Controller"},
      {"label":"D","text":"Repository"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '2 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '2 hours'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'SWD392' LIMIT 1),
    NULL, 'b2a1c3f6d4e5b2a1c3f6d4e5b2a1c3f6d4e5b2a1c3f6d4e5b2a1c3f6d4e5b2a1',
    '3', 'MULTIPLECHOICE',
    'Which of the following are creational design patterns? (Select all that apply)',
    '[
      {"label":"A","text":"Singleton"},
      {"label":"B","text":"Observer"},
      {"label":"C","text":"Factory Method"},
      {"label":"D","text":"Strategy"},
      {"label":"E","text":"Abstract Factory"},
      {"label":"F","text":"Builder"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '2 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '2 hours'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'SWD392' LIMIT 1),
    NULL, 'c3b2d4a1e5f6c3b2d4a1e5f6c3b2d4a1e5f6c3b2d4a1e5f6c3b2d4a1e5f6c3b2',
    '4', 'TRUEFALSE',
    'The Open/Closed Principle states that a class should be open for extension but closed for modification.',
    '[
      {"label":"A","text":"True"},
      {"label":"B","text":"False"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '2 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '2 hours'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'SWD392' LIMIT 1),
    NULL, 'd4c3e5b2f6a1d4c3e5b2f6a1d4c3e5b2f6a1d4c3e5b2f6a1d4c3e5b2f6a1d4c3',
    '5', 'SINGLECHOICE',
    'Which SOLID principle is violated when a class has more than one reason to change?',
    '[
      {"label":"A","text":"Open/Closed Principle"},
      {"label":"B","text":"Single Responsibility Principle"},
      {"label":"C","text":"Liskov Substitution Principle"},
      {"label":"D","text":"Dependency Inversion Principle"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '2 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '2 hours'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'SWD392' LIMIT 1),
    NULL, 'e5d4f6c3a1b2e5d4f6c3a1b2e5d4f6c3a1b2e5d4f6c3a1b2e5d4f6c3a1b2e5d4',
    '6', 'ESSAY',
    'Explain the difference between composition and inheritance. When would you prefer one over the other? Provide code examples to support your answer.',
    NULL,
    NULL, FALSE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '2 hours', FALSE, FALSE, NULL, NOW() - INTERVAL '2 hours'
),

-- ──────────────────────────────────────────
-- Session 3: VNR202 — Văn hóa Việt Nam
-- ──────────────────────────────────────────
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'VNR202' LIMIT 1),
    NULL, 'f6e5a1d4b2c3f6e5a1d4b2c3f6e5a1d4b2c3f6e5a1d4b2c3f6e5a1d4b2c3f6e5',
    '1', 'SINGLECHOICE',
    'Trống đồng Đông Sơn là biểu tượng tiêu biểu của nền văn hóa nào?',
    '[
      {"label":"A","text":"Văn hóa Sa Huỳnh"},
      {"label":"B","text":"Văn hóa Đông Sơn"},
      {"label":"C","text":"Văn hóa Óc Eo"},
      {"label":"D","text":"Văn hóa Đồng Nai"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '1 hour', FALSE, FALSE, NULL, NOW() - INTERVAL '1 hour'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'VNR202' LIMIT 1),
    NULL, 'a1e5b2f6c3d4a1e5b2f6c3d4a1e5b2f6c3d4a1e5b2f6c3d4a1e5b2f6c3d4a1e5',
    '2', 'SINGLECHOICE',
    'Hệ thống chữ viết nào được sử dụng chính thức tại Việt Nam hiện nay?',
    '[
      {"label":"A","text":"Chữ Nôm"},
      {"label":"B","text":"Chữ Hán"},
      {"label":"C","text":"Chữ Quốc ngữ (Latin hóa)"},
      {"label":"D","text":"Chữ Phạn"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '1 hour', FALSE, FALSE, NULL, NOW() - INTERVAL '1 hour'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'VNR202' LIMIT 1),
    NULL, 'b2f6c3a1d4e5b2f6c3a1d4e5b2f6c3a1d4e5b2f6c3a1d4e5b2f6c3a1d4e5b2f6',
    '3', 'MULTIPLECHOICE',
    'Những yếu tố nào sau đây thuộc về văn hóa vật chất của người Việt?',
    '[
      {"label":"A","text":"Nhà ở truyền thống (nhà sàn, nhà rường)"},
      {"label":"B","text":"Tín ngưỡng thờ cúng tổ tiên"},
      {"label":"C","text":"Trang phục áo dài"},
      {"label":"D","text":"Ẩm thực (phở, bánh mì, cơm tấm)"},
      {"label":"E","text":"Lễ hội Tết Nguyên Đán"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '1 hour', FALSE, FALSE, NULL, NOW() - INTERVAL '1 hour'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'VNR202' LIMIT 1),
    NULL, 'c3a1d4b2e5f6c3a1d4b2e5f6c3a1d4b2e5f6c3a1d4b2e5f6c3a1d4b2e5f6c3a1',
    '4', 'TRUEFALSE',
    'Quan họ Bắc Ninh đã được UNESCO công nhận là Di sản văn hóa phi vật thể đại diện của nhân loại.',
    '[
      {"label":"A","text":"Đúng"},
      {"label":"B","text":"Sai"}
    ]'::jsonb,
    NULL, TRUE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '1 hour', FALSE, FALSE, NULL, NOW() - INTERVAL '1 hour'
),
(
    (SELECT id FROM exam_sessions WHERE customer_id = 2 AND subject_code = 'VNR202' LIMIT 1),
    NULL, 'd4b2e5c3f6a1d4b2e5c3f6a1d4b2e5c3f6a1d4b2e5c3f6a1d4b2e5c3f6a1d4b2',
    '5', 'ESSAY',
    'Trình bày những đặc trưng cơ bản của văn hóa Việt Nam. Theo bạn, yếu tố nào có ảnh hưởng lớn nhất đến sự hình thành bản sắc văn hóa dân tộc Việt Nam? Giải thích.',
    NULL,
    NULL, FALSE, 'NONE', NULL, NULL, NULL,
    NOW() - INTERVAL '1 hour', FALSE, FALSE, NULL, NOW() - INTERVAL '1 hour'
);


-- ============================================================
-- 3. QUESTION JOBS (status = WAITING_HUMAN, resolver_type = HUMAN)
-- ============================================================
INSERT INTO question_jobs (
    question_id,
    question_hash,
    customer_email,
    exam_code,
    subject_code,
    device_id,
    question_number,
    question_type,
    question_text,
    options,
    screenshot_base64,
    captured_at,
    status,
    resolver_type,
    answer,
    answer_source,
    error_message,
    retry_count,
    max_retries,
    created_at
)
SELECT
    'qjob_' || es.subject_code || '_' || qr.question_number AS question_id,
    qr.question_hash,
    (SELECT email FROM customers WHERE id = 2),
    es.exam_code,
    es.subject_code,
    es.device_id,
    qr.question_number,
    qr.question_type,
    qr.question_text,
    qr.options,
    NULL,               -- screenshot_base64
    qr.captured_at,
    'WAITING_HUMAN',    -- status: chờ human giải (đúng với resolver_type = HUMAN)
    'HUMAN',            -- resolver_type
    NULL,               -- answer chưa có
    'NONE',
    NULL,
    0,
    3,
    qr.created_at
FROM question_records qr
         JOIN exam_sessions es ON es.id = qr.exam_session_id
WHERE es.customer_id = 2;