package com.novax.leadora.api.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Result of one user turn: the persisted user message and the assistant's reply.
 * {@code blocked} is true when the guardrail refused the request (mutation / off-topic)
 * and therefore no LLM call was made.
 */
@Data
@Builder
public class SendMessageResponse {

    private ChatMessageResponse userMessage;
    private ChatMessageResponse assistantMessage;
    private String intent;
    private boolean blocked;
}
