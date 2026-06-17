# Internal Sales Role-Based Chat Assistant (`feature/ai_internal`)

Trợ lý chat nội bộ cho nhân viên kinh doanh: tra cứu dữ liệu CRM của chính mình,
tổng hợp số liệu toàn đội, và hỏi đáp theo tài liệu công ty (RAG). **Chỉ đọc** (BR-35).

> Vì login/RBAC chưa hoàn thiện, mọi người dùng tạm có **quyền cao nhất** (hỏi gì cũng được
> trong phạm vi nghiệp vụ). Guardrail backend từ chối: (1) yêu cầu thay đổi dữ liệu
> (xóa/sửa/tạo/gửi/duyệt…) và (2) câu hỏi ngoài nghiệp vụ (toán, lập trình, đời sống…).

## Use cases đã làm
- Create New Chat Session · View Chat Session List · Continue Existing Chat Session
- Query Assigned Sales CRM Data · Query Team Sales CRM Summary · Delete Chat Session
- (Tùy chọn) Upload/List/Delete tài liệu công ty cho RAG

## Kiến trúc — hybrid (phân loại ý định ở backend rồi prefetch dữ liệu)
```
User → IntentClassifier (rule-based)
        ├─ MUTATION / OFF_TOPIC  → chặn, trả lời canned (KHÔNG gọi LLM)
        ├─ ASSIGNED_DATA         → CrmContextService.assignedContext(user)   (WHERE assigned_user_id = me)
        ├─ TEAM_SUMMARY          → CrmContextService.teamSummary()
        ├─ DOC_QUERY             → RagService.retrieveContext(query)          (pgvector)
        └─ GENERAL_BUSINESS      → RAG + dữ liệu của user
     → ChatLlmService (Ollama, system prompt chỉ-đọc) → lưu ai_chat_messages
```

### File chính
| Lớp | Đường dẫn |
|---|---|
| Guardrail | `application/usecase/chat/intent/IntentClassifier.java`, `GuardrailMessages.java` |
| Truy vấn CRM (scope) | `application/usecase/chat/CrmContextService.java` |
| RAG | `infrastructure/integration/ai/RagService.java` |
| LLM | `infrastructure/integration/ai/ChatLlmService.java` |
| Orchestrator | `application/usecase/chat/SendChatMessageUseCase.java` |
| API | `api/controller/ChatController.java`, `ChatDocumentController.java` |
| Actor tạm | `common/security/CurrentUserProvider.java` (header `X-User-Id` → `.env AI_CHAT_DEV_USER_ID` → user đầu tiên) |
| Frontend | `features/ai_assistant/screens/AiAssistantScreen.tsx` + `hooks/use_chat_sessions.ts` + `services/chat_assistant_service.ts` |

## API
```
POST   /api/v1/chat/sessions                 tạo session   (body: { title? })
GET    /api/v1/chat/sessions                 danh sách session
GET    /api/v1/chat/sessions/{id}/messages   lịch sử tin nhắn
POST   /api/v1/chat/sessions/{id}/messages   gửi tin       (body: { content })
DELETE /api/v1/chat/sessions/{id}            xoá (soft) session
POST   /api/v1/chat/documents                upload tài liệu (multipart: file, title?)
GET    /api/v1/chat/documents                danh sách tài liệu
DELETE /api/v1/chat/documents/{id}           xoá tài liệu + embeddings
```
Header tùy chọn `X-User-Id: <users.user_id>` để chỉ định người dùng (khi đã có login).

## Cài đặt & chạy

### 1. Ollama (LLM + embedding) — local, miễn phí
```powershell
# Cài Ollama: https://ollama.com/download
ollama pull qwen2.5:3b   # model chat (đổi qua OLLAMA_CHAT_MODEL)
ollama pull bge-m3       # model embedding 1024 chiều (đổi qua OLLAMA_EMBEDDING_MODEL)
ollama serve             # chạy ở http://localhost:11434
```
Máy yếu có thể dùng `qwen2.5:1.5b`. Nếu đổi embedding model khác 1024 chiều, sửa
`AI_EMBEDDING_DIMENSIONS` trong `.env` cho khớp (và xoá/tạo lại bảng vector store).

### 2. `.env` (đã thêm sẵn các key)
```
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_CHAT_MODEL=qwen2.5:3b
OLLAMA_EMBEDDING_MODEL=bge-m3
AI_EMBEDDING_DIMENSIONS=1024
AI_VECTORSTORE_INIT_SCHEMA=true
AI_CHAT_DEV_USER_ID=        # để trống = dùng user đầu tiên trong DB
```

### 3. Database
- `SPRING_JPA_DDL_AUTO=update` → Hibernate tự tạo bảng `ai_documents`.
- Spring AI tự tạo extension `vector` + bảng `leadora_vector_store` (do `initialize-schema=true`).
- Nếu cần làm thủ công (vd dùng `validate`): chạy `backend/src/main/resources/db/ai_chat_assistant.sql`.
- Supabase: extension `vector` có sẵn; user `postgres` có quyền `CREATE EXTENSION`.

### 4. Chạy
```powershell
cd backend  ; .\mvnw.cmd spring-boot:run     # API :8085  (khởi động được kể cả khi Ollama tắt)
cd frontend ; npm run dev                    # web :3000 → /ai-assistant
```
Backend **không** fail khi Ollama tắt (không auto-pull lúc boot); chỉ lúc gửi tin mà Ollama
không chạy thì trợ lý trả về thông báo lỗi thân thiện (MSG-31).

## Thử nhanh guardrail
- "Xóa lead giúp tôi" / "Cập nhật deal X thành WON" → bị chặn (đề nghị thao tác trên màn hình).
- "Giải phương trình bậc hai giúp tôi" → bị chặn (ngoài nghiệp vụ).
- "Tôi có bao nhiêu deal đang mở?" → trả lời từ dữ liệu được giao.
- "Tổng hợp doanh số toàn đội" → trả lời từ tổng hợp team.

## Giới hạn hiện tại / TODO khi có login
- `CurrentUserProvider` đang dùng `X-User-Id`/dev-user; thay bằng principal đã xác thực.
- Data-scope team đang mở cho tất cả (quyền cao nhất tạm thời); siết theo RBAC (SS=của mình, SM=team).
- LLM call nằm trong transaction (đơn giản cho MVP); tách read/generate/write khi scale.
