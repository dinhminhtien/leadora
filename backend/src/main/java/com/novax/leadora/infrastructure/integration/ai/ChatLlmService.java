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
            You are "Leadora Internal Assistant", a helper for sales staff of a hospitality CRM.

            CORE RULES (always follow):
            1. READ-ONLY: Never create, edit, delete, send, approve, reject, confirm, or perform any
               data-changing action. If asked to, politely decline and suggest doing it on the relevant
               screen. You only look up and summarise data.
            2. BUSINESS SCOPE ONLY: Only answer questions about sales/CRM data (leads, customers, deals,
               tasks, revenue, SLA, quotations, bookings...) and company documents/policies. Politely
               decline anything off-topic (math, programming, general life/entertainment...).
            3. GROUND IN PROVIDED DATA: Base answers only on the "REFERENCE DATA" section below (if any)
               and the conversation so far. Do not invent or guess figures. If no relevant data is
               available, say clearly that you found no information you are allowed to access.
            4. LANGUAGE: Reply in the SAME language as the user's latest message — Vietnamese if they
               write Vietnamese, English if they write English.
            5. STYLE: Be concise. Use bullet points or **bold** when it helps readability.
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
            systemText = systemText + "\n\n=== REFERENCE DATA (use only this) ===\n" + referenceBlock;
        } else {
            systemText = systemText + "\n\n(No reference data was retrieved for this request.)";
        }

        String content = chatClient.prompt()
                .system(systemText)
                .messages(priorMessages)
                .user(userMessage)
                .call()
                .content();

        return stripReasoning(content);
    }

    /**
     * Removes the chain-of-thought block that reasoning models (e.g. qwen3) emit, so the user
     * only sees the final answer. Harmless for models that don't produce {@code <think>} tags.
     */
    static String stripReasoning(String content) {
        if (content == null) {
            return "";
        }
        String cleaned = content.replaceAll("(?is)<think>.*?</think>", "").trim();
        // Defensive: an unclosed <think> (truncated output) — drop everything up to the tag.
        int open = cleaned.toLowerCase().indexOf("<think>");
        if (open >= 0) {
            cleaned = cleaned.substring(0, open).trim();
        }
        return cleaned;
    }
}
