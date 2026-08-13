# THIẾT KẾ CƠ SỞ DỮ LIỆU & HIBERNATE ENTITY TÍCH HỢP AI ABSA
## (Aspect-Based Sentiment Analysis & XAI Integration Design - 100% Text-Based)

Tài liệu này cung cấp chi tiết thiết kế cấu trúc bảng cơ sở dữ liệu, các lớp Java Entity (Hibernate/JPA) và kịch bản SQL Migration phục vụ cho việc tích hợp mô hình AI Phân tích Cảm xúc Khía cạnh (ABSA) dạng văn bản và giải thích mô hình (XAI) vào hệ thống **Leadora CRM** sau khi hoàn thành giai đoạn huấn luyện mô hình.

---

## 1. PHÂN TÍCH THỰC TRẠNG & ĐỊNH HƯỚNG THIẾT KẾ

### 1.1. Phân tích Đầu vào AI (Bình luận thô)
*   **Trường hiện tại**: `comment` trong thực thể [SalesFeedbackEntity.java](file:///d:/leadora/backend/src/main/java/com/novax/leadora/infrastructure/persistence/entity/SalesFeedbackEntity.java).
*   **Định nghĩa trong DB**: Kiểu `TEXT` (`columnDefinition = "TEXT"`).
*   **Đánh giá**: Kiểu `TEXT` trong PostgreSQL cho phép lưu trữ tối đa **1 GB** văn bản. Đây là kiểu dữ liệu tối ưu nhất, hoàn toàn đáp ứng các bình luận cực dài của khách hàng mà không gặp rủi ro tràn cột.

### 1.2. Phân tích Đầu ra AI & Mở rộng tương lai (Những điểm còn thiếu)
Cấu trúc hiện tại của bảng `sales_feedbacks` **thiếu hoàn toàn** các trường phục vụ tích hợp AI và quản trị rủi ro:
1.  **Chưa có cột lưu kết quả AI dạng cấu trúc**: Cần lưu trữ JSON trả về từ AI Service gồm: Nhãn cảm xúc tổng quát, các khía cạnh phân tích cụ thể (Aspects), độ tin cậy (Confidence Score) và các từ khóa trọng tâm (Important Words từ Integrated Gradients).
2.  **Chưa có cơ chế theo dõi trạng thái phân tích**: Cần quản lý vòng đời xử lý AI (`ai_status`) để phục vụ cơ chế gọi API bất đồng bộ và kiểm soát lỗi/retry khi AI Service quá tải hoặc gặp sự cố.
3.  **Chưa hỗ trợ cảnh báo/lọc khẩn cấp**: Cần đánh dấu khẩn cấp (`is_urgent`) và phân cấp rủi ro (`risk_level`) trực tiếp trong cơ sở dữ liệu để Manager có thể truy vấn nhanh trên Dashboard mà không cần parse chuỗi JSON ở tầng ứng dụng.

---

## 2. THIẾT KẾ CHI TIẾT CÁC TRƯỜNG BỔ SUNG (100% TEXT-BASED)

| Tên trường (Java) | Tên cột (SQL) | Kiểu dữ liệu (PostgreSQL) | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `aiStatus` | `ai_status` | `VARCHAR(20)` | Trạng thái xử lý AI (`PENDING`, `PROCESSED`, `FAILED`). Mặc định: `PENDING`. |
| `aiAnalysisResult` | `ai_analysis_result` | `JSONB` | Chứa dữ liệu JSON kết quả ABSA & XAI chi tiết (Ánh xạ qua POJO `AiAnalysisResultDto`). |
| `isUrgent` | `is_urgent` | `BOOLEAN` | Đánh dấu phản hồi tiêu cực cần xử lý gấp (UC-10.1). Mặc định: `FALSE`. |
| `riskLevel` | `risk_level` | `VARCHAR(20)` | Cấp độ rủi ro dựa trên mức tiêu cực (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`). |
| `aiProcessedAt` | `ai_processed_at` | `TIMESTAMPTZ` | Thời điểm hoàn tất phân tích AI. |
| `aiErrorMessage` | `ai_error_message` | `TEXT` | Nội dung thông báo lỗi khi `ai_status` là `FAILED`. |

*Ràng buộc đặc biệt: Không thêm bất kỳ trường nào liên quan đến hình ảnh.*

---

## 3. MÃ NGUỒN JAVA ĐỀ XUẤT (SPRING BOOT 3.x / HIBERNATE 6.x)

Hệ thống đang chạy **Spring Boot 3.5.14** (sử dụng **Hibernate 6.x**), cho phép chúng ta dùng `@JdbcTypeCode(SqlTypes.JSON)` của Hibernate 6 để map trực tiếp cột `JSONB` xuống đối tượng Java POJO có kiểu dữ liệu rõ ràng (Type-safe DTO) nhằm tránh lỗi `ClassCastException` khi Jackson deserialization.

### 3.1. Định nghĩa Enums mới
Tạo các lớp enum này tại package `com.novax.leadora.infrastructure.persistence.entity.enums`:

**`AiStatus.java`**
```java
package com.novax.leadora.infrastructure.persistence.entity.enums;

public enum AiStatus {
    PENDING,     // Đang chờ gửi sang AI Service
    PROCESSED,   // Đã phân tích thành công và cập nhật kết quả
    FAILED       // Gặp lỗi khi gửi/xử lý (sẽ được quét bởi retry job)
}
```

**`RiskLevel.java`**
```java
package com.novax.leadora.infrastructure.persistence.entity.enums;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

### 3.2. Định nghĩa POJO Type-safe chứa kết quả AI (`AiAnalysisResultDto.java`)
Để tránh lỗi `ClassCastException` khi ép kiểu tự động từ Jackson `LinkedHashMap` sang các đối tượng chuyên biệt tại runtime, ta xây dựng lớp POJO DTO rõ ràng sau. 

*Lưu ý: Mảng `importantWords` được giới hạn tối đa 10 - 15 từ có điểm cao nhất để bảo vệ bộ nhớ RAM hệ thống khỏi nguy cơ tràn bộ đệm (Out Of Memory) đối với các bình luận dài.*

```java
package com.novax.leadora.api.dto.response;

import lombok.*;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysisResultDto implements Serializable {

    private String overallSentiment;      // Cảm xúc tổng quát (Positive, Neutral, Negative)
    private Double overallConfidence;     // Độ tin cậy tổng quát (0.0 - 1.0)
    private List<AspectSentiment> aspects; // Kết quả phân tích cảm xúc từng khía cạnh
    private List<ImportantWord> importantWords; // Giới hạn tối đa Top 10-15 từ khóa từ Integrated Gradients (XAI)

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AspectSentiment implements Serializable {
        private String aspect;            // Tên khía cạnh (Attitude, Speed, Accuracy, Facility, Price)
        private String sentiment;         // Cảm xúc khía cạnh (Positive, Neutral, Negative)
        private Double confidence;        // Độ tin cậy của khía cạnh (0.0 - 1.0)
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportantWord implements Serializable {
        private String word;              // Từ khóa được highlight
        private Double attribution;       // Điểm tác động từ thuật toán Integrated Gradients
    }
}
```

### 3.3. Cấu trúc cập nhật của `SalesFeedbackEntity.java`
Tích hợp các trường AI mới vào thực thể [SalesFeedbackEntity.java](file:///d:/leadora/backend/src/main/java/com/novax/leadora/infrastructure/persistence/entity/SalesFeedbackEntity.java):

```java
package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.api.dto.response.AiAnalysisResultDto; // Import POJO mới
import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.AiStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sales_feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesFeedbackEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "feedback_id")
    private UUID feedbackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private BookingEntity booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_staff_id")
    private UserEntity salesStaff;

    @Column(name = "rating")
    private Short rating;

    @Column(name = "rating_attitude")
    private Short ratingAttitude;

    @Column(name = "rating_speed")
    private Short ratingSpeed;

    @Column(name = "rating_accuracy")
    private Short ratingAccuracy;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    // --- CÁC TRƯỜNG PHỤC VỤ TÍCH HỢP AI ABSA (100% TEXT-BASED) ---

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false, length = 20)
    @Builder.Default
    private AiStatus aiStatus = AiStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_analysis_result", columnDefinition = "jsonb")
    private AiAnalysisResultDto aiAnalysisResult; // Lưu cấu trúc POJO type-safe JSONB

    @Column(name = "is_urgent", nullable = false)
    @Builder.Default
    private Boolean isUrgent = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(name = "ai_processed_at")
    private OffsetDateTime aiProcessedAt;

    @Column(name = "ai_error_message", columnDefinition = "TEXT")
    private String aiErrorMessage;

    // -------------------------------------

    @Column(name = "feedback_token", unique = true, length = 255)
    private String feedbackToken;

    @Column(name = "token_expires_at")
    private OffsetDateTime tokenExpiresAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private UserEntity reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
}
```

---

## 4. KỊCH BẢN SQL MIGRATION (DATABASE SCHEMA UPDATE)

Tên file migration đề xuất: `V2__add_ai_absa_fields_to_sales_feedbacks.sql`

### 4.1. PostgreSQL (Supabase / Production)
*Sử dụng kiểu `JSONB` và tận dụng chỉ mục GIN để tăng tốc độ tìm kiếm các key/value sâu bên trong tài liệu JSON.*

```sql
-- Bước 1: Thêm các cột mới vào bảng sales_feedbacks
ALTER TABLE sales_feedbacks 
ADD COLUMN ai_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
ADD COLUMN ai_analysis_result JSONB,
ADD COLUMN is_urgent BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN risk_level VARCHAR(20),
ADD COLUMN ai_processed_at TIMESTAMPTZ,
ADD COLUMN ai_error_message TEXT;

-- Bước 2: Tạo các chỉ mục (Indexes) để tối ưu hóa truy vấn
-- Index phục vụ dashboard quét các trường hợp khẩn cấp
CREATE INDEX idx_sales_feedbacks_is_urgent ON sales_feedbacks(is_urgent) WHERE is_urgent = TRUE;

-- Index phục vụ background scheduler quét bản ghi cần retry
CREATE INDEX idx_sales_feedbacks_ai_status ON sales_feedbacks(ai_status);

-- GIN Index trên cột JSONB phục vụ việc truy vấn nhanh các Aspect bên trong JSON
CREATE INDEX idx_sales_feedbacks_ai_analysis_res ON sales_feedbacks USING gin (ai_analysis_result);
```

### 4.2. MySQL (Môi trường phát triển cục bộ nếu có)
*Sử dụng kiểu dữ liệu `JSON` tiêu chuẩn.*

```sql
-- Thêm các cột vào bảng sales_feedbacks
ALTER TABLE sales_feedbacks 
ADD COLUMN ai_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
ADD COLUMN ai_analysis_result JSON NULL,
ADD COLUMN is_urgent BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN risk_level VARCHAR(20) NULL,
ADD COLUMN ai_processed_at DATETIME NULL,
ADD COLUMN ai_error_message TEXT NULL;

-- Tạo chỉ mục thông thường
CREATE INDEX idx_sales_feedbacks_is_urgent ON sales_feedbacks(is_urgent);
CREATE INDEX idx_sales_feedbacks_ai_status ON sales_feedbacks(ai_status);
```

---

## 5. LUỒNG DỮ LIỆU & CƠ CHẾ VẬN HÀNH BẤT ĐỒNG BỘ

### 5.1. Cấu hình Thread Pool chuyên biệt cho tác vụ AI
```java
@Bean(name = "aiAnalysisExecutor")
public Executor aiAnalysisExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("ai-absa-");
    executor.initialize();
    return executor;
}
```

### 5.2. Giải pháp Đồng bộ Giao dịch (Transaction Isolation) khi chạy `@Async`
**Vấn đề**: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` chỉ chạy sau khi Transaction chính đã commit và đóng hoàn toàn. Tại thời điểm này, Connection cũ đã trả lại cho Pool. Việc gọi trực tiếp cập nhật Database hoặc lưu thực thể Task mới từ Event Listener sẽ không thể ghi xuống DB hoặc bị lỗi `TransactionRequiredException`.

**Giải pháp**: Tách biệt logic cập nhật Database sang một Service chuyên biệt có annotate `@Transactional(propagation = Propagation.REQUIRES_NEW)` để buộc Spring Boot mở một Transaction và Connection độc lập cho tác vụ này.

#### **A. Lớp Service đặc quyền mở Transaction độc lập (`AiAnalysisService.java`):**
```java
package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.response.AiAnalysisResultDto;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.AiStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.RiskLevel;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final SalesFeedbackRepository feedbackRepository;
    private final TaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW) // Mở connection và transaction độc lập
    public void saveAiAnalysisSuccess(UUID feedbackId, AiAnalysisResultDto result, RiskLevel riskLevel, boolean isUrgent) {
        SalesFeedbackEntity feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found: " + feedbackId));

        feedback.setAiStatus(AiStatus.PROCESSED);
        feedback.setAiAnalysisResult(result);
        feedback.setIsUrgent(isUrgent);
        feedback.setRiskLevel(riskLevel);
        feedback.setAiProcessedAt(OffsetDateTime.now());
        feedback.setAiErrorMessage(null);
        feedbackRepository.save(feedback);

        // Tạo SLA Task khẩn cấp (UC-10.1) ngay trong Transaction này
        if (isUrgent) {
            createUrgentFollowUpTask(feedback);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAiAnalysisFailure(UUID feedbackId, String errorMessage) {
        SalesFeedbackEntity feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found: " + feedbackId));

        feedback.setAiStatus(AiStatus.FAILED);
        feedback.setAiErrorMessage(errorMessage);
        feedbackRepository.save(feedback);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAiStatusToPending(UUID feedbackId) {
        SalesFeedbackEntity feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found: " + feedbackId));

        feedback.setAiStatus(AiStatus.PENDING);
        feedbackRepository.save(feedback);
    }

    private void createUrgentFollowUpTask(SalesFeedbackEntity feedback) {
        UserEntity assignee = feedback.getSalesStaff();
        if (assignee == null) {
            log.warn("No Sales Staff assigned to feedback ID: {}. Task cannot be automatically assigned.", feedback.getFeedbackId());
            return;
        }

        String customerName = feedback.getCustomer() != null ? feedback.getCustomer().getFullName() : "Khách hàng ẩn danh";
        String title = String.format("[AI ALERT] Phản hồi tiêu cực từ %s", customerName);
        
        StringBuilder description = new StringBuilder();
        description.append("Hệ thống AI phát hiện phản hồi tiêu cực từ khách hàng.\n\n");
        description.append(String.format("Nội dung phản hồi: \"%s\"\n\n", feedback.getComment()));
        if (feedback.getAiAnalysisResult() != null) {
            description.append(String.format("Khía cạnh tiêu cực: %s\n", feedback.getAiAnalysisResult().getOverallSentiment()));
        }

        TaskEntity urgentTask = TaskEntity.builder()
                .title(title)
                .description(description.toString())
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.OPEN)
                .activityType(ActivityType.FOLLOW_UP)
                .assignedUser(assignee)
                .customer(feedback.getCustomer())
                .overdueNotified(false)
                .build();

        taskRepository.save(urgentTask);
        log.info("Urgent Follow-up Task created successfully for feedback: {}", feedback.getFeedbackId());
    }
}
```

#### **B. Lớp Transactional Event Listener (`SalesFeedbackAiEventListener.java`):**
```java
package com.novax.leadora.application.usecase.feedback.listener;

import com.novax.leadora.application.usecase.feedback.event.AiAnalysisTriggerEvent;
import com.novax.leadora.application.usecase.feedback.AiAnalysisService;
import com.novax.leadora.infrastructure.integration.ai.AiServiceClient; // Client gọi sang FastAPI
import com.novax.leadora.api.dto.response.AiAnalysisResultDto;
import com.novax.leadora.infrastructure.persistence.entity.enums.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesFeedbackAiEventListener {

    private final AiServiceClient aiServiceClient;
    private final AiAnalysisService aiAnalysisService;

    @Async("aiAnalysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void triggerAiAnalysis(AiAnalysisTriggerEvent event) {
        log.info("Starting Async AI Analysis event for feedback ID: {}", event.getFeedbackId());
        
        try {
            // Gọi HTTP REST request sang Python FastAPI AI Service
            AiAnalysisResultDto result = aiServiceClient.callAbsaModel(event.getComment());
            
            // Xử lý logic quyết định mức độ khẩn cấp (Ví dụ: bất kỳ khía cạnh nào bị Negative đều đánh dấu khẩn cấp)
            boolean isUrgent = result.getAspects().stream()
                    .anyMatch(aspect -> "Negative".equalsIgnoreCase(aspect.getSentiment()));
            
            RiskLevel riskLevel = isUrgent ? RiskLevel.HIGH : RiskLevel.LOW;
            if ("Negative".equalsIgnoreCase(result.getOverallSentiment()) && isUrgent) {
                riskLevel = RiskLevel.CRITICAL;
            }

            // Gọi service với propagation = REQUIRES_NEW để cập nhật DB độc lập
            aiAnalysisService.saveAiAnalysisSuccess(event.getFeedbackId(), result, riskLevel, isUrgent);
            log.info("AI Analysis transaction successfully saved for feedback ID: {}", event.getFeedbackId());

        } catch (Exception e) {
            log.error("Failed to run AI Analysis for feedback ID: {}", event.getFeedbackId(), e);
            aiAnalysisService.saveAiAnalysisFailure(event.getFeedbackId(), e.getMessage());
        }
    }
}
```

---

## 6. KẾ HOẠCH THIẾT LẬP RETRY SCHEDULER
Để giải quyết triệt để lỗi **Race Condition (Double Processing)**: Scheduler khi fetch các bản ghi có trạng thái `FAILED` sẽ thực hiện cập nhật trạng thái của chúng thành `PENDING` (hoặc `PROCESSING`) và **lưu ngay lập tức xuống DB** trước khi đẩy event vào Event Publisher. Việc này đảm bảo nếu lần quét tiếp theo diễn ra khi tác vụ cũ chưa chạy xong, nó sẽ không quét lại các bản ghi đang xử lý này nữa.

```java
package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.application.usecase.feedback.event.AiAnalysisTriggerEvent;
import com.novax.leadora.application.usecase.feedback.AiAnalysisService;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.AiStatus;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisRetryScheduler {

    private final SalesFeedbackRepository feedbackRepository;
    private final AiAnalysisService aiAnalysisService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 */10 * * * *") // Chạy mỗi 10 phút một lần
    @Transactional(readOnly = true)
    public void retryFailedAiAnalysis() {
        log.info("Starting retry job for failed AI feedback analysis...");
        
        List<SalesFeedbackEntity> failedFeedbacks = feedbackRepository
            .findByAiStatusAndSubmittedAtAfter(
                AiStatus.FAILED, 
                OffsetDateTime.now().minusDays(1)
            );

        for (SalesFeedbackEntity feedback : failedFeedbacks) {
            log.info("Retriggering AI analysis. Transitioning state to PENDING and saving to DB for ID: {}", feedback.getFeedbackId());
            
            // Bước 1: Khắc phục Race Condition - Cập nhật trạng thái thành PENDING ngay lập tức trong Transaction độc lập
            try {
                aiAnalysisService.updateAiStatusToPending(feedback.getFeedbackId());
                
                // Bước 2: Sau khi trạng thái đã PENDING trong DB, publish event để xử lý Async
                eventPublisher.publishEvent(new AiAnalysisTriggerEvent(feedback.getFeedbackId(), feedback.getComment()));
            } catch (Exception e) {
                log.error("Failed to transition status and publish trigger event for feedback ID: {}", feedback.getFeedbackId(), e);
            }
        }
    }
}
```

---

## 6. QUẢN TRỊ HIỆU NĂNG AI & CHỐNG OVERFITTING TRONG CƠ SỞ DỮ LIỆU
Để đảm bảo kết quả AI lưu vào trường `JSONB` trong DB thực sự chất lượng và không bị sai lệch (overfitted) do tập train VLSP nhỏ, mô hình cần được cấu hình các biện pháp chống quá khớp:
1. **Layer Freezing:** Chỉ fine-tune các tầng cuối và classifier heads để bảo toàn tính tổng quát hóa của XLM-RoBERTa.
2. **Confidence Thresholding:** Áp dụng ngưỡng chặn độ tin cậy động ở tầng suy luận (Inference) để đảm bảo không lưu trữ các kết quả dự đoán có độ tin cậy thấp.
3. **Regularization:** Thiết lập Weight Decay cao (`0.05` - `0.1`) và Dropout (`0.3`) để giảm thiểu sai số khi phân tích các từ ngữ chưa xuất hiện trong tập train gốc.
