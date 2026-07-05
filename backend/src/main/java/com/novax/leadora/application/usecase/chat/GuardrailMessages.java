package com.novax.leadora.application.usecase.chat;

/** Canned assistant replies for guardrail outcomes. MSG-30 / MSG-31 are used verbatim per spec. */
public final class GuardrailMessages {

    private GuardrailMessages() {
    }

    /** BR-35 — read-only assistant; action-oriented requests are refused. */
    public static final String MUTATION_REFUSAL_VI =
            "Trợ lý chỉ có quyền tra cứu và tổng hợp dữ liệu (chỉ đọc). "
                    + "Tôi không thể tạo, sửa, xóa, gửi, duyệt hay xác nhận bất kỳ dữ liệu nào. "
                    + "Vui lòng thực hiện thao tác này trực tiếp trên màn hình nghiệp vụ tương ứng.";

    public static final String MUTATION_REFUSAL_EN =
            "I'm a read-only assistant — I can only look up and summarise data. "
                    + "I can't create, edit, delete, send, approve or confirm any records. "
                    + "Please perform this action directly on the relevant screen.";

    /** Out-of-scope (non-business) questions. */
    public static final String OFF_TOPIC_REFUSAL_VI =
            "Tôi là trợ lý nội bộ của Leadora, chỉ hỗ trợ các câu hỏi liên quan đến dữ liệu "
                    + "bán hàng/CRM và tài liệu công ty. Câu hỏi này nằm ngoài phạm vi hỗ trợ của tôi.";

    public static final String OFF_TOPIC_REFUSAL_EN =
            "I'm Leadora's internal assistant and can only help with sales/CRM data and company "
                    + "documents. This question is outside my scope.";

    /** MSG-30 — no authorized data matched the request. */
    public static final String NO_DATA_VI =
            "Không tìm thấy thông tin nào bạn được phép truy cập cho yêu cầu này.";
    public static final String NO_DATA_EN =
            "No authorized information was found for your request.";

    /** MSG-31 — generic system error (e.g. the LLM is unreachable / out of quota). */
    public static final String SYSTEM_ERROR_VI =
            "Đã xảy ra lỗi không mong muốn. Vui lòng thử lại sau.";
    public static final String SYSTEM_ERROR_EN =
            "An unexpected error occurred. Please try again later.";

    /**
     * The AI provider rejected the request for quota / rate-limit reasons — most commonly the
     * Gemini free tier returning HTTP 429 RESOURCE_EXHAUSTED. Distinct from a generic error so the
     * user (and dev) can tell "out of tokens" apart from a real bug.
     */
    public static final String QUOTA_EXCEEDED_VI =
            "Trợ lý AI hiện đã hết lượt truy vấn (chạm giới hạn quota/tần suất của Gemini). "
                    + "Vui lòng thử lại sau ít phút, hoặc kiểm tra hạn mức của API key.";
    public static final String QUOTA_EXCEEDED_EN =
            "The AI assistant has run out of queries (Gemini quota / rate limit reached). "
                    + "Please try again in a few minutes, or check the API key's quota.";

    /** The AI provider is unreachable or misconfigured (bad/missing API key, auth error, timeout). */
    public static final String AI_UNAVAILABLE_VI =
            "Không kết nối được dịch vụ AI (API key hoặc cấu hình có thể chưa đúng). "
                    + "Vui lòng thử lại sau hoặc báo quản trị viên.";
    public static final String AI_UNAVAILABLE_EN =
            "Could not reach the AI service (the API key or configuration may be invalid). "
                    + "Please try again later or contact an administrator.";

    public static String mutationRefusal(boolean vietnamese) {
        return vietnamese ? MUTATION_REFUSAL_VI : MUTATION_REFUSAL_EN;
    }

    public static String offTopicRefusal(boolean vietnamese) {
        return vietnamese ? OFF_TOPIC_REFUSAL_VI : OFF_TOPIC_REFUSAL_EN;
    }

    public static String noData(boolean vietnamese) {
        return vietnamese ? NO_DATA_VI : NO_DATA_EN;
    }

    public static String systemError(boolean vietnamese) {
        return vietnamese ? SYSTEM_ERROR_VI : SYSTEM_ERROR_EN;
    }

    public static String quotaExceeded(boolean vietnamese) {
        return vietnamese ? QUOTA_EXCEEDED_VI : QUOTA_EXCEEDED_EN;
    }

    public static String aiUnavailable(boolean vietnamese) {
        return vietnamese ? AI_UNAVAILABLE_VI : AI_UNAVAILABLE_EN;
    }
}
