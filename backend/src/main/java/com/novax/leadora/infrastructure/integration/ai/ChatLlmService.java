package com.novax.leadora.infrastructure.integration.ai;

import com.novax.leadora.infrastructure.persistence.entity.AiChatMessageEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the Spring AI {@link ChatClient} (Ollama). The system prompt is the second line of
 * defence behind {@code IntentClassifier}: it re-states the read-only and business-only policy
 * and forbids inventing data not present in the supplied reference block (BR-35/BR-36).
 */
@Slf4j
@Service
public class ChatLlmService {

    private static final String SYSTEM_PROMPT = """
            Bạn là "Trợ lý nội bộ Leadora" — trợ lý cho nhân viên kinh doanh của một CRM ngành khách sạn.

            NGUYÊN TẮC BẮT BUỘC:
            1. CHỈ ĐỌC: Tuyệt đối KHÔNG tạo, sửa, xóa, gửi, duyệt, từ chối, xác nhận hay thực hiện bất kỳ
               hành động thay đổi dữ liệu nào. Nếu người dùng yêu cầu, hãy từ chối lịch sự và gợi ý họ thao tác
               trực tiếp trên màn hình nghiệp vụ tương ứng.
            2. CHỈ NGHIỆP VỤ: Chỉ trả lời câu hỏi liên quan đến dữ liệu bán hàng/CRM (lead, khách hàng, deal,
               công việc, doanh số, SLA, báo giá, booking...) và tài liệu/chính sách của công ty. Nếu câu hỏi
               nằm ngoài phạm vi (toán học, lập trình, đời sống, giải trí...), hãy từ chối lịch sự.
            3. CHỈ DÙNG DỮ LIỆU ĐƯỢC CUNG CẤP: Chỉ dựa vào phần "DỮ LIỆU THAM CHIẾU" bên dưới (nếu có).
               Không suy đoán hay bịa số liệu. Nếu không có dữ liệu phù hợp, hãy nói rõ là không tìm thấy
               thông tin được phép truy cập cho yêu cầu này.
            4. Trả lời NGẮN GỌN, bằng tiếng Việt, có thể dùng gạch đầu dòng hoặc **in đậm** khi phù hợp.
            """;

    private final ChatClient chatClient;

    public ChatLlmService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Generates an assistant reply.
     *
     * @param referenceBlock prefetched CRM/RAG context (may be empty)
     * @param history        prior turns of this session, oldest first (excludes the current turn)
     * @param userMessage    the current user turn
     */
    public String generate(String referenceBlock, List<AiChatMessageEntity> history, String userMessage) {
        List<Message> priorMessages = new ArrayList<>();
        if (history != null) {
            for (AiChatMessageEntity m : history) {
                if (m.getRole() == ChatRole.USER) {
                    priorMessages.add(new UserMessage(m.getContent()));
                } else if (m.getRole() == ChatRole.ASSISTANT) {
                    priorMessages.add(new AssistantMessage(m.getContent()));
                }
            }
        }

        String systemText = SYSTEM_PROMPT;
        if (StringUtils.hasText(referenceBlock)) {
            systemText = systemText + "\n\n=== DỮ LIỆU THAM CHIẾU (chỉ dùng phần này) ===\n" + referenceBlock;
        } else {
            systemText = systemText + "\n\n(Không có dữ liệu tham chiếu nào được truy xuất cho yêu cầu này.)";
        }

        return chatClient.prompt()
                .system(systemText)
                .messages(priorMessages)
                .user(userMessage)
                .call()
                .content();
    }
}
