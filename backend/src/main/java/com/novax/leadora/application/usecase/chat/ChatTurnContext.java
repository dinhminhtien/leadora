package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatMessageResponse;

import java.util.List;

/**
 * Everything the rest of a chat turn needs, read and committed in one short transaction.
 *
 * <p>Deliberately free of entities: once this is returned the transaction is closed, and anything
 * still attached to the persistence context would throw the moment it was touched.
 *
 * @param userMessage the just-persisted user turn, already mapped for the response
 * @param history     prior turns, oldest first, capped to what the prompt replays
 * @param lastIntent  {@code intentMatched} of the previous assistant turn, for follow-up handling
 */
public record ChatTurnContext(ChatMessageResponse userMessage, List<ChatTurn> history,
                              String lastIntent) {

    public List<String> priorUserMessages() {
        return history.stream().filter(ChatTurn::isUser).map(ChatTurn::content).toList();
    }
}
