# Internal Sales Chat Assistant — as-built

> **Phạm vi:** trạng thái thực tế của code trên nhánh `feature/ai-internal` (cập nhật 2026-08-10).
> Khi tài liệu và code mâu thuẫn, **code đúng**. Sửa file này cùng lúc với code.

Trợ lý chat nội bộ cho nhân viên kinh doanh: tra cứu dữ liệu CRM trong phạm vi quyền của mình,
tổng hợp số liệu toàn đội, phân tích hiệu suất, và hỏi đáp theo tài liệu công ty (RAG).
**Chỉ đọc — BR-35.** Trợ lý không có bất kỳ công cụ ghi nào; không có tool calling.

Toàn bộ AI nằm **bên trong Spring Boot** qua Spring AI 1.1.7. Không có service Python/FastAPI —
thư mục `ai-service/` rỗng, kế hoạch Qwen3 + Ollama + BAAI/bge-m3 đã bị **thay thế**.

---

## 1. Luồng runtime của một câu hỏi

```
POST /api/v1/chat/sessions/{id}/messages/stream        (SSE — đường chính)
POST /api/v1/chat/sessions/{id}/messages               (blocking — fallback)
   │
   ▼
[0] ChatTurnWriter.begin()          ← TRANSACTION NGẮN #1
       kiểm session thuộc về caller → lưu câu hỏi → đọc 10 message gần nhất
       trả về ChatTurnContext (đã DETACH khỏi JPA)
   ▼
[1] Phân giải theo CẢ SESSION, không chỉ lượt hiện tại:
       IntentClassifier.resolveVietnamese()  → ngôn ngữ trả lời
       IntentClassifier.resolveAreas()       → CrmArea nào cần liệt kê chi tiết
       DateRangeResolver.resolve()           → khoảng thời gian (ChatDateRange)
   ▼
[2] IntentClassifier.classify()     ← GUARDRAIL, rule-based, KHÔNG gọi LLM
       blocked → trả lời canned ngay, tốn 0 token
   ▼
[3] ContextAssembler.assemble()     ← gom "REFERENCE DATA", ngoài mọi transaction
       ├── CrmSnapshotService          (SQL, 1 round trip cho counts)
       ├── PerformanceSnapshotService  (gọi lại 2 use case của Reporting)
       └── RagService.retrieveContext  (pgvector)
       chạy SONG SONG khi cần nhiều nguồn, timeout 6s, best-effort
   ▼
[4] ChatLlmService.stream() / .generate()   ← Gemini
   ▼
[5] ChatTurnWriter.complete()       ← TRANSACTION NGẮN #2
```

### Vì sao KHÔNG `@Transactional` cả method

`SendChatMessageUseCase` cố tình không transactional. Trước đây cả method nằm trong một
transaction → connection DB bị giữ suốt thời gian LLM trả lời (3–8s) → pool 5 connection cạn khi
có 5 cuộc chat đồng thời, làm đứng cả những request không liên quan gì tới chat.

Hai transaction ngắn được tách vào **bean riêng** `ChatTurnWriter`. **Bắt buộc phải là bean khác:**
`@Transactional` chạy qua proxy AOP, gọi `this.method()` trong cùng bean sẽ bypass proxy và
annotation bị bỏ qua âm thầm.

Hệ quả: mọi thứ giữa hai transaction phải làm việc trên object **đã detach** — đó là lý do tồn tại
của `ChatActor` và `ChatTurn` (record thuần). **Không bao giờ truyền `UserEntity` xuống các bước
[1]–[4]** — sẽ `LazyInitializationException` khi chạy trên thread khác.

---

## 2. Luồng nạp tài liệu (RAG ingest)

```
POST /api/v1/chat/documents  (multipart)
   │
   ├─ UploadDocumentUseCase: CHỈ lưu metadata row rồi RETURN NGAY
   │
   └─ DocumentIngestService.ingestInBackground()   @Async("documentIngestExecutor"), 1 thread
         [1] Tika              — bóc text layer
         [2] VisionOcrService  — OCR chữ NẰM TRONG ảnh (scan, biểu đồ, screenshot)
         [3] nối tikaText + ocrText thành MỘT text (không chunk riêng — xem ghi chú dưới)
         [4] SemanticChunker   — cắt theo NGHĨA, không theo số token
         [5] gắn metadata {documentId, title, fileName}
         [6] embedding 768 chiều (Gemini)
         [7] vectorStore.add() → bảng leadora_vector_store
         [8] xoá bản cũ cùng title + ragService.evictContextCache()
```

**Vì sao async:** ingest file Word/PDF lớn mất vài phút, dài hơn thời gian browser giữ HTTP request.
Luồng đồng bộ cũ khiến client báo lỗi trong khi server vẫn commit thành công — triệu chứng
*"upload báo fail nhưng file xuất hiện sau lần upload kế tiếp"*.

**Trạng thái không cần đổi schema:** cột `chunk_count` kiêm luôn cột trạng thái —
`0` = đang xử lý · `>0` = số chunk thật · `-1` (`CHUNK_COUNT_FAILED`) = thất bại.
Row thất bại được **giữ lại** (không xoá) để UI hiện lỗi thay vì file biến mất im lặng.

**Vì sao nối text layer + OCR thành một:** giữ riêng thì một file toàn ảnh luôn sinh tối thiểu 2
chunk — mẩu rác Word để lại ở text layer thành một chunk riêng, vector gần như rỗng nhưng mang tiêu
đề tài liệu, cạnh tranh slot top-K. `SemanticChunker` chỉ gộp được mẩu vụn *trong cùng một* input.

**`SemanticChunker` khác gì splitter thường:** tách câu → embed **từng câu** (kèm cửa sổ ±1 câu
láng giềng) → đo cosine distance giữa hai câu liên tiếp → cắt ở chỗ vượt **percentile 90**.
Giá phải trả: mỗi upload bắn hàng trăm request embedding. Tắt bằng `AI_SEMANTIC_CHUNKING=false`
để về token splitting (rẻ hơn 10–30×). Mọi lỗi đều fallback về `TokenTextSplitter`.

---

## 3. Bản đồ file

| Vai trò | Đường dẫn (`com.novax.leadora`) |
|---|---|
| **API** | `api/controller/ChatController.java` · `ChatDocumentController.java` |
| **Orchestrator** | `application/usecase/chat/SendChatMessageUseCase.java` (blocking) · `StreamChatMessageUseCase.java` (SSE) |
| **Transaction ngắn** | `application/usecase/chat/ChatTurnWriter.java` |
| **DTO detached** | `chat/ChatActor.java` · `ChatTurn.java` · `ChatTurnContext.java` |
| **Guardrail** | `chat/intent/IntentClassifier.java` · `ChatIntent.java` · `IntentResult.java` · `chat/GuardrailMessages.java` |
| **Thời gian** | `chat/time/ChatClock.java` · `ChatDateRange.java` · `chat/intent/DateRangeResolver.java` · `config/TimeConfig.java` |
| **Cổng gom context** | `chat/ContextAssembler.java` |
| **Dữ liệu CRM (scope)** | `chat/CrmSnapshotService.java` · `chat/intent/CrmArea.java` · `chat/dto/*` |
| **Hiệu suất** | `chat/PerformanceSnapshotService.java` → gọi `usecase/reporting/GetSalesPerformanceReportUseCase` + `GetTaskPerformanceReportUseCase` |
| **SQL gộp** | `infrastructure/persistence/repository/ChatAggregateRepository.java` (native, không JPQL) |
| **RAG** | `infrastructure/integration/ai/RagService.java` · `SemanticChunker.java` · `VisionOcrService.java` · `DocumentImageExtractor.java` |
| **LLM** | `infrastructure/integration/ai/ChatLlmService.java` (chứa `SYSTEM_PROMPT`) |
| **Ingest nền** | `chat/DocumentIngestService.java` · `UploadDocumentUseCase.java` · `DeleteDocumentUseCase.java` · `ListDocumentsUseCase.java` |
| **Lỗi LLM** | `chat/AiErrorClassifier.java` (phân biệt hết quota vs lỗi thật) |
| **Danh tính** | `common/security/CurrentUserProvider.java` |
| **Thread pool** | `config/AsyncConfig.java` |
| **Entity** | `infrastructure/persistence/entity/AiChatSessionEntity` · `AiChatMessageEntity` · `AiDocumentEntity` |
| **Frontend** | `app/(dashboard)/ai-assistant/page.tsx` · `features/ai_assistant/{components/FloatingAssistant.tsx, components/LiaMascot.tsx, screens/LiaLandingScreen.tsx, hooks/use_chat_sessions.ts}` · `services/chat_assistant_service.ts` · `stores/chat_store.ts` |

---

## 4. Phân quyền và data scope

### Bốn lớp phòng thủ

| # | Ở đâu | Chặn gì |
|---|---|---|
| 1 | `@PreAuthorize` trên controller | ai được vào chat |
| 2 | `IntentClassifier.isMutation()` | lệnh sửa/xoá — **chặn trước khi gọi LLM, tốn 0 token** |
| 3 | `WHERE assigned_user_id = :scope` trong SQL | LLM **không bao giờ nhìn thấy** row ngoài quyền |
| 4 | `SYSTEM_PROMPT` luật 1 & 2 | lưới an toàn cuối |

**Lớp 3 là lớp duy nhất có giá trị bảo mật thật.** Lớp 2 và 4 chỉ là UX + tiết kiệm token, vì trợ lý
**về mặt kiến trúc không có đường ghi** — không tool calling, không function nào mutate. Lớp 2 vì thế
được viết rộng rãi có chủ đích: thà lọt một câu lệnh (vô hại) còn hơn chặn nhầm câu hỏi thật
("lead nào được **tạo** cuối cùng?").

### Quyền

```
ChatController          @PreAuthorize("hasAnyRole('SALES','MANAGER')")
ChatDocumentController  @PreAuthorize("hasRole('MANAGER')")     ← chỉ MANAGER upload tài liệu
```

`CrmSnapshotService.canSeeAllData()` — chỉ `{MANAGER, ADMIN}` đọc được mọi record.
`AI_CHAT_TOP_PRIVILEGE=true` mở cho mọi role (**cửa hậu dev, mặc định `false`**).

### Ai là người hỏi — `CurrentUserProvider.resolve()`

Thứ tự, dừng ngay khi khớp:
1. `sub` (UUID) trong JWT đã verify
2. claim `email` trong **cùng** JWT đó → account phải được Admin tạo trước
3. JWT hợp lệ nhưng không map ra account → **`ACCOUNT_NOT_PROVISIONED` (403), DỪNG HẲN**
4. Header `X-User-Id` — **chỉ khi profile `dev`**
5. `AI_CHAT_DEV_USER_ID` — **chỉ khi profile `dev`**
6. Không ra gì → `AccessDeniedException` (403)

> ⚠️ Bước 3 phải dừng hẳn, không được rơi xuống bước 4. Nếu rơi xuống thì bất kỳ ai có token đều
> mạo danh được người khác chỉ bằng cách bỏ claim `email`.
> Fallback cũ *"lấy user đầu tiên trong DB"* **đã bị xoá** — đó là lỗ hổng cho phép truy cập
> không cần xác thực.

### Intent → nguồn dữ liệu (`ContextAssembler.assemble()`)

| `ChatIntent` | Nguồn | Ghi chú |
|---|---|---|
| `MUTATION_BLOCKED` / `OFF_TOPIC` | — | chặn, không gọi LLM |
| `META_CONVERSATION` | **không gì cả** | "dịch lại câu vừa rồi" — lịch sử đã đủ, rẻ nhất |
| `PERSONAL_DATA` | `personalSnapshot()` | có sở hữu cách ("của tôi") → ghim vào người hỏi **kể cả Manager** |
| `ASSIGNED_DATA` | `scopedSnapshot()` | nêu tên đồng nghiệp → snapshot của người đó (chỉ khi `canSeeAllData`) |
| `TEAM_SUMMARY` | `teamSummary()` | không đủ quyền → **thu hẹp âm thầm** về scope riêng, không từ chối |
| `PERFORMANCE_REPORT` | `PerformanceSnapshotService` + snapshot | tỉ lệ **và** bản ghi, chạy song song |
| `DOC_QUERY` | RAG + danh mục tài liệu | luôn kèm danh mục, kể cả khi không tìm ra excerpt |
| `GENERAL_BUSINESS` | RAG + CRM song song | |

Thứ tự kiểm trong `classify()` có ý nghĩa: **sở hữu cách trước** (`ASSIGNED_KEYWORDS` →
`PERSONAL_DATA`), rồi `PERFORMANCE_KEYWORDS`, rồi `TEAM_KEYWORDS`. Đảo thứ tự là hỏng:
- "top 5 deal **của tôi**" — `top ` cũng là từ khoá team → nếu team trước thì trả về dữ liệu cả công ty.
- "xếp hạng **nhân viên**" — cũng là từ khoá team → nếu team trước thì `PERFORMANCE_REPORT` không bao giờ chạy.

---

## 5. Tầng thời gian

`SYSTEM_PROMPT` là hằng, nhưng **không chứa ngày nào**. Thời gian được nối vào lúc gọi:

```java
// ChatLlmService.systemText() — chạy MỖI request
SYSTEM_PROMPT + LANGUAGE_HINT + "\n\n" + clock.promptBlock() + REFERENCE DATA
```

`ChatClock.promptBlock()` sinh ra khối này mỗi lượt, với 12 mốc **đã tính sẵn**:

```
=== CURRENT TIME (business timezone Asia/Ho_Chi_Minh) ===
Now: 2026-08-10T00:02+07:00 (Monday)
Resolved periods — use these exact dates, do not compute your own:
  today = 2026-08-10 .. 2026-08-10
  yesterday / last_7_days / this_week / last_week / last_30_days
  this_month / last_month / this_quarter / last_quarter / this_year / last_year
```

**Vì sao tính sẵn thay vì để model tự suy:** LLM làm số học lịch rất hay sai (tuần bắt đầu thứ Hai
hay CN, tháng 30/31 ngày, năm nhuận, quý mấy). Tính bằng `java.time` là deterministic và test được.
Nguyên tắc chung của cả package: **đưa dữ liệu, đừng đưa lời dặn.**

**Instant và calendar là hai đầu vào tách rời:**

```java
ChatClock(Clock clock)                                  // Clock nói KHI NÀO
OffsetDateTime.now(clock.withZone(zone()))              // zone() nói LỊCH NÀO
```

`TimeConfig` cấp bean `Clock.systemUTC()` — UTC chứ không phải zone mặc định, để không code nào vô
tình thừa hưởng timezone của container. **Đó chính là bug gốc:** `OffsetDateTime.now()` theo zone
JVM = UTC trên Cloud Run, nên một lead tạo lúc 06:00 ngày 09/08 giờ VN nằm ở 23:00 ngày 08/08 UTC →
"hôm nay" trả lời sai ngày, 7 tiếng trên mỗi 24 tiếng.

**Lọc theo ngày:** `DateRangeResolver` (rule-based, 0ms, 0 token) nhận `hôm nay/hôm qua/tuần này/
tháng trước/quý này/năm ngoái`, `7 ngày qua`, `3 tháng qua`, `tháng 7`, `2026-03-05`, `05/03/2026`,
và span hai ngày. Không nhận ra → `ChatDateRange.allTime()` → **không lọc gì**, plan y hệt như trước.

> Dùng rule ở đây nhưng phê phán rule ở `IntentClassifier` là có lý do: cách gọi tên một khoảng thời
> gian là **tập đóng** nên rule phủ hết được; còn intent thì mở.

`from`/`to` xuyên xuống 9 nhánh SQL gộp + 8 query listing + 2 query aggregate. Mọi bảng lọc trên
**`created_at`** — một cột duy nhất, có chủ đích: "paid tháng này" ≠ "created tháng này", trộn lẫn
sẽ cho ra bộ số không cộng/so sánh được với nhau.

**Kế thừa qua lượt:** ngôn ngữ, area và khoảng thời gian đều kế thừa từ các lượt trước —
"ok, chi tiết hơn" sau "lead hôm nay" vẫn là hôm nay. Lưu ý: `resolve()` **đọc lại chữ** từ lượt cũ
rồi phân giải theo `today()` hiện tại, nên hội thoại vắt qua nửa đêm sẽ chuyển sang ngày mới.
Đó là hành vi cố ý — mở lại hội thoại hôm qua vào sáng nay là tình huống phổ biến hơn, và dòng
`Period:` trong reference data buộc model công bố ngày nó trả lời cho, nên sai thì nhìn thấy được.

---

## 6. Cấu trúc prompt

```
SYSTEM MESSAGE  (dựng lại mỗi request)
├── SYSTEM_PROMPT     ← static final, ~1.400 token. Chỉ chứa CHÍNH SÁCH
├── LANGUAGE_HINT     ← hằng, chọn VI hoặc EN
├── CURRENT TIME      ← runtime, ChatClock
└── REFERENCE DATA    ← runtime, SQL/pgvector
MESSAGES  ← tối đa MAX_HISTORY_MESSAGES = 10 MESSAGE (≈5 lượt), không phải 10 lượt
USER      ← câu hỏi hiện tại
```

Thứ tự có chủ đích: phần tĩnh **trước**, phần đổi mỗi lượt **sau**. Nếu bật prompt caching của
Gemini (cache theo tiền tố), phần tĩnh vẫn hit cache; đảo lại thì dòng `Now:` đổi mỗi phút sẽ phá
vỡ toàn bộ cache.

`SYSTEM_PROMPT` có 5 luật chính, nhưng luật 3 đã nở thành 8 luật con (`3b`→`3g`) — mỗi cái sinh ra
từ một bug thật. **Đánh số hiện đang lộn xộn** (`3d2` nằm giữa `3e2` và `3f`), phản ánh cách nó lớn
lên: model trả lời sai → thêm một luật con.

---

## 7. API

```
POST   /api/v1/chat/sessions                        tạo session      body: { title? }
GET    /api/v1/chat/sessions                        danh sách session
GET    /api/v1/chat/sessions/{id}/messages          lịch sử tin nhắn
POST   /api/v1/chat/sessions/{id}/messages          gửi tin (blocking)  body: { content }
POST   /api/v1/chat/sessions/{id}/messages/stream   gửi tin (SSE)       body: { content }
PUT    /api/v1/chat/sessions/{id}                   đổi tên          body: { title }
DELETE /api/v1/chat/sessions/{id}                   xoá mềm

POST   /api/v1/chat/documents                       upload  multipart: file, title?   (MANAGER)
GET    /api/v1/chat/documents                       danh sách                          (MANAGER)
DELETE /api/v1/chat/documents/{id}                  xoá tài liệu + embeddings          (MANAGER)
```

### Giao thức SSE — một JSON object mỗi event

```
start  {userMessage, intent, blocked}   một lần, trước mọi text
token  {t}                              0..n lần, nối theo thứ tự
done   {assistantMessage}               một lần, SAU khi đã persist
error  {message}                        thay cho done; text hiển thị được cho user
```

Lượt bị chặn vẫn phát `start` → refusal như một `token` → `done`, để client chỉ cần một code path.
**Chỉ persist một lần, ở cuối** — ghi từng phần sẽ để lại message rách khi client ngắt giữa chừng.

Frontend dùng `fetch` + đọc stream chứ **không** dùng `EventSource`, vì `EventSource` không set được
header `Authorization`. Lỗi transport → tự fallback sang endpoint blocking.

---

## 8. Cấu hình

### Model / provider

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `GEMINI_USE_VERTEX` | `true` | `true` = Vertex AI (ADC/service account) · `false` = AI Studio |
| `GEMINI_API_KEY` / `GOOGLE_GENAI_API_KEY` | *(rỗng)* | phải **rỗng thật** khi chạy Vertex |
| `GEMINI_PROJECT_ID` · `GEMINI_LOCATION` | `mock-project-id` · `asia-southeast1` | |
| `GEMINI_CHAT_MODEL` | `gemini-2.5-flash` | |
| `GEMINI_CHAT_TEMPERATURE` | `0.2` | |
| `GEMINI_THINKING_BUDGET` | `0` | tắt "thinking token"; **Gemini 3.x có thể bỏ qua** |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-001` | `text-embedding-004` → 404 |
| `AI_EMBEDDING_DIMENSIONS` | `768` | phải khớp cột vector |

> ⚠️ **Cạm bẫy đã mất nhiều giờ:** default của `api-key` phải là `${...:}` (rỗng), **không phải**
> `#{null}`. `@ConfigurationProperties` **không** eval SpEL, nên `#{null}` bind thành chuỗi 6 ký tự
> `"#{null}"` — một api-key non-blank. Chat sống sót vì `vertex-ai: true` bắt nó bỏ qua key, nhưng
> **connection embedding không có công tắc đó** → "API key not valid" ở mọi lần upload.
>
> Embedding client suy ra mode thuần từ property có mặt: có `api-key` → AI Studio ·
> có `project-id`+`location` → Vertex AI.

### RAG / OCR

| Biến | Mặc định |
|---|---|
| `ai.rag.retrieval.top-k` | `6` |
| `ai.rag.retrieval.similarity-threshold` | `0.4` |
| `AI_SEMANTIC_CHUNKING` | `true` |
| `ai.rag.semantic-chunking.{breakpoint-percentile, buffer-size, max-chars, min-chars, max-sentences}` | `90, 1, 1800, 160, 1500` |
| `AI_VISION_OCR_MODE` | `ALL_IMAGES` (· `SCANNED_ONLY` · `OFF`) |
| `AI_VISION_OCR_MAX_IMAGES` | `20` — chốt chặn chính về quota |
| `AI_VISION_OCR_SCANNED_THRESHOLD` | `200` ký tự |
| `AI_VISION_OCR_MODEL` | *(rỗng = dùng model chat)* |
| `AI_VECTORSTORE_INIT_SCHEMA` | `true` |
| `AI_MAX_UPLOAD_SIZE` | `20MB` |

### Khác

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `app.business-zone` | `Asia/Ho_Chi_Minh` | lịch nghiệp vụ cho toàn bộ chat |
| `AI_CHAT_TOP_PRIVILEGE` | `false` | **cửa hậu dev** — `true` mở mọi record cho mọi role |
| `AI_CHAT_DEV_USER_ID` | *(rỗng)* | chỉ có tác dụng ở profile `dev` |

`.env`: **một file duy nhất ở gốc repo** (`leadora/.env`), backend nạp qua
`spring.config.import: optional:file:.env` + `optional:file:../.env`.

### Retry — cố ý fail nhanh

Spring AI mặc định retry 429 tới 10 lần (2s → ×5 → tối đa 3 phút), khiến lỗi quota biến thành treo
hàng chục giây. Cấu hình hiện tại `AI_RETRY_MAX_ATTEMPTS=2` — tức 2 lần gọi = **1 lần retry ngắn**
(800ms → ×2 → trần 3s), rồi `AiErrorClassifier` phân biệt *hết quota* với *lỗi thật* để trả thông
báo đúng. Nâng lên khi đã có quota trả phí.

---

## 9. Thread pool (`config/AsyncConfig.java`)

| Bean | core / max / queue | Dùng cho |
|---|---|---|
| `documentIngestExecutor` | 1 / 1 / 20 | ingest tài liệu — **cố ý 1 thread**, tránh bão embedding |
| `chatStreamExecutor` | 4 / 24 / 50 | SSE, `CallerRunsPolicy` |
| `taskExecutor` | 5 / 20 / 500 | gom context song song trong `ContextAssembler` |

> ⚠️ `CallerRunsPolicy` trên `chatStreamExecutor`: khi pool đầy, việc bị đẩy ngược về **thread
> request của Tomcat** → áp lực lan ra toàn app. Chưa có rate limit per-user.

---

## 10. Hằng số tuning

| Hằng | Giá trị | Ở đâu |
|---|---|---|
| `MAX_HISTORY_MESSAGES` | 10 | `ChatLlmService` |
| `PROMPT_HISTORY_LIMIT` | 10 | `ChatTurnWriter` — khớp cái trên, không fetch thừa |
| `GATHER_TIMEOUT_SECONDS` | 6 | `ContextAssembler` |
| `STREAM_TIMEOUT_MS` | 180.000 | `StreamChatMessageUseCase` |
| listing caps | 10 (payments 8) | `CrmSnapshotService` |
| `MAX_REPS` / `MAX_SUGGESTED_REPS` / `MAX_MENTIONED_STAFF` | 20 / 6 / 3 | `CrmSnapshotService` |
| `MIN_MENTION_CHARS` | 5 | tên ngắn hơn quá dễ va vào từ thường (bỏ dấu, "An" → "an") |
| `MAX_ROWS` | 15 | `PerformanceSnapshotService` |
| `CHUNK_COUNT_FAILED` | -1 | `DocumentIngestService` |

---

## 11. Database

| Bảng | Chủ | Ghi chú |
|---|---|---|
| `ai_chat_sessions` | JPA | soft delete qua `ChatSessionStatus.DELETED` |
| `ai_chat_messages` | JPA | có `intent_matched` — dùng cho kế thừa intent ở lượt sau |
| `ai_documents` | JPA | `chunk_count` kiêm cột trạng thái |
| `leadora_vector_store` | **Spring AI** | `vector(768)`, HNSW, `COSINE_DISTANCE` |

`ddl-auto` mặc định là **`validate`** — schema quản lý ngoài (Supabase). Script tay:
`backend/src/main/resources/db/ai_chat_assistant.sql`.

> ⚠️ Đổi số chiều embedding thì **phải** `DROP TABLE public.leadora_vector_store;` — Spring AI không
> tự migrate cột vector.

Chat **đọc** thêm: `leads`, `deals`, `tasks`, `quotations`, `bookings`, `payments`, `customers`,
`sla_tracking`, `users`. Index `created_at` + `assigned_user_id` đã có ở 7 bảng đầu.
**`customers` KHÔNG có index nào** — lọc ngày trên bảng đó sẽ seq scan.

---

## 12. Test

| File | Chốt cái gì |
|---|---|
| `IntentClassifierTest` | guardrail BR-35, off-topic, meta, routing BR-36, ưu tiên sở hữu cách |
| `DateRangeResolverTest` | phân giải kỳ, kế thừa qua lượt, `thắng 7 deal` ≠ tháng 7 |
| `ChatClockTest` | **biên lịch bằng `Clock.fixed`**: nửa đêm, CN→T2, 31/12→01/01, năm nhuận, ngày 31 |
| `TimeConfigTest` | bean `Clock` wire được (`@DataJpaTest` không nạp `TimeConfig` → thiếu bean vẫn xanh suite nhưng chết lúc boot) |
| `CrmSnapshotServiceTest` | rò rỉ scope, header listing, area proportionate |
| `ChatAggregateRepositoryTest` | text SQL native: đủ 9 nhánh, đủ scope, đủ date filter, **không dính token** |
| `ChatQueryCompilationTest` | `@DataJpaTest` — compile JPQL thật bằng EntityManager |
| `RagServiceTest` · `SemanticChunkerTest` · `VisionOcrServiceTest` · `DocumentImageExtractorTest` | ingest |

`ChatAggregateRepository` là **native SQL**, nên `ChatQueryCompilationTest` **không** validate được nó.
Sửa câu đó thì phải chạy với DB thật.

---

## 13. Muốn sửa X thì đụng Y

| Muốn | Sửa |
|---|---|
| Đổi giọng/định dạng trả lời | `ChatLlmService.SYSTEM_PROMPT` |
| Thêm CRM area mới | `CrmArea` (+ screen path) → thêm nhánh UNION trong `ChatAggregateRepository.COUNT_ALL` → `append*()` trong `CrmSnapshotService` → từ khoá trong `IntentClassifier.AREA_KEYWORDS`. `ChatAggregateRepositoryTest.everyAreaIsCounted` sẽ fail nếu quên nhánh SQL |
| Thêm intent mới | `ChatIntent` → nhánh trong `IntentClassifier.classify()` → case trong `ContextAssembler.assemble()` → thêm vào `dataIntentOrNull()` nếu muốn kế thừa |
| Thêm cách gọi tên khoảng thời gian | `ChatClock.anchors()` + `DateRangeResolver.ANCHOR_PHRASES` (hai chỗ, cùng key) |
| Đổi nguồn thời gian (API ngoài) | **chỉ** thay bean trong `TimeConfig` — không class nào khác phải sửa. Đừng gọi mạng mỗi lần đọc; lấy mẫu định kỳ rồi `Clock.offset(Clock.systemUTC(), drift)` |
| Đổi múi giờ nghiệp vụ | `app.business-zone` |
| Nới/siết quyền xem toàn bộ | `CrmSnapshotService.FULL_SCOPE_ROLES` |
| Đổi độ dài lịch sử gửi model | `MAX_HISTORY_MESSAGES` **và** `PROMPT_HISTORY_LIMIT` (giữ bằng nhau) |
| RAG trả sai/thiếu | `ai.rag.retrieval.top-k`, `similarity-threshold`, rồi tới `SemanticChunker` |
| Upload ra 0 chunk | xem log `Parsed {} ({}): {} chars from text layer, {} chars from vision OCR` — hai số đó giải thích mọi ca thất bại |

---

## 14. Giới hạn và rủi ro đã biết

1. **Prompt injection qua tài liệu RAG.** Chunk lấy từ pgvector được nối thẳng vào **system message**.
   Một file chứa *"bỏ qua mọi chỉ dẫn trước đó…"* mang trọng lượng của system prompt. Giảm nhẹ duy
   nhất: chỉ MANAGER upload được. Cách chữa đúng: đưa REFERENCE DATA xuống user message kèm
   delimiter, và dặn model coi nội dung trong đó là *dữ liệu*, không phải *chỉ thị*.
2. **`IntentClassifier` ~500 dòng danh sách từ khoá.** So khớp substring trên text đã bỏ dấu → chuỗi
   ngắn rất dễ va (`" sla "` phải bọc space vì là substring của "translate"/"slack"). Không có cách
   nào chứng minh danh sách đã đủ. Thay thế lâu dài: tool calling, hoặc LLM classifier có structured
   output.
3. **Không có rate limit per-user** cho chat. Xem cảnh báo `CallerRunsPolicy` ở §9.
4. **Không có cross-module orchestrator.** `application.workflow_service` trong SDD **không tồn tại**.
5. **`ReportingUtils` quy đổi ngày theo UTC**, trong khi phần còn lại của chat dùng
   `app.business-zone`. Lệch tối đa 7h, chỉ đáng kể khi hỏi đúng **một ngày**.
   `PerformanceSnapshotService.period()` khai báo cảnh báo này vào reference data để model tự hedge.
   Sửa triệt để = đổi `ReportingUtils` → **đổi số của toàn bộ màn hình Reporting**, phải cân nhắc riêng.
6. **`sla_records` là bảng chết** — không job nào cập nhật. Chat đọc `sla_tracking`, cùng bảng mà
   `SlaBreachScheduler` và màn hình SLA Control dùng. Đừng đọc nhầm.
7. **Cache `rag-context` key theo query text, không theo user.** Đúng vì tài liệu công ty là kiến
   thức chung — nhưng nếu sau này thêm phân quyền tài liệu thì **rò rỉ ngay**.
8. Chỉ ~6/22 automated function trong `pipeline-and-workflow.md §3` là có thật. Đọc banner ở đó
   trước khi giả định một job tồn tại.
