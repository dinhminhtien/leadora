package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatMessageResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.AiChatMessageEntity;
import com.novax.leadora.infrastructure.persistence.entity.AiChatSessionEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatRole;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatSessionStatus;
import com.novax.leadora.infrastructure.persistence.repository.AiChatMessageRepository;
import com.novax.leadora.infrastructure.persistence.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The two short transactions that bracket a chat turn: read-and-record before generation, persist
 * the reply after.
 *
 * <p><b>Why this is a separate bean.</b> The whole turn used to run inside one transaction, which
 * meant a database connection stayed checked out for as long as the model took to answer — three
 * to eight seconds. With a pool of five, five concurrent conversations exhausted it and every
 * other request in the application queued behind them, including ones that never touch chat.
 * Splitting the work moves the connection hold from the length of an LLM call down to a few
 * milliseconds at each end.
 *
 * <p>It has to be a different bean from the caller: {@code @Transactional} is applied by a proxy,
 * and a method calling another method on {@code this} bypasses it — the annotation would be
 * silently ignored and the split would exist only in the source.
 */
@Service
@RequiredArgsConstructor
public class ChatTurnWriter {

    private static final int TITLE_MAX = 60;

    /**
     * How many prior messages to load for the prompt. Matches the cap {@code ChatLlmService}
     * applies when replaying turns, so nothing is fetched only to be discarded.
     */
    private static final int PROMPT_HISTORY_LIMIT = 10;

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;

    /**
     * Loads the session, auto-titles it on the first turn, records the user's message, and returns
     * the conversation state the rest of the turn needs — all detached.
     */
    @Transactional
    public ChatTurnContext begin(UUID sessionId, ChatActor actor, String content) {
        AiChatSessionEntity session = loadOwnedActiveSession(sessionId, actor);

        List<AiChatMessageEntity> recent = new ArrayList<>(messageRepository.findRecentForPrompt(
                sessionId, ChatRole.USER, PageRequest.of(0, PROMPT_HISTORY_LIMIT)));
        Collections.reverse(recent);

        boolean firstTurn = recent.isEmpty();
        if (firstTurn && (!StringUtils.hasText(session.getTitle())
                || "New conversation".equals(session.getTitle()))) {
            session.setTitle(truncate(content, TITLE_MAX));
        }

        AiChatMessageEntity userMessage = save(session, ChatRole.USER, content, null);

        String lastIntent = recent.stream()
                .filter(m -> m.getRole() == ChatRole.ASSISTANT && m.getIntentMatched() != null)
                .reduce((first, second) -> second) // last assistant turn
                .map(AiChatMessageEntity::getIntentMatched)
                .orElse(null);

        List<ChatTurn> history = recent.stream()
                .map(m -> new ChatTurn(m.getRole(), m.getContent()))
                .toList();

        return new ChatTurnContext(ChatMessageResponse.from(userMessage), history, lastIntent);
    }

    /** Records the assistant's reply and floats the session to the top of the list. */
    @Transactional
    public ChatMessageResponse complete(UUID sessionId, String reply, String intentMatched) {
        AiChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session", sessionId));
        AiChatMessageEntity message = save(session, ChatRole.ASSISTANT, reply, intentMatched);
        sessionRepository.save(session); // bumps updatedAt
        return ChatMessageResponse.from(message);
    }

    private AiChatSessionEntity loadOwnedActiveSession(UUID sessionId, ChatActor actor) {
        AiChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session", sessionId));
        if (session.getStatus() == ChatSessionStatus.DELETED
                || !session.getUser().getUserId().equals(actor.userId())) {
            // Not found rather than forbidden: confirming a session exists would leak that
            // somebody else owns it.
            throw new ResourceNotFoundException("Chat session", sessionId);
        }
        return session;
    }

    private AiChatMessageEntity save(AiChatSessionEntity session, ChatRole role,
                                     String content, String intentMatched) {
        return messageRepository.save(AiChatMessageEntity.builder()
                .session(session)
                .role(role)
                .content(content)
                .intentMatched(intentMatched)
                .build());
    }

    private String truncate(String s, int max) {
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
