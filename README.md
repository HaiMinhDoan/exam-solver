# Exam Solver Backend

Java Spring Boot backend cho hệ thống giải câu hỏi tự động hỗ trợ giảng dạy.

## Kiến trúc tổng quan

```
Client (Rust App)
    │
    │  POST /api/solve  { api_key, question_id, session, question, ... }
    ▼
[SolveController]
    │
    ▼
[SolveServiceImpl]
    ├── Validate API Key (PostgreSQL)
    ├── Check Cache (question_records)
    ├── Get/Create ExamSession
    │
    ├── [AiSolverService] ◄── Interface
    │       └── [ClaudeAiSolverService] ◄── Implementation
    │               │  POST https://api.anthropic.com/v1/messages
    │               └── Prompt Engineering → JSON Response
    │
    └── Save QuestionRecord (PostgreSQL)
```

## Tech Stack

- **Java 21** + **Spring Boot 3.2**
- **PostgreSQL** (JPA/Hibernate, JSONB cho options)
- **Spring Security** + JWT authentication
- **WebFlux WebClient** để gọi Claude API (non-blocking)
- **Claude Sonnet** cho AI giải bài

## Cấu trúc project

```
src/main/java/com/examsolver/
├── controller/
│   ├── SolveController.java      ← POST /api/solve (endpoint chính)
│   ├── AuthController.java       ← POST /api/auth/register, /login
│   ├── AdminController.java      ← Admin quản lý API keys
│   └── CustomerController.java   ← Khách hàng xem key của mình
├── service/
│   ├── ai/
│   │   ├── AiSolverService.java          ← Interface
│   │   └── ClaudeAiSolverService.java    ← Claude AI implementation
│   └── impl/
│       ├── SolveServiceImpl.java         ← Business logic chính
│       ├── ApiKeyServiceImpl.java        ← Quản lý API keys
│       └── AuthServiceImpl.java         ← Authentication
├── entity/
│   ├── Customer.java         ← Khách hàng (email, phone, role)
│   ├── ApiKey.java           ← Key với thời hạn sử dụng
│   ├── ExamSession.java      ← Phiên thi (exam_code, subject_code)
│   └── QuestionRecord.java   ← Câu hỏi + đáp án (lưu DB)
├── security/
│   ├── JwtService.java       ← Tạo/xác thực JWT
│   └── JwtAuthFilter.java    ← Filter kiểm tra Bearer token
└── config/
    ├── SecurityConfig.java   ← Spring Security rules
    ├── WebClientConfig.java  ← WebClient cho Claude API
    └── DataInitializer.java  ← Tạo admin mặc định
```

## Setup

### 1. Tạo database PostgreSQL

```sql
CREATE DATABASE exam_solver_db;
```

### 2. Cấu hình environment variables

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_db_password
export CLAUDE_API_KEY=sk-ant-api03-...
export JWT_SECRET=your-long-random-secret-at-least-64-chars
```

Hoặc tạo file `application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/exam_solver_db
    username: postgres
    password: your_password
claude:
  api:
    key: sk-ant-api03-...
```

### 3. Build & Run

```bash
mvn clean install
mvn spring-boot:run
```

Server chạy tại: `http://localhost:8080`

## API Endpoints

### Authentication

```http
POST /api/auth/register
{
  "email": "teacher@school.edu.vn",
  "phoneNumber": "0901234567",
  "fullName": "Nguyễn Văn A",
  "password": "SecurePass123"
}

POST /api/auth/login
{
  "email": "teacher@school.edu.vn",
  "password": "SecurePass123"
}
→ { "data": { "token": "eyJ..." } }
```

### Admin - Tạo API Key cho khách hàng

```http
POST /api/admin/api-keys
Authorization: Bearer <admin-jwt-token>
{
  "customer_id": 2,
  "validity_days": 90,
  "description": "Gói 3 tháng - Thầy Nguyễn"
}
→ { "data": { "key_value": "esk_abc123...", "expires_at": "2026-07-28T..." } }
```

### Solve - Endpoint chính từ Rust client

```http
POST /api/solve
{
  "api_key": "esk_abc123...",          ← THÊM field này so với doc gốc
  "question_id": "hash123",
  "session": {
    "email": "teacher@school.edu.vn",
    "exam_code": "HADA12123",
    "subject_code": "MLN131",
    "device_id": "machine_id_123"
  },
  "question": {
    "number": "1",
    "question_type": "SINGLECHOICE",
    "text": "Chủ nghĩa Mác-Lênin là gì?",
    "options": [
      {"label": "A", "text": "Hệ thống quan điểm..."},
      {"label": "B", "text": "Học thuyết kinh tế..."},
      {"label": "C", "text": "Triết học duy tâm..."},
      {"label": "D", "text": "Phong trào công nhân..."}
    ]
  },
  "captured_at": "2026-04-27T05:30:00",
  "ai_answer": null
}

→ {
  "success": true,
  "message": "Answer processed",
  "question_id": "hash123",
  "answer": "A",
  "auto_click": true
}
```

## Logic API Key

- **1 khách hàng = 1 API key active** tại một thời điểm
- Admin tạo key với số ngày hiệu lực tùy chỉnh (ví dụ: 30, 60, 90 ngày)
- Khi tạo key mới, key cũ tự động bị revoke
- Key bao gồm: prefix `esk_` + 48 random bytes (URL-safe base64)
- Cleanup task chạy 2h sáng mỗi ngày để deactivate key hết hạn

## AI - Prompt Engineering

`ClaudeAiSolverService` build prompt theo từng loại câu hỏi:
- **SINGLECHOICE**: Chọn 1 đáp án → trả về `"A"`
- **MULTIPLECHOICE**: Chọn nhiều → trả về `"A,C"` 
- **TRUEFALSE**: Đúng/Sai → trả về `"A"` hoặc `"B"`
- **ESSAY**: Tự luận → trả về text đầy đủ

Claude được yêu cầu trả về **JSON thuần túy** (không markdown, không text thừa).

## Database Schema

```sql
-- Khách hàng
customers (id, email, phone_number, full_name, password_hash, role, is_active, created_at)

-- API keys với thời hạn
api_keys (id, key_value, customer_id, expires_at, is_active, validity_days, usage_count, last_used_at)

-- Phiên thi
exam_sessions (id, customer_id, exam_code, subject_code, device_id, created_at)

-- Câu hỏi và đáp án (core data)
question_records (id, exam_session_id, question_id, question_number, question_type,
                  question_text, options jsonb, answer, auto_click, ai_model_used,
                  processing_time_ms, captured_at, has_screenshot, success, error_message)
```

## Thêm AI provider mới

Implement interface `AiSolverService`:

```java
@Service
public class GptAiSolverService implements AiSolverService {
    @Override
    public SolveResponse solveQuestion(SolveRequest request) { ... }
    
    @Override
    public String getProviderName() { return "GPT-4o (OpenAI)"; }
}
```

Sau đó swap bean trong config hoặc dùng `@Primary` / `@Qualifier`.
