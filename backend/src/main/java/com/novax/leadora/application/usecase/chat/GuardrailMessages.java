package com.novax.leadora.application.usecase.chat;

/** Canned assistant replies for guardrail outcomes. MSG-30 / MSG-31 are used verbatim per spec. */
public final class GuardrailMessages {

    private GuardrailMessages() {
    }

    /** BR-35 — read-only assistant; action-oriented requests are refused. */
    public static final String MUTATION_REFUSAL =
            "Trợ lý chỉ có quyền tra cứu và tổng hợp dữ liệu (chỉ đọc). "
                    + "Tôi không thể tạo, sửa, xóa, gửi, duyệt hay xác nhận bất kỳ dữ liệu nào. "
                    + "Vui lòng thực hiện thao tác này trực tiếp trên màn hình nghiệp vụ tương ứng.";

    /** Out-of-scope (non-business) questions. */
    public static final String OFF_TOPIC_REFUSAL =
            "Tôi là trợ lý nội bộ của Leadora, chỉ hỗ trợ các câu hỏi liên quan đến dữ liệu "
                    + "bán hàng/CRM và tài liệu công ty. Câu hỏi này nằm ngoài phạm vi hỗ trợ của tôi.";

    /** MSG-30 — no authorized data matched the request. */
    public static final String NO_DATA = "No authorized information was found for your request.";

    /** MSG-31 — generic system error (e.g. the LLM is unreachable). */
    public static final String SYSTEM_ERROR = "An unexpected error occurred. Please try again later.";
}
