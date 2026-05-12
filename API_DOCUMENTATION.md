# Exam Solver API Documentation

## Base URL

```text
http://localhost:8081
```

Swagger UI:

```text
http://localhost:8081/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8081/v3/api-docs
```

## Response Format

Các API nghiệp vụ thường trả về wrapper:

```json
{
  "success": true,
  "message": "Optional message",
  "data": {}
}
```

Một số API dành cho Rust client trả response trực tiếp, không bọc trong `ApiResponse`, ví dụ `POST /api/solve` và `GET /api/jobs/{jobId}`.

Các field JSON dùng đúng theo DTO hiện tại: một số response dùng `snake_case`, riêng entity `QuestionBank` đang trả trực tiếp theo Java property dạng `camelCase`.

## Authentication

JWT truyền qua header:

```http
Authorization: Bearer <JWT_TOKEN>
```

Theo code hiện tại:

- Public: `POST /api/auth/**`, `POST /api/solve`, `GET /api/jobs/{jobId}`.
- Admin: toàn bộ `/api/admin/**` yêu cầu JWT có role `ADMIN`.
- Các endpoint dùng `Authentication auth` cần JWT hợp lệ để hoạt động đúng: `/api/me/profile`, `/api/sessions/**`, `/api/human/sessions/**`, `/api/human/jobs/{jobId}/answer`, `/api/jobs/my`.
- `GET /api/human/jobs/{jobId}` hiện không kiểm tra JWT ở controller.

## Pagination

Các endpoint nhận `Pageable` hỗ trợ query:

```text
page=0&size=20&sort=createdAt,desc
```

Response là format mặc định của Spring `Page`, ví dụ rút gọn:

```json
{
  "success": true,
  "data": {
    "content": [],
    "totalElements": 50,
    "totalPages": 3,
    "size": 20,
    "number": 0,
    "first": true,
    "last": false,
    "empty": false
  }
}
```

## Auth API

### Register

`POST /api/auth/register`

Tạo tài khoản mới. Response status hiện tại là `200 OK`.

Request:

```json
{
  "email": "student@example.com",
  "phoneNumber": "0123456789",
  "fullName": "John Doe",
  "password": "securePassword123",
  "role": "CUSTOMER"
}
```

Validation:

- `email`: bắt buộc, đúng định dạng email.
- `phoneNumber`: nếu gửi thì phải có 10-11 chữ số.
- `fullName`: bắt buộc.
- `password`: bắt buộc, tối thiểu 8 ký tự.
- `role`: optional, mặc định `CUSTOMER`.

Response:

```json
{
  "success": true,
  "message": "Đăng ký thành công",
  "data": {
    "token": "jwt-token",
    "email": "student@example.com",
    "full_name": "John Doe",
    "role": "CUSTOMER"
  }
}
```

### Login

`POST /api/auth/login`

Request:

```json
{
  "email": "student@example.com",
  "password": "securePassword123"
}
```

Response:

```json
{
  "success": true,
  "message": "Đăng nhập thành công",
  "data": {
    "token": "jwt-token",
    "email": "student@example.com",
    "full_name": "John Doe",
    "role": "CUSTOMER"
  }
}
```

## Customer API

### Get Profile

`GET /api/me/profile`

Yêu cầu JWT.

Response:

```json
{
  "success": true,
  "data": {
    "email": "student@example.com",
    "fullName": "John Doe",
    "phoneNumber": "0123456789",
    "role": "CUSTOMER",
    "createdAt": "2026-05-12T10:30:00"
  }
}
```

Lưu ý: endpoint này trả `Map`, nên field là `camelCase`, không phải `snake_case`.

## Solve API

### Submit Question

`POST /api/solve`

Public endpoint cho Rust client. Hệ thống xác thực customer bằng `session.email`; customer phải tồn tại, active và còn hạn truy cập.

Request:

```json
{
  "question_id": "q_001",
  "session": {
    "email": "student@example.com",
    "exam_code": "HADA12",
    "subject_code": "MLN131",
    "device_id": "device_001"
  },
  "question": {
    "number": "1",
    "question_type": "SINGLECHOICE",
    "text": "What is 2 + 2?",
    "options": [
      { "label": "A", "text": "3" },
      { "label": "B", "text": "4" }
    ],
    "screenshot_base64": "iVBORw0KGgo..."
  },
  "captured_at": "2026-05-12T10:30:00",
  "ai_answer": null
}
```

Response `202 Accepted`:

```json
{
  "job_id": 123,
  "question_id": "q_001",
  "status": "PENDING",
  "message": "Question received. Processing asynchronously."
}
```

### Get Job Status

`GET /api/jobs/{jobId}?email={email}`

Public endpoint để poll kết quả. `email` bắt buộc và phải là owner của job.

Response:

```json
{
  "job_id": 123,
  "question_id": "q_001",
  "status": "DONE",
  "answer": "B",
  "answer_source": "AI",
  "auto_click": true,
  "processing_time_ms": 2450,
  "created_at": "2026-05-12T10:30:00",
  "updated_at": "2026-05-12T10:30:02"
}
```

Endpoint này hiện không trả các field chi tiết câu hỏi như `question_text` hoặc `options`; các field đó chỉ có ở Human API.

### Get My Jobs

`GET /api/jobs/my?status=DONE&page=0&size=20`

Yêu cầu JWT.

Query:

- `status`: optional, enum `WAITING_HUMAN`, `PENDING`, `PROCESSING`, `DONE`, `FAILED`, `SKIPPED`.
- `page`, `size`, `sort`: optional.

Response:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "job_id": 123,
        "question_id": "q_001",
        "status": "DONE",
        "answer": "B",
        "answer_source": "AI",
        "auto_click": true,
        "processing_time_ms": 2450,
        "created_at": "2026-05-12T10:30:00",
        "updated_at": "2026-05-12T10:30:02"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

## Exam Session API

### List My Sessions

`GET /api/sessions/my?page=0&size=20`

Yêu cầu JWT.

Response:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "exam_code": "HADA12",
        "subject_code": "MLN131",
        "device_id": "device_001",
        "created_at": "2026-05-12T10:30:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

### Get Session Questions

`GET /api/sessions/{sessionId}/questions`

Yêu cầu JWT. Customer chỉ xem được session của mình; admin được xem mọi session.

Response:

```json
{
  "success": true,
  "data": [
    {
      "id": 10,
      "question_hash": "abc123",
      "question_number": "1",
      "question_type": "SINGLECHOICE",
      "question_text": "What is 2 + 2?",
      "answer": "B",
      "answer_source": "AI",
      "success": true,
      "processing_time_ms": 2450,
      "created_at": "2026-05-12T10:30:00"
    }
  ]
}
```

## Human Solver API

### List Human Sessions

`GET /api/human/sessions?page=0&size=20`

Yêu cầu JWT. Trả danh sách session có job thuộc luồng human.

Response:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "exam_code": "HADA12",
        "subject_code": "MLN131",
        "device_id": "device_001",
        "pending_count": 25,
        "created_at": "2026-05-12T10:30:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

### Get Jobs In Human Session

`GET /api/human/sessions/{sessionId}/jobs?page=0&size=20`

Yêu cầu JWT. Customer chỉ xem được session của mình.

Response:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "job_id": 101,
        "question_id": "q_hw001",
        "question_number": "1",
        "question_type": "SINGLECHOICE",
        "question_text": "What is the capital of France?",
        "status": "WAITING_HUMAN",
        "auto_click": false,
        "options": [
          { "label": "A", "text": "London" },
          { "label": "B", "text": "Paris" }
        ],
        "created_at": "2026-05-12T08:00:00",
        "updated_at": "2026-05-12T08:00:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

### Get Human Job Detail

`GET /api/human/jobs/{jobId}`

Controller hiện tại không yêu cầu JWT và không kiểm tra owner.

Response:

```json
{
  "success": true,
  "data": {
    "job_id": 101,
    "question_id": "q_hw001",
    "question_number": "1",
    "question_type": "SINGLECHOICE",
    "question_text": "What is the capital of France?",
    "status": "WAITING_HUMAN",
    "auto_click": false,
    "options": [
      { "label": "A", "text": "London" },
      { "label": "B", "text": "Paris" }
    ],
    "created_at": "2026-05-12T08:00:00",
    "updated_at": "2026-05-12T08:00:00"
  }
}
```

### Submit Human Answer

`POST /api/human/jobs/{jobId}/answer`

Yêu cầu JWT.

Request:

```json
{
  "answer": "B"
}
```

Response:

```json
{
  "success": true,
  "message": "Đã lưu đáp án",
  "data": {
    "job_id": 101,
    "question_id": "q_hw001",
    "question_number": "1",
    "question_type": "SINGLECHOICE",
    "question_text": "What is the capital of France?",
    "status": "DONE",
    "answer": "B",
    "answer_source": "HUMAN",
    "auto_click": true,
    "options": [
      { "label": "A", "text": "London" },
      { "label": "B", "text": "Paris" }
    ],
    "processing_time_ms": 15,
    "created_at": "2026-05-12T08:00:00",
    "updated_at": "2026-05-12T10:30:00"
  }
}
```

## Admin API

Tất cả endpoint trong phần này yêu cầu JWT role `ADMIN`.

### List Customers

`GET /api/admin/customers?page=0&size=20`

Response:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "email": "student@example.com",
        "full_name": "John Doe",
        "phone_number": "0123456789",
        "role": "CUSTOMER",
        "active": true,
        "ai_mode_enabled": false,
        "access_expires_at": "2026-12-31T23:59:59",
        "created_at": "2026-05-12T10:30:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

### Get Customer

`GET /api/admin/customers/{id}`

Response:

```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "student@example.com",
    "full_name": "John Doe",
    "phone_number": "0123456789",
    "role": "CUSTOMER",
    "active": true,
    "ai_mode_enabled": false,
    "access_expires_at": "2026-12-31T23:59:59",
    "created_at": "2026-05-12T10:30:00"
  }
}
```

### Update Customer

`PATCH /api/admin/customers/{id}`

Chỉ cập nhật các field sau:

```json
{
  "access_expires_at": "2027-05-12T23:59:59",
  "ai_mode_enabled": true,
  "active": true
}
```

Response:

```json
{
  "success": true,
  "message": "Updated",
  "data": {
    "id": 1,
    "email": "student@example.com",
    "full_name": "John Doe",
    "phone_number": "0123456789",
    "role": "CUSTOMER",
    "active": true,
    "ai_mode_enabled": true,
    "access_expires_at": "2027-05-12T23:59:59",
    "created_at": "2026-05-12T10:30:00"
  }
}
```

### Extend Customer Access

`POST /api/admin/customers/{id}/extend?days=30`

`days` optional, mặc định `30`. Nếu customer còn hạn thì cộng thêm từ hạn hiện tại; nếu đã hết hạn thì cộng từ thời điểm hiện tại. Endpoint cũng set `active = true`.

Response:

```json
{
  "success": true,
  "message": "Gia hạn +30 ngày",
  "data": {
    "id": 1,
    "email": "student@example.com",
    "active": true,
    "ai_mode_enabled": true,
    "access_expires_at": "2027-06-11T23:59:59",
    "created_at": "2026-05-12T10:30:00"
  }
}
```

### List Prompt Versions

`GET /api/admin/prompts/{promptType}/versions`

`promptType` được chuyển sang uppercase.

Response:

```json
{
  "success": true,
  "data": [
    {
      "id": 2,
      "prompt_type": "SINGLECHOICE",
      "version_number": 2,
      "version_label": "v2",
      "prompt_template": "Prompt content",
      "is_active": true,
      "created_by": "admin@example.com",
      "notes": "Improved prompt",
      "created_at": "2026-05-12T10:30:00",
      "activated_at": "2026-05-12T11:00:00"
    }
  ]
}
```

### Get Active Prompt Version

`GET /api/admin/prompts/{promptType}/active`

Nếu không có active version, endpoint vẫn trả `200 OK` với `success: false`.

Response khi có active:

```json
{
  "success": true,
  "data": {
    "id": 2,
    "prompt_type": "SINGLECHOICE",
    "version_number": 2,
    "version_label": "v2",
    "prompt_template": "Prompt content",
    "is_active": true,
    "created_by": "admin@example.com",
    "notes": "Improved prompt",
    "created_at": "2026-05-12T10:30:00",
    "activated_at": "2026-05-12T11:00:00"
  }
}
```

Response khi không có active:

```json
{
  "success": false,
  "message": "Không có active version cho: SINGLECHOICE"
}
```

### Create Prompt Version

`POST /api/admin/prompts/versions`

`version_number` không nằm trong request; service tự tăng version theo `prompt_type`.

Request:

```json
{
  "prompt_type": "SINGLECHOICE",
  "version_label": "v3",
  "prompt_template": "Prompt content",
  "notes": "Testing"
}
```

Response:

```json
{
  "success": true,
  "message": "Version created",
  "data": {
    "id": 3,
    "prompt_type": "SINGLECHOICE",
    "version_number": 3,
    "version_label": "v3",
    "prompt_template": "Prompt content",
    "is_active": false,
    "created_by": "admin@example.com",
    "notes": "Testing",
    "created_at": "2026-05-12T15:00:00"
  }
}
```

### Get Prompt Version

`GET /api/admin/prompts/versions/{id}`

Response:

```json
{
  "success": true,
  "data": {
    "id": 3,
    "prompt_type": "SINGLECHOICE",
    "version_number": 3,
    "version_label": "v3",
    "prompt_template": "Prompt content",
    "is_active": false,
    "created_by": "admin@example.com",
    "notes": "Testing",
    "created_at": "2026-05-12T15:00:00"
  }
}
```

### Activate Prompt Version

`POST /api/admin/prompts/versions/{id}/activate`

Activate version được chọn và deactivate tất cả version khác cùng `prompt_type`.

Response:

```json
{
  "success": true,
  "message": "Version activated",
  "data": {
    "id": 3,
    "prompt_type": "SINGLECHOICE",
    "version_number": 3,
    "version_label": "v3",
    "prompt_template": "Prompt content",
    "is_active": true,
    "created_by": "admin@example.com",
    "notes": "Testing",
    "created_at": "2026-05-12T15:00:00",
    "activated_at": "2026-05-12T15:30:00"
  }
}
```

### Search Question Bank

`GET /api/admin/question-bank?subjectCode=MLN131&type=SINGLECHOICE&page=0&size=20`

Query:

- `subjectCode`: optional.
- `type`: optional, enum `SINGLECHOICE`, `MULTIPLECHOICE`, `TRUEFALSE`, `ESSAY`.
- `page`, `size`, `sort`: optional.

Lưu ý: endpoint trả trực tiếp entity `QuestionBank`, không dùng response DTO, nên field là `camelCase`.

Response:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "questionHash": "abc123",
        "normalizedText": "what is 2 2",
        "originalText": "What is 2 + 2?",
        "questionType": "SINGLECHOICE",
        "optionsJson": "[{\"label\":\"A\",\"text\":\"3\"},{\"label\":\"B\",\"text\":\"4\"}]",
        "answer": "B",
        "subjectCode": "MATH101",
        "hitCount": 145,
        "verified": true,
        "promptVersionId": 2,
        "createdAt": "2026-05-12T10:30:00",
        "updatedAt": "2026-05-12T10:30:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

### Verify Question Bank Answer

`PATCH /api/admin/question-bank/{id}/verify`

Request:

```json
{
  "answer": "C"
}
```

Response:

```json
{
  "success": true,
  "message": "Answer verified"
}
```

Do `ApiResponse` bỏ field `data` khi `null`, response không có `data: null`.

## Error Responses

### Validation Error

`400 Bad Request`

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "email": "Invalid email format",
    "password": "Password must be at least 8 characters"
  }
}
```

### Business Error

`400 Bad Request`

```json
{
  "success": false,
  "message": "Bạn không có quyền xem job này."
}
```

### Unauthorized

`401 Unauthorized`

```json
{
  "success": false,
  "message": "Email hoặc mật khẩu không đúng"
}
```

### Not Found

`404 Not Found`

```json
{
  "success": false,
  "message": "Job not found: 123"
}
```

### Internal Error

`500 Internal Server Error`

```json
{
  "success": false,
  "message": "Internal server error"
}
```

## Enum Values

### Customer Role

- `CUSTOMER`
- `ADMIN`

### Question Type

- `SINGLECHOICE`
- `MULTIPLECHOICE`
- `TRUEFALSE`
- `ESSAY`

### Job Status

- `WAITING_HUMAN`
- `PENDING`
- `PROCESSING`
- `DONE`
- `FAILED`
- `SKIPPED`

### Resolver Type

- `AI`
- `HUMAN`

### Answer Source

- `BANK`
- `AI`
- `HUMAN`
- `NONE`

## Date Time Format

Các timestamp dùng ISO local date time:

```text
2026-05-12T10:30:00
```

Jackson timezone được cấu hình là `Asia/Ho_Chi_Minh`.

## Disabled / Commented Endpoint

`ManualSolveController` hiện đang comment toàn bộ, nên các endpoint sau không active:

- `POST /api/questions/solve`
- `POST /api/questions/solve/batch`
- `GET /api/questions/jobs/{jobId}`
- `GET /api/questions/jobs`
