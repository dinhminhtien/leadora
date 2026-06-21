package com.novax.leadora.application.usecase.chat.intent;

/**
 * Outcome of intent classification. When {@code blocked} is true the guardrail
 * supplies a ready-to-return {@code blockMessage} and the assistant makes no LLM call.
 */
public record IntentResult(ChatIntent intent, boolean blocked, String blockMessage) {

    public static IntentResult of(ChatIntent intent) {
        return new IntentResult(intent, false, null);
    }

    public static IntentResult blocked(ChatIntent intent, String message) {
        return new IntentResult(intent, true, message);
    }
}
