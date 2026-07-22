package com.novax.leadora.application.usecase.chat;

/**
 * Canned assistant replies for guardrail outcomes, in the language of the user's question.
 *
 * <p>The assistant answers in whichever language it was asked in, so these messages — which are
 * returned <em>instead of</em> an LLM reply and therefore never pass through the system prompt's
 * language rule — must carry their own translations. The {@code vietnamese} flag is resolved by
 * {@code IntentClassifier.resolveVietnamese(...)}, which inherits the session's language when a
 * turn is too short to judge on its own.
 *
 * <p>MSG-30 ({@code NO_DATA}) and MSG-31 ({@code SYSTEM_ERROR}) are the code constants for the
 * corresponding entries in the standard message catalogue.
 */
public final class GuardrailMessages {

    private GuardrailMessages() {
    }

    /** BR-35 — read-only assistant; action-oriented requests are refused. */
    public static final String MUTATION_REFUSAL_EN =
            "I'm a read-only assistant — I can only look up and summarise data. "
                    + "I can't create, edit, delete, send, approve or confirm any records. "
                    + "Please perform this action directly on the relevant screen.";

    public static final String MUTATION_REFUSAL_VI =
            "Tôi là trợ lý chỉ đọc — tôi chỉ có thể tra cứu và tổng hợp dữ liệu. "
                    + "Tôi không thể tạo, sửa, xoá, gửi, duyệt hay xác nhận bất kỳ bản ghi nào. "
                    + "Bạn vui lòng thực hiện thao tác này trực tiếp trên màn hình tương ứng.";

    /** Out-of-scope (non-business) questions. */
    public static final String OFF_TOPIC_REFUSAL_EN =
            "I'm Leadora's internal assistant and can only help with sales/CRM data and company "
                    + "documents. This question is outside my scope.";

    public static final String OFF_TOPIC_REFUSAL_VI =
            "Tôi là trợ lý nội bộ của Leadora, chỉ hỗ trợ về dữ liệu kinh doanh/CRM và tài liệu "
                    + "công ty. Câu hỏi này nằm ngoài phạm vi của tôi.";

    /** MSG-30 — no authorized data matched the request. */
    public static final String NO_DATA_EN =
            "No authorized information was found for your request.";

    public static final String NO_DATA_VI =
            "Không tìm thấy thông tin nào bạn được phép xem cho yêu cầu này.";

    /** MSG-31 — generic system error (e.g. the LLM is unreachable / out of quota). */
    public static final String SYSTEM_ERROR_EN =
            "An unexpected error occurred. Please try again later.";

    public static final String SYSTEM_ERROR_VI =
            "Đã xảy ra lỗi không mong muốn. Vui lòng thử lại sau.";

    /** The AI provider rejected the request for quota / rate-limit reasons (e.g. Gemini HTTP 429). */
    public static final String QUOTA_EXCEEDED_EN =
            "The AI assistant has run out of queries (Gemini quota / rate limit reached). "
                    + "Please try again in a few minutes, or check the API key's quota.";

    public static final String QUOTA_EXCEEDED_VI =
            "Trợ lý AI đã hết lượt truy vấn (đã chạm giới hạn quota/tần suất của Gemini). "
                    + "Vui lòng thử lại sau vài phút, hoặc kiểm tra hạn mức của API key.";

    /** The AI provider is unreachable or misconfigured (bad/missing API key, auth error, timeout). */
    public static final String AI_UNAVAILABLE_EN =
            "Could not reach the AI service (the API key or configuration may be invalid). "
                    + "Please try again later or contact an administrator.";

    public static final String AI_UNAVAILABLE_VI =
            "Không thể kết nối tới dịch vụ AI (API key hoặc cấu hình có thể không hợp lệ). "
                    + "Vui lòng thử lại sau hoặc liên hệ quản trị viên.";

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
