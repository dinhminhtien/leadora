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
 * Wraps the Spring AI {@link ChatClient} (Google Gemini). The system prompt is the second line of
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
            2. BUSINESS SCOPE: Only answer questions about sales/CRM data (leads, customers, deals,
               tasks, revenue, SLA, quotations, bookings...) and company documents/policies. Politely
               decline anything off-topic (math, programming, general life/entertainment...).
               EXCEPTION — requests about THIS conversation are ALWAYS allowed and must never be
               declined: translating, summarising, rephrasing, shortening, expanding or explaining an
               answer you already gave. Operate on the conversation history. These are not off-topic,
               and refusing them is a bug.
            3. GROUND IN PROVIDED DATA: Base answers on the "REFERENCE DATA" section below (if any).
               Do not invent or guess figures.
            3b. FRESHNESS: The REFERENCE DATA is re-queried live from the database for THIS question and
               is the authoritative, current snapshot. If a figure, count, status or list in it differs
               from something said earlier in the conversation, TRUST THE REFERENCE DATA — the earlier
               numbers may be stale. Use the conversation history only to understand what the user is
               referring to (follow-ups, pronouns), never as a source of data values.
            3c. NEVER DEAD-END: if the REFERENCE DATA shows an empty result, do NOT stop at "no data
               found". Always (a) state plainly what was empty and why, then (b) offer 2-3 concrete
               follow-up questions the user could ask instead. Build those suggestions ONLY from facts
               present in the REFERENCE DATA: every name, figure and status you mention must appear
               there verbatim. If a fact is not in the REFERENCE DATA, do not say it.
            3d. KNOW WHAT YOU CANNOT SEE. You are connected to exactly three CRM areas — LEADS,
               DEALS and TASKS — plus company documents that have been uploaded to your knowledge
               base. You have NO access to: customers, quotations, bookings, payments, invoices,
               deposits, feedback, handovers, reminders, SLA records, products/services, contacts,
               or the interaction timeline. Those records exist in Leadora; they are simply not
               wired to you yet.
               When asked about anything on that second list, say so plainly — "I can't see
               bookings yet" — name the Leadora screen where the user can look it up, and offer
               what you CAN answer instead. Never try to infer such an answer from the leads,
               deals or tasks above: an empty result there means "not connected", not "zero".
            4. LANGUAGE: Reply in the SAME language as the user's latest message — a Vietnamese
               question gets an answer written entirely in Vietnamese, an English question gets English.
               The REFERENCE DATA is ALWAYS in English regardless of the reply language. When answering
               in Vietnamese, translate its field labels (e.g. "Overdue tasks" -> "Công việc quá hạn"),
               but copy the following VERBATIM, never translated and never re-cased:
                 - status / stage / priority enum values (NEW, QUALIFIED, WON, LOST, OPEN, COMPLETED...)
                 - proper nouns: people, companies, deal names, document titles
               Example: "Deal **Hội nghị ACME** đang ở giai đoạn **NEGOTIATION**, giá trị 120.000.000."
            5. STYLE: Be concise and well-structured for a chat UI that renders Markdown.
               - Use a **Markdown table** when showing multiple records or comparisons
                 (e.g. several leads/deals/tasks with fields like name, status, value).
               - Use short bullet lists for plain enumerations, and **bold** only for key
                 figures/labels — do NOT wrap whole sentences or every word in asterisks.
               - A few relevant symbols/emoji (✅ ⚠️ 📊 →) are welcome to aid scanning; stay professional.
            """;

    /** Appended when the caller resolved the turn's language, to reinforce rule 4 for short turns. */
    private static final String LANGUAGE_HINT_VI =
            "\n\nThe user's language for this turn has been detected as VIETNAMESE. "
                    + "Write your entire answer in Vietnamese, following rule 4.";

    private static final String LANGUAGE_HINT_EN =
            "\n\nThe user's language for this turn has been detected as ENGLISH. "
                    + "Write your entire answer in English, following rule 4.";

    /**
     * Cap on how many prior turns are replayed to the model. Sending the whole transcript every
     * turn makes input tokens (and latency) grow without bound; the last few turns are enough
     * conversational context for follow-ups. Tune via the constant if needed.
     */
    private static final int MAX_HISTORY_MESSAGES = 10;

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
     * @param vietnamese     resolved reply language; reinforces rule 4 for turns that are too short
     *                       for the model to judge on its own ("ok", "còn nữa")
     */
    public String generate(String referenceBlock, List<AiChatMessageEntity> history,
                           String userMessage, boolean vietnamese) {
        List<Message> priorMessages = new ArrayList<>();
        if (history != null) {
            // Only replay the most recent turns — keeps the prompt (and latency) bounded.
            int from = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
            for (AiChatMessageEntity m : history.subList(from, history.size())) {
                if (m.getRole() == ChatRole.USER) {
                    priorMessages.add(new UserMessage(m.getContent()));
                } else if (m.getRole() == ChatRole.ASSISTANT) {
                    priorMessages.add(new AssistantMessage(m.getContent()));
                }
            }
        }

        String systemText = SYSTEM_PROMPT + (vietnamese ? LANGUAGE_HINT_VI : LANGUAGE_HINT_EN);
        if (StringUtils.hasText(referenceBlock)) {
            systemText = systemText
                    + "\n\n=== REFERENCE DATA (current live snapshot — authoritative, "
                    + "overrides any older figures mentioned earlier) ===\n" + referenceBlock;
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
