package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatMessageResponse;
import com.novax.leadora.api.dto.response.SendMessageResponse;
import com.novax.leadora.application.usecase.chat.intent.ChatIntent;
import com.novax.leadora.application.usecase.chat.intent.IntentClassifier;
import com.novax.leadora.application.usecase.chat.intent.IntentResult;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.integration.ai.ChatLlmService;
import com.novax.leadora.infrastructure.integration.ai.RagService;
import com.novax.leadora.infrastructure.persistence.entity.AiChatMessageEntity;
import com.novax.leadora.infrastructure.persistence.entity.AiChatSessionEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatRole;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatSessionStatus;
import com.novax.leadora.infrastructure.persistence.repository.AiChatMessageRepository;
import com.novax.leadora.infrastructure.persistence.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * UC — Query Assigned Sales CRM Data / Query Team Sales CRM Summary via the chat assistant.
 *
 * <p>Implements the hybrid pipeline:
 * <pre>
 *   classify intent → (block mutation/off-topic) → prefetch scoped CRM/RAG context → LLM → persist
 * </pre>
 *
 * <p>Note: the LLM call happens inside the transaction for simplicity. A local model can be slow,
 * so under high concurrency this would hold a DB connection — acceptable for the current MVP /
 * single-tenant dev setup; revisit (split read/generate/write transactions) when scaling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendChatMessageUseCase {

    private static final int TITLE_MAX = 60;

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final IntentClassifier intentClassifier;
    private final CrmContextService crmContextService;
    private final RagService ragService;
    private final ChatLlmService chatLlmService;

    @Transactional
    public SendMessageResponse execute(UUID sessionId, UserEntity user, String content) {
        AiChatSessionEntity session = loadOwnedActiveSession(sessionId, user);

        // History BEFORE this turn (for the LLM's conversational context).
        List<AiChatMessageEntity> history =
                messageRepository.findBySession_SessionIdOrderByCreatedAtAsc(sessionId);

        // Auto-title the session from the first user message.
        boolean firstTurn = history.isEmpty();
        if (firstTurn && (!StringUtils.hasText(session.getTitle())
                || "Cuộc trò chuyện mới".equals(session.getTitle()))) {
            session.setTitle(truncate(content, TITLE_MAX));
        }

        // Persist the user turn.
        AiChatMessageEntity userMessage = saveMessage(session, ChatRole.USER, content, null);

        // [1] Guardrail (context-aware: inherits the prior turn's data scope for follow-ups).
        String lastIntentName = history.stream()
                .filter(m -> m.getRole() == ChatRole.ASSISTANT && m.getIntentMatched() != null)
                .reduce((first, second) -> second) // last assistant turn
                .map(AiChatMessageEntity::getIntentMatched)
                .orElse(null);
        IntentResult intent = intentClassifier.classify(content, lastIntentName);
        if (intent.blocked()) {
            AiChatMessageEntity blockedReply =
                    saveMessage(session, ChatRole.ASSISTANT, intent.blockMessage(), intent.intent().name());
            touch(session);
            return buildResponse(userMessage, blockedReply, intent.intent(), true);
        }

        // [2] Prefetch scoped context.
        String referenceBlock = buildReferenceBlock(intent.intent(), user, content);

        // [3] Generate (best-effort: a failed/unavailable LLM degrades to a friendly message,
        // in the same language as the question).
        boolean vi = IntentClassifier.isVietnamese(content);
        String reply;
        try {
            reply = chatLlmService.generate(referenceBlock, history, content);
            if (!StringUtils.hasText(reply)) {
                reply = GuardrailMessages.noData(vi);
            }
        } catch (Exception ex) {
            log.error("LLM generation failed for session {}: {}", sessionId, ex.getMessage(), ex);
            reply = GuardrailMessages.systemError(vi);
        }

        AiChatMessageEntity assistantMessage =
                saveMessage(session, ChatRole.ASSISTANT, reply, intent.intent().name());
        touch(session);
        return buildResponse(userMessage, assistantMessage, intent.intent(), false);
    }

    private String buildReferenceBlock(ChatIntent intent, UserEntity user, String content) {
        return switch (intent) {
            case ASSIGNED_DATA -> crmContextService.assignedContext(user);
            case TEAM_SUMMARY -> crmContextService.teamSummary();
            case DOC_QUERY -> ragService.retrieveContext(content);
            case GENERAL_BUSINESS -> {
                // Light blend: company docs + the user's own data.
                String rag = ragService.retrieveContext(content);
                String assigned = crmContextService.assignedContext(user);
                yield (StringUtils.hasText(rag) ? rag + "\n" : "") + assigned;
            }
            default -> "";
        };
    }

    private AiChatSessionEntity loadOwnedActiveSession(UUID sessionId, UserEntity user) {
        AiChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session", sessionId));
        if (session.getStatus() == ChatSessionStatus.DELETED
                || !session.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Chat session", sessionId);
        }
        return session;
    }

    private AiChatMessageEntity saveMessage(AiChatSessionEntity session, ChatRole role,
                                            String content, String intentMatched) {
        AiChatMessageEntity message = AiChatMessageEntity.builder()
                .session(session)
                .role(role)
                .content(content)
                .intentMatched(intentMatched)
                .build();
        return messageRepository.save(message);
    }

    /** Bumps the session's updatedAt so it floats to the top of the session list. */
    private void touch(AiChatSessionEntity session) {
        sessionRepository.save(session);
    }

    private SendMessageResponse buildResponse(AiChatMessageEntity userMessage,
                                              AiChatMessageEntity assistantMessage,
                                              ChatIntent intent, boolean blocked) {
        return SendMessageResponse.builder()
                .userMessage(ChatMessageResponse.from(userMessage))
                .assistantMessage(ChatMessageResponse.from(assistantMessage))
                .intent(intent.name())
                .blocked(blocked)
                .build();
    }

    private String truncate(String s, int max) {
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
