# KẾ HOẠCH DỰ ÁN & BẢN THIẾT KẾ KỸ THUẬT: MÔ HÌNH AI PHÂN TÍCH KHÍA CẠNH & CẢM XÚC SONG NGỮ (TEXT-BASED ABSA & XAI)

Tài liệu này tổng hợp toàn bộ báo cáo phân tích, kiến trúc thuật toán học sâu phân tích cảm xúc khía cạnh (Text-Based ABSA) và kế hoạch triển khai tích hợp vào hệ thống **Leadora CRM** phục vụ đồ án tốt nghiệp của nhóm **NovaX**.

---

## 1. KHẢ NĂNG "WOW" VÀ TÍNH KHẢ QUAN CỦA ĐỒ ÁN (F1-SCORE: 85% - 89%)

*   **Tính thực tiễn đột phá**: Tự động bóc tách cảm xúc của khách hàng theo từng khía cạnh dịch vụ cụ thể (Thái độ nhân viên sales, tốc độ làm việc, độ chính xác thông tin, giá cả, cơ sở vật chất). Khắc phục tình trạng khách hàng chấm điểm đại khái nhưng bình luận chi tiết.
*   **Độ chính xác khả quan**:
    *   *Mô hình Text (XLM-RoBERTa-base)*: Đạt F1-score từ **85% - 89%** trên cả tiếng Anh và tiếng Việt nhờ kiến trúc Transformer đa ngôn ngữ được tinh chỉnh (fine-tuned) trên tập dữ liệu chuyên biệt.
*   **Điểm cộng trước Hội đồng AI (Tính giải thích được - XAI)**: Áp dụng thuật toán giải thích mô hình **Integrated Gradients** (sử dụng thư viện Captum trong PyTorch) để tính toán độ quan trọng của từng từ ngữ (attribution scores). Từ đó trực quan hóa (highlight) các từ khóa mang tính quyết định đến cảm xúc tiêu cực hoặc tích cực của khách hàng trực tiếp trên UI của Manager.

> [!NOTE]
> **BIỆN LUẬN KHOA HỌC VỀ MÂU THUẪN NHÃN (FORM 3 CỘT VS AI DỰ ĐOÁN 5 KHÍA CẠNH):**
> 
> Biểu mẫu đánh giá định lượng (Rating Form) của Leadora CRM chỉ thu thập 3 khía cạnh (*Attitude*, *Speed*, *Accuracy*) nhằm tinh giản giao diện, tối ưu hóa trải nghiệm người dùng (UX) và tối đa hóa tỷ lệ phản hồi (Response Rate). 
> Tuy nhiên, văn bản phản hồi tự do (Unstructured Text) lại chứa đựng hàm lượng thông tin đa chiều vượt qua câu hỏi số học. Khách hàng thường chủ động khen ngợi hoặc phàn nàn về **Giá cả (Price)** ("chi phí phát sinh", "đắt đỏ") và **Cơ sở vật chất (Facility)** ("phòng họp ồn", "thiết bị lỗi").
> 
> Việc huấn luyện AI dự đoán 5 khía cạnh thay vì 3 giúp doanh nghiệp:
> 1. **Khám phá khiếu nại ẩn**: Tự động bóc tách các vấn đề về phòng ốc/cơ sở vật chất (*Facility*) hoặc bất mãn tài chính (*Price*) nằm sâu trong câu bình luận thô của khách hàng.
> 2. **Giải thích nguyên nhân biến động điểm số**: Ví dụ, nếu khách hàng chấm điểm *Accuracy* 1 sao nhưng comment viết *"Báo giá không rõ ràng, chi phí quá đắt"*, AI sẽ bóc tách khía cạnh *Price* mang nhãn *Negative*, giúp Manager hiểu rõ nguyên nhân thực sự là do giá cả chứ không phải lỗi nghiệp vụ nhân viên.

---

## 2. KIẾN TRÚC THUẬT TOÁN HỌC SÂU (PYTORCH MULTI-TASK NET)

Sơ đồ mô hình nạp văn bản (Text comment) thông qua XLM-RoBERTa và phân loại đa nhiệm (Multi-task Classification) để dự đoán đồng thời cảm xúc của nhiều khía cạnh dịch vụ:

```
                            ┌────────────────────────┐
                            │  Customer Feedback     │
                            │  (Văn bản bình luận)   │
                            └───────────┬────────────┘
                                        │
                                        ▼
                            ┌────────────────────────┐
                            │    TOKENIZATION &      │
                            │   PADDING/TRUNCATION   │
                            └───────────┬────────────┘
                                        │
                                        ▼
                            ┌────────────────────────┐
                            │   XLM-RoBERTa Encoder  │
                            │   (Trích xuất đặc trưng)│
                            └───────────┬────────────┘
                                        │ (Token Embeddings: [Batch, Seq_Len, 768])
                                        ▼
                            ┌────────────────────────┐
                            │      Mean Pooling      │
                            │  (Bỏ qua padding token)│
                            └───────────┬────────────┘
                                        │ (Sentence Representation: [Batch, 768])
                                        ▼
                            ┌────────────────────────┐
                            │    Projection Layer    │
                            │    (Linear + Dropout)  │
                            └───────────┬────────────┘
                                        ├───────────────────────┬───────────────────────┐
                                        ▼                       ▼                       ▼
                            ┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐
                            │    Attitude Classifier│ │     Speed Classifier  │ │   Accuracy Classifier │
                            │ (Negative/Neutral/Pos)│ │ (Negative/Neutral/Pos)│ │ (Negative/Neutral/Pos)│
                            └───────────────────────┘ └───────────────────────┘ └───────────────────────┘
```

### Mã nguồn kiến trúc mạng Neural (PyTorch):

```python
import torch
import torch.nn as nn
from transformers import AutoModel

class TextBasedFeedbackABSA(nn.Module):
    def __init__(self, model_name="xlm-roberta-base", num_classes=3):
        """
        Mô hình ABSA đa nhiệm (Multi-task) phân loại cảm xúc 3 nhãn:
        Negative (0), Neutral (1), Positive (2).
        """
        super(TextBasedFeedbackABSA, self).__init__()
        
        # 1. Khởi tạo mô hình XLM-RoBERTa dùng chung làm Encoder (Cased Model)
        self.encoder = AutoModel.from_pretrained(model_name)
        self.hidden_dim = self.encoder.config.hidden_size # 768
        
        # 2. Tầng Projection giảm thiểu quá khớp (Overfitting)
        self.dropout = nn.Dropout(0.3)
        
        # 3. Các đầu phân loại đa nhiệm (Multi-task Classification Heads)
        self.head_attitude = nn.Linear(self.hidden_dim, num_classes)
        self.head_speed = nn.Linear(self.hidden_dim, num_classes)
        self.head_accuracy = nn.Linear(self.hidden_dim, num_classes)
        self.head_facility = nn.Linear(self.hidden_dim, num_classes)
        self.head_price = nn.Linear(self.hidden_dim, num_classes)

    def mean_pooling(self, model_output, attention_mask):
        """
        Thực hiện Mean Pooling trên các token embeddings của câu, bỏ qua các padding tokens
        nhằm tránh mất mát thông tin ngữ cảnh so với việc chỉ lấy token đặc biệt <s> ở index 0.
        """
        token_embeddings = model_output.last_hidden_state # Shape: [batch_size, seq_len, 768]
        # Mở rộng attention_mask sang kích thước của token_embeddings
        input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float() # Shape: [batch_size, seq_len, 768]
        # Nhân chập embeddings với mask để triệt tiêu giá trị các padding token (bằng 0)
        sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, 1) # Shape: [batch_size, 768]
        # Tính tổng số token thực tế trong câu, giới hạn min là 1e-9 để tránh chia cho 0
        sum_mask = torch.clamp(input_mask_expanded.sum(1), min=1e-9) # Shape: [batch_size, 768]
        return sum_embeddings / sum_mask
        
    def forward(self, input_ids, attention_mask):
        # Trích xuất vector đặc trưng từ văn bản thông qua XLM-RoBERTa
        outputs = self.encoder(input_ids=input_ids, attention_mask=attention_mask)
        
        # Thực hiện Mean Pooling trên toàn bộ câu thay thế cho outputs.last_hidden_state[:, 0, :]
        pooled_representation = self.mean_pooling(outputs, attention_mask) # Shape: [Batch_Size, 768]
        pooled_representation = self.dropout(pooled_representation)
        
        # Dự đoán cảm xúc song song cho từng khía cạnh
        return {
            "attitude": self.head_attitude(pooled_representation),
            "speed": self.head_speed(pooled_representation),
            "accuracy": self.head_accuracy(pooled_representation),
            "facility": self.head_facility(pooled_representation),
            "price": self.head_price(pooled_representation)
        }
```

---

## 3. THAY ĐỔI VỀ PHÍA DATABASE & CODEBASE TÍCH HỢP

### A. Kiểm tra hệ thống hiện tại
*   Bảng `sales_feedbacks` chỉ lưu điểm số số học (`rating`, `rating_attitude`, `rating_speed`, `rating_accuracy`) và bình luận thô văn bản (`comment` kiểu `TEXT`).
*   Hệ thống chạy 100% bằng Text, không thu thập và xử lý hình ảnh từ phía khách hàng.

### B. Phương án nâng cấp Database (Đồng bộ với thiết kế tích hợp)
Bổ sung các trường AI cần thiết vào bảng `sales_feedbacks` thông qua thực thể [SalesFeedbackEntity.java](file:///d:/leadora/backend/src/main/java/com/novax/leadora/infrastructure/persistence/entity/SalesFeedbackEntity.java) bằng script migration:
1.  `ai_status` (`VARCHAR`): Quản lý trạng thái xử lý AI (`PENDING`, `PROCESSED`, `FAILED`).
2.  `ai_analysis_result` (`JSONB`): Lưu trữ cấu trúc JSON kết quả phân tích ABSA và chỉ số XAI (sử dụng POJO `AiAnalysisResultDto` rõ ràng).
3.  `is_urgent` (`BOOLEAN`): Đánh dấu khẩn cấp khi phát hiện bình luận tiêu cực để kích hoạt luồng hỗ trợ ngay lập tức.
4.  `risk_level` (`VARCHAR`): Lưu cấp độ rủi ro phục vụ truy vấn nhanh trên dashboard quản lý.
5.  `ai_processed_at` (`TIMESTAMPTZ`): Lưu mốc thời gian hoàn tất phân tích.
6.  `ai_error_message` (`TEXT`): Lưu trữ vết lỗi khi gọi API AI bị thất bại phục vụ cơ chế retry scheduler.

### C. Cơ chế tích hợp Asynchronous (Bất đồng bộ)
Để đảm bảo API nhận feedback của khách hàng không bị chậm trễ (Latency):
*   Khi khách hàng gửi form, Spring Boot backend lưu dữ liệu thô ngay lập tức vào database và trả về thành công cho khách hàng.
*   Spring Boot kích hoạt một sự kiện chạy nền (**Spring Event `@Async`** trên thread pool `aiAnalysisExecutor`) để gửi nội dung `comment` sang Python FastAPI AI Service.
*   Sau khi nhận kết quả từ FastAPI, Spring Boot cập nhật lại bản ghi feedback đó trong DB và tự động tạo một SLA Task (`UC-10.1`) cho bộ phận chăm sóc khách hàng hoặc sales staff phụ trách nếu AI phát hiện lỗi dịch vụ nghiêm trọng (`is_urgent = true`).

---

## 4. KẾ HOẠCH TRIỂN KHAI 5 GIAI ĐOẠN CHI TIẾT

*   **Giai đoạn 1: Chuẩn bị & Gán nhãn Dữ liệu (Tuần 1 - 2)**: Thu thập khoảng 3,000 - 5,000 mẫu nhận xét song ngữ Anh - Việt thuộc các miền nhà hàng, khách sạn, dịch vụ tài chính. Sử dụng công cụ **Doccano** để gán nhãn đa thuộc tính ABSA cho văn bản theo đúng định dạng JSON đầu ra.
*   **Giai đoạn 2: Phát triển & Huấn luyện Mô hình AI trên Kaggle (Tuần 3 - 5)**:
    *   Xây dựng mạng học sâu phân tích đa nhiệm PyTorch bằng GPU miễn phí của Kaggle.
    *   **CHỈ DẪN HUẤN LUYỆN QUAN TRỌNG (LOSS FUNCTION & NaN LOSS PREVENTION & OVERFITTING PREVENTION)**: 
        1. Để giải quyết bài toán khuyết nhãn (Missing Labels) khi một câu review không đề cập đến một khía cạnh cụ thể, nhóm nghiên cứu gán giá trị `-100` cho khía cạnh khuyết thiếu và sử dụng hàm mất mát `nn.CrossEntropyLoss(ignore_index=-100)` trong PyTorch.
        2. **Ngăn chặn lỗi NaN Loss**: Khi 100% mẫu dữ liệu trong một Batch đều khuyết nhãn cho một khía cạnh nào đó, hàm Cross Entropy Loss của khía cạnh đó sẽ trả về giá trị `NaN`. Khi cộng dồn vào tổng loss, nó sẽ làm nhiễm `NaN` toàn bộ mô hình và phá hỏng quá trình học. Nhóm bắt buộc phải cài đặt bộ lọc kiểm tra:
           ```python
           if not torch.isnan(head_loss):
               total_loss += head_loss
               active_heads += 1
           # Tính loss trung bình có trọng số:
           final_loss = total_loss / max(active_heads, 1)
           ```
        3. **Chống Overfitting trên dataset VLSP cố định**: Để đảm bảo mô hình có khả năng tổng quát hóa tốt nhất, nhóm bắt buộc phải áp dụng các kỹ thuật:
           - **Layer Freezing:** Đóng băng 6 hoặc 8 tầng đầu tiên của mô hình XLM-RoBERTa, chỉ cập nhật trọng số ở các tầng cuối và classifier heads để bảo toàn các tri thức ngôn ngữ chung và giảm số tham số học vẹt.
           - **L2 Regularization:** Cấu hình `weight_decay = 0.05` đến `0.1` trong Optimizer AdamW để làm mịn ranh giới quyết định.
           - **Early Stopping:** Cài đặt cơ chế tự động dừng sớm nếu Validation Macro F1 không tăng liên tiếp trong 3 epoch.
           - **Stratified Data Splitting:** Chia tập dữ liệu Train/Val theo tỷ lệ `80/20` phân tầng theo nhãn để đảm bảo phân bổ nhãn đồng đều giữa hai tập.
    *   Áp dụng kỹ thuật giải thích mô hình **Integrated Gradients** (thư viện Captum) để tính toán điểm trọng tâm (word attribution). Xuất model ra định dạng file `.pth` hoặc `.onnx`.
*   **Giai đoạn 3: Xây dựng AI Microservice bằng FastAPI (Tuần 6 - 7)**:
    *   Đóng gói mô hình và viết các FastAPI endpoint nhận văn bản đầu vào, trả về JSON kết quả phân tích ABSA và danh sách các từ kèm độ quan trọng đối với quyết định phân loại cảm xúc.
    *   **RÀNG BUỘC HIỆU NĂNG VÀ BỘ NHỚ (MEMORY & STORAGE LIMITATION)**: Để ngăn chặn tình trạng quá tải bộ nhớ RAM ở Java Backend và phình to dung lượng lưu trữ `JSONB` trong Database đối với những bình luận cực dài (lên tới hàng trăm từ), FastAPI Microservice tuyệt đối **KHÔNG** trả về điểm số Attribution của toàn bộ các token trong câu. FastAPI phải thực hiện lọc (Filter) và chỉ trả về mảng chứa **tối đa Top 10 hoặc Top 15 từ khóa có giá trị tuyệt đối của điểm Attribution cao nhất** (mang tính quyết định cảm xúc mạnh mẽ nhất).
    *   **LÀM SẠCH KÝ TỰ SENTENCEPIECE (CLEAN STRING CONTRACT)**: Vì XLM-RoBERTa sử dụng SentencePiece Tokenizer, nên các token trả về sẽ có ký tự prefix đại diện cho khoảng trắng (` ` hoặc `_`). FastAPI microservice bắt buộc phải gọi `.replace(" ", "").replace(" ", "")` để làm sạch chuỗi từ khóa trước khi trả về JSON cho Java Backend.
*   **Giai đoạn 4: Tích hợp vào Backend Spring Boot & DB (Tuần 8 - 10)**: Cấu hình API Spring Boot, chạy script migration DB, thiết lập cơ chế gọi API bất đồng bộ và tự động tạo Task khẩn cấp (`UC-10.1`) dựa trên kết quả phân tích tiêu cực từ AI.
*   **Giai đoạn 5: Frontend UI & Hoàn thiện Báo cáo (Tuần 11 - 12)**: Vẽ biểu đồ Radar phân tích chất lượng dịch vụ trên Next.js Dashboard, hiển thị đoạn text được highlight các màu sắc khác nhau biểu thị mức độ tác động của từ khóa (đỏ cho từ tiêu cực, xanh cho từ tích cực) trực quan cho Manager và viết chương nghiên cứu AI vào báo cáo tốt nghiệp.
