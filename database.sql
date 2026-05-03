-- ============================================================
-- EXAM SOLVER DATABASE - Full Schema
-- PostgreSQL
-- ============================================================

-- 1. CUSTOMERS
-- ============================================================
CREATE TABLE customers (
                           id               BIGSERIAL PRIMARY KEY,
                           email            VARCHAR(255) NOT NULL UNIQUE,
                           phone_number     VARCHAR(20) UNIQUE,
                           full_name        VARCHAR(255),
                           password_hash    VARCHAR(255) NOT NULL,
                           role             VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER'
                               CHECK (role IN ('CUSTOMER', 'ADMIN')),
                           is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
                           created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
                           updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_email    ON customers (email);
CREATE INDEX idx_customers_phone    ON customers (phone_number);
CREATE INDEX idx_customers_role     ON customers (role);

COMMENT ON TABLE  customers              IS 'Khách hàng / giáo viên sử dụng hệ thống';
COMMENT ON COLUMN customers.role        IS 'CUSTOMER = giáo viên, ADMIN = quản trị viên';
COMMENT ON COLUMN customers.is_active   IS 'FALSE = tài khoản bị khoá';


-- 2. API_KEYS
-- ============================================================
CREATE TABLE api_keys (
                          id              BIGSERIAL PRIMARY KEY,
                          key_value       VARCHAR(128) NOT NULL UNIQUE,
                          customer_id     BIGINT       NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
                          expires_at      TIMESTAMP    NOT NULL,
                          is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
                          validity_days   INT          NOT NULL DEFAULT 30,
                          description     TEXT,
                          usage_count     BIGINT       NOT NULL DEFAULT 0,
                          created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
                          last_used_at    TIMESTAMP
);

CREATE INDEX idx_api_keys_key_value   ON api_keys (key_value);
CREATE INDEX idx_api_keys_customer    ON api_keys (customer_id);
CREATE INDEX idx_api_keys_active      ON api_keys (is_active, expires_at);

COMMENT ON TABLE  api_keys                  IS 'API key cấp cho từng khách hàng, mỗi khách chỉ có 1 key active';
COMMENT ON COLUMN api_keys.key_value       IS 'Giá trị key dạng esk_<base64>, dài 128 ký tự';
COMMENT ON COLUMN api_keys.validity_days   IS 'Số ngày hiệu lực do admin cài đặt khi cấp key';
COMMENT ON COLUMN api_keys.usage_count     IS 'Số lần key được sử dụng để gọi /api/solve';
COMMENT ON COLUMN api_keys.last_used_at    IS 'Lần cuối key được dùng';


-- 3. EXAM_SESSIONS
-- ============================================================
CREATE TABLE exam_sessions (
                               id            BIGSERIAL PRIMARY KEY,
                               customer_id   BIGINT       NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
                               exam_code     VARCHAR(100) NOT NULL,
                               subject_code  VARCHAR(50)  NOT NULL,
                               device_id     VARCHAR(255),
                               created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_exam_sessions_customer   ON exam_sessions (customer_id);
CREATE INDEX idx_exam_sessions_lookup     ON exam_sessions (customer_id, exam_code, subject_code, device_id);

COMMENT ON TABLE  exam_sessions               IS 'Phiên thi — nhóm các câu hỏi trong cùng kỳ thi / môn học';
COMMENT ON COLUMN exam_sessions.exam_code    IS 'Mã kỳ thi, VD: HADA12123, Exam_FU_DN';
COMMENT ON COLUMN exam_sessions.subject_code IS 'Mã môn học, VD: MLN131, VNR202, SWD392';
COMMENT ON COLUMN exam_sessions.device_id    IS 'Machine ID của thiết bị client';


-- 4. QUESTION_RECORDS
-- ============================================================
CREATE TABLE question_records (
                                  id                  BIGSERIAL    PRIMARY KEY,
                                  exam_session_id     BIGINT       NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
                                  question_id         VARCHAR(256) NOT NULL,
                                  question_number     VARCHAR(20),
                                  question_type       VARCHAR(20)  NOT NULL
                                      CHECK (question_type IN ('SINGLECHOICE','MULTIPLECHOICE','TRUEFALSE','ESSAY')),
                                  question_text       TEXT         NOT NULL,
                                  options             JSONB,                        -- [{"label":"A","text":"..."},...]
                                  answer              VARCHAR(512),
                                  auto_click          BOOLEAN      NOT NULL DEFAULT TRUE,
                                  ai_model_used       VARCHAR(100),
                                  processing_time_ms  BIGINT,
                                  captured_at         TIMESTAMP,
                                  has_screenshot      BOOLEAN      NOT NULL DEFAULT FALSE,
                                  success             BOOLEAN      NOT NULL DEFAULT FALSE,
                                  error_message       TEXT,
                                  created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qr_question_id      ON question_records (question_id);
CREATE INDEX idx_qr_session          ON question_records (exam_session_id);
CREATE INDEX idx_qr_success_cache    ON question_records (question_id, success);
CREATE INDEX idx_qr_type             ON question_records (question_type);
CREATE INDEX idx_qr_options_gin      ON question_records USING GIN (options);  -- tìm kiếm nhanh trong JSONB

COMMENT ON TABLE  question_records                    IS 'Lưu toàn bộ câu hỏi và đáp án AI trả về';
COMMENT ON COLUMN question_records.question_id       IS 'Hash ID duy nhất của câu hỏi, dùng để cache';
COMMENT ON COLUMN question_records.options           IS 'JSONB: [{"label":"A","text":"Đáp án A"},...]';
COMMENT ON COLUMN question_records.answer            IS 'SINGLECHOICE: "A" | MULTIPLECHOICE: "A,C" | ESSAY: text dài';
COMMENT ON COLUMN question_records.has_screenshot    IS 'TRUE nếu client gửi kèm ảnh screenshot câu hỏi';
COMMENT ON COLUMN question_records.success           IS 'FALSE nếu AI gặp lỗi khi giải';
COMMENT ON COLUMN question_records.processing_time_ms IS 'Thời gian xử lý toàn bộ request (ms)';


-- ============================================================
-- AUTO-UPDATE updated_at cho bảng customers
-- ============================================================
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$;

CREATE TRIGGER trg_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();


-- ============================================================
-- SAMPLE DATA (tuỳ chọn, dùng để test)
-- ============================================================

-- Admin mặc định (password: Admin@12345 — BCrypt hash)
INSERT INTO customers (email, full_name, password_hash, role)
VALUES ('admin@examsolver.vn', 'System Admin',
        '$2a$10$7QJ8z1Z2k3L4m5N6o7P8q.Rexamplehashfordemopurposesonly1',
        'ADMIN')
    ON CONFLICT (email) DO NOTHING;