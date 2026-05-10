-- ============================================================
-- EXAM SOLVER DATABASE SCHEMA
-- PostgreSQL
-- ============================================================

-- Extension cần thiết cho full-text search
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

-- GIN index cho full-text search (pg_trgm)
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

-- Đảm bảo chỉ 1 version active trên mỗi prompt_type
CREATE UNIQUE INDEX idx_pv_unique_active
    ON prompt_versions(prompt_type)
    WHERE is_active = TRUE;

-- ============================================================
-- 5. question_records
-- ============================================================
CREATE TABLE question_records (
                                  id                BIGSERIAL PRIMARY KEY,
                                  exam_session_id   BIGINT       NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
                                  question_bank_id  BIGINT       REFERENCES question_bank(id) ON DELETE SET NULL,
                                  question_hash     VARCHAR(64),
                                  question_number   VARCHAR(20),
                                  question_type     VARCHAR(20)  NOT NULL
                                      CHECK (question_type IN ('SINGLECHOICE', 'MULTIPLECHOICE', 'TRUEFALSE', 'ESSAY')),
                                  question_text     TEXT         NOT NULL,
                                  options           JSONB,
                                  answer            VARCHAR(512),
                                  auto_click        BOOLEAN      NOT NULL DEFAULT TRUE,
                                  answer_source     VARCHAR(10)
                                      CHECK (answer_source IN ('BANK', 'AI', 'NONE')),
                                  ai_model_used     VARCHAR(100),
                                  prompt_version_id BIGINT,
                                  processing_time_ms BIGINT,
                                  captured_at       TIMESTAMP,
                                  has_screenshot    BOOLEAN      NOT NULL DEFAULT FALSE,
                                  success           BOOLEAN      NOT NULL DEFAULT FALSE,
                                  error_message     TEXT,
                                  created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qr_session  ON question_records(exam_session_id);
CREATE INDEX idx_qr_hash     ON question_records(question_hash);
CREATE INDEX idx_qr_bank_ref ON question_records(question_bank_id);

-- ============================================================
-- 6. question_jobs
-- ============================================================
CREATE TABLE question_jobs (
                               id                      BIGSERIAL PRIMARY KEY,
                               question_id             VARCHAR(256) NOT NULL,
                               question_hash           VARCHAR(64),
                               customer_email          VARCHAR(255) NOT NULL,
                               exam_code               VARCHAR(100) NOT NULL,
                               subject_code            VARCHAR(50)  NOT NULL,
                               device_id               VARCHAR(255),
                               question_number         VARCHAR(20),
                               question_type           VARCHAR(20)  NOT NULL
                                   CHECK (question_type IN ('SINGLECHOICE', 'MULTIPLECHOICE', 'TRUEFALSE', 'ESSAY')),
                               question_text           TEXT         NOT NULL,
                               options                 JSONB,
                               screenshot_base64       TEXT,
                               captured_at             TIMESTAMP,
                               status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                   CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED', 'SKIPPED')),
                               answer                  TEXT,
                               answer_source           VARCHAR(10)
                                   CHECK (answer_source IN ('BANK', 'AI', 'NONE')),
                               error_message           TEXT,
                               retry_count             INT          NOT NULL DEFAULT 0,
                               max_retries             INT          NOT NULL DEFAULT 3,
                               processing_started_at   TIMESTAMP,
                               processing_finished_at  TIMESTAMP,
                               processing_time_ms      BIGINT,
                               created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
                               updated_at              TIMESTAMP    NOT NULL DEFAULT NOW()
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





