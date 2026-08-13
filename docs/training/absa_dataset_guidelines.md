# HƯỚNG DẪN CHUẨN BỊ DATASET HUẤN LUYỆN MÔ HÌNH AI ABSA
## (Dataset Preparation Guidelines for Aspect-Based Sentiment Analysis)

Tài liệu này hướng dẫn nguồn dữ liệu (datasets) song ngữ Anh - Việt chất lượng cao và quy trình tiền xử lý, gán nhãn dữ liệu phục vụ cho việc huấn luyện mô hình XLM-RoBERTa phân tích cảm xúc khía cạnh (ABSA) trong hệ thống Leadora CRM.

---

## 1. CÁC NGUỒN DATASET CHUẨN HÓA (RECOMMENDED DATASETS)

Để huấn luyện mô hình **XLM-RoBERTa** (Multilingual) hoạt động tốt trên cả tiếng Anh và tiếng Việt trong lĩnh vực CRM/Dịch vụ khách hàng, nhóm NovaX nên kết hợp các bộ dữ liệu sau:

### 1.1. Bộ dữ liệu Tiếng Việt (Vietnamese Datasets)
1.  **VLSP 2018 ABSA Dataset (Khuyên dùng làm Baseline)**:
    *   **Lĩnh vực**: Khách sạn (Hotel) & Nhà hàng (Restaurant) - cực kỳ tương đồng với các nghiệp vụ CRM/Sales trong Leadora.
    *   **Nội dung**: Các bình luận của khách hàng được gán nhãn chi tiết theo thực thể-thuộc tính (Entity-Attribute) và phân cực cảm xúc (Sentiment Polarity).
        *   *Ví dụ nhãn*: `HOTEL#SERVICE_QUALITY` (Thái độ nhân viên, tốc độ phục vụ) hoặc `HOTEL#CLEANLINESS` (Vệ sinh phòng ốc).
        *   *Cảm xúc*: `Positive`, `Negative`, `Neutral`.
    *   **Nguồn tải**: Có thể tìm và tải từ cộng đồng nghiên cứu AI Việt Nam hoặc qua kho GitHub [ds4v/absa-vlsp-2018](https://github.com/ds4v/absa-vlsp-2018).
2.  **ViTASA Dataset**:
    *   **Lĩnh vực**: Thiết bị di động, Nhà hàng, Khách sạn.
    *   **Nội dung**: Bộ dữ liệu TASA (Targeted Aspect-Based Sentiment Analysis) quy mô lớn với hơn 500,000 cặp target-aspect được thu thập từ các bình luận mạng xã hội thực tế. Rất phù hợp nếu muốn huấn luyện mô hình phát hiện chính xác đối tượng được nhắc đến.

### 1.2. Bộ dữ liệu Tiếng Anh (English Datasets)
1.  **SemEval-2016 Task 5 (Bộ dữ liệu ABSA Tiêu chuẩn Thế giới)**:
    *   **Lĩnh vực**: Restaurant, Hotel, Laptops.
    *   **Nội dung**: Dữ liệu chuẩn mực cao do hội đồng SemEval cung cấp. Rất thích hợp để huấn luyện mô hình đa ngôn ngữ nhận diện các khía cạnh dịch vụ bằng tiếng Anh.
2.  **M-ABSA (Multilingual ABSA Dataset)**:
    *   **Lĩnh vực**: Nhà hàng, Khách sạn, Du lịch.
    *   **Nội dung**: Bộ dữ liệu ABSA song hành trên 21 ngôn ngữ khác nhau (bao gồm cả tiếng Việt và tiếng Anh). Giúp mô hình XLM-RoBERTa tối ưu hóa việc chuyển giao tri thức liên ngôn ngữ (Cross-lingual Transfer Learning).

---

## 2. ÁNH XẠ KHÍA CẠNH (ASPECT MAPPING) VÀO DỮ LIỆU LEADORA CRM

Để mô hình AI tương thích tốt với cấu trúc điểm đánh giá hiện tại của bảng `sales_feedbacks` (gồm các cột `rating_attitude`, `rating_speed`, `rating_accuracy`), chúng ta cần ánh xạ các nhãn của dataset VLSP 2018 / SemEval về các khía cạnh nghiệp vụ của Leadora:

| Khía cạnh trong Leadora | Ánh xạ nhãn tương ứng (VLSP 2018) | Ví dụ bình luận minh họa |
| :--- | :--- | :--- |
| **Attitude** (Thái độ nhân viên) | `SERVICE#ATTITUDE` hoặc `STAFF#BEHAVIOR` | "Nhân viên tư vấn nhiệt tình nhưng hơi thiếu chuyên nghiệp." |
| **Speed** (Tốc độ làm việc/phục vụ) | `SERVICE#SPEED` hoặc `PROCESS#TIME` | "Thời gian duyệt hồ sơ ký hợp đồng quá chậm." |
| **Accuracy** (Độ chính xác thông tin) | `SERVICE#ACCURACY` hoặc `INFORMATION#TRUTH` | "Tư vấn báo giá một đằng, lúc làm hợp đồng lại ghi một nẻo." |
| **Facility** (Cơ sở vật chất/Công cụ) | `FACILITY#DESIGN` hoặc `ROOM#QUALITY` | "Phòng tiếp khách chật chội và nóng nực." |
| **Price** (Giá cả/Chi phí) | `SERVICE#PRICE` hoặc `VALUE#MONEY` | "Chi phí dịch vụ hơi đắt so với chất lượng nhận được." |

---

## 3. QUY TRÌNH TIỀN XỬ LÝ DỮ LIỆU & GÁN NHÃN (DATA PIPELINE)

```
┌─────────────────┐      ┌────────────────────┐      ┌───────────────────┐
│ Thu thập dữ liệu│ ───> │  Tiền xử lý Text   │ ───> │ Gán nhãn ABSA     │
│ (Raw Comments)  │      │ (Sử dụng Underthe) │      │ (Sử dụng Doccano) │
└─────────────────┘      └────────────────────┘      └───────────────────┘
                                                               │
                                                               ▼
                                                     ┌───────────────────┐
                                                     │ Định dạng đầu ra  │
                                                     │ (JSON/HuggingFace)│
                                                     └───────────────────┘
```

### Giai đoạn 1: Tiền xử lý dữ liệu Tiếng Việt (Text Preprocessing)
Tiếng Việt có đặc trưng ghép từ và đại từ nhân xưng phức tạp. Cần sử dụng thư viện **Underthe** hoặc **CocCocTokenizer** để:
1.  **Word Segmentation (Tách từ)**: Chuyển `"nhân viên phục vụ"` thành `"nhân_viên phục_vụ"`.
2.  **Normalize Text**: Chuẩn hóa Telex, viết tắt (vd: `kh` -> `khách hàng`, `nv` -> `nhân viên`, `ko` -> `không`), và loại bỏ emoji không cần thiết.
3.  **LƯU Ý QUAN TRỌNG VỀ LOWERCASE**: **KHÔNG** thực hiện Lowercasing (chuyển chữ thường) đối với dữ liệu đầu vào. Do mô hình XLM-RoBERTa sử dụng là biến thể **CASED** (phân biệt chữ hoa/thường), việc Lowercasing sẽ làm mất đi các thông tin ngữ nghĩa quan trọng (nhận diện thực thể, viết tắt chuyên ngành như VIP, SLA, tên riêng người/địa danh), gây suy giảm hiệu năng đáng kể (F1-score).

### Giai đoạn 2: Gán nhãn dữ liệu bổ sung với Doccano (Data Labeling)
Nếu nhóm muốn gán nhãn thêm dữ liệu thực tế thu thập được từ hệ thống CRM Leadora:
*   Cài đặt công cụ gán nhãn mã nguồn mở **Doccano** (`pip install doccano`).
*   Tạo project dạng **Sequence Labeling** hoặc **Document Classification** đa nhãn.
*   Quy ước gán nhãn theo định dạng: `[Từ khóa khía cạnh] -> [Tên khía cạnh] -> [Cảm xúc]`.
    *   *Ví dụ*: Trong câu *"Thái độ nhân viên rất tốt nhưng giá cả hơi đắt"*:
        *   `Thái độ nhân viên` -> Khía cạnh: `Attitude`, Cảm xúc: `Positive`
        *   `giá cả` -> Khía cạnh: `Price`, Cảm xúc: `Negative`

---

## 4. ĐỊNH DẠNG ĐẦU RA DATASET TRƯỚC KHI TRAIN
Dữ liệu trước khi đưa vào mô hình PyTorch/HuggingFace nên được format dưới dạng JSON như sau để dễ dàng viết DataLoader:

```json
[
  {
    "id": 1001,
    "text": "Nhân viên tư vấn siêu nhiệt tình, tuy nhiên làm thủ tục bàn giao còn hơi lâu.",
    "aspects": [
      {
        "category": "Attitude",
        "sentiment": "Positive",
        "text_segment": "Nhân viên tư vấn siêu nhiệt tình"
      },
      {
        "category": "Speed",
        "sentiment": "Negative",
        "text_segment": "làm thủ tục bàn giao còn hơi lâu"
      }
    ]
  }
]
```

### Công cụ Train đề xuất:
*   **Hugging Face Transformers**: Sử dụng lớp `XLMRobertaForSequenceClassification` hoặc xây dựng Custom Head trong PyTorch để phân loại đa nhiệm (Multi-task Classification - dự đoán đồng thời nhiều khía cạnh cảm xúc).
*   **Môi trường train**: Google Colab Pro hoặc **Kaggle Notebooks** (được cung cấp 30 giờ GPU T4 x2 miễn phí mỗi tuần).

---

## 5. PHƯƠNG PHÁP HUẤN LUYỆN TRÁNH OVERFITTING VỚI DATASET SẴN CÓ
Để đạt độ chính xác thực tế tốt nhất (~82% - 85% Macro F1) trên tập dữ liệu VLSP 2018 hữu hạn mà không bị quá khớp (Overfitting), quy trình huấn luyện trên Kaggle bắt buộc áp dụng các kỹ thuật sau:
1.  **Layer Freezing (Đóng băng Encoder):** Đóng băng 6 hoặc 8 tầng đầu tiên của mô hình XLM-RoBERTa (ngăn không cho cập nhật trọng số), chỉ cập nhật 4 tầng cuối và Classifier Heads. Giúp giảm thiểu số tham số học vẹt và bảo toàn tri thức ngôn ngữ nền tảng.
2.  **L2 Regularization (Weight Decay):** Cấu hình `weight_decay = 0.05` đến `0.1` trong Optimizer AdamW để phạt các trọng số có giá trị lớn, làm mịn ranh giới quyết định.
3.  **Early Stopping:** Cài đặt cơ chế tự động dừng sớm nếu Validation Macro F1 không tăng liên tiếp trong 3 epoch.
4.  **Stratified Data Splitting:** Chia tập dữ liệu Train/Val theo tỷ lệ `80/20` phân tầng theo nhãn để đảm bảo phân bổ nhãn đồng đều giữa hai tập.
