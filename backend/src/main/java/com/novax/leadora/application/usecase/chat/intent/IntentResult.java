package com.novax.leadora.application.usecase.chat.intent;

/**
 * Outcome of intent classification. When {@code blocked} is true the guardrail
 * supplies a ready-to-return {@code blockMessage} and the assistant makes no LLM call.
 *
 * <p>Which CRM areas to detail is deliberately NOT part of this: like the reply language, it is
 * resolved across the session rather than from one message, so it is computed by the caller via
 * {@link IntentClassifier#resolveAreas}.
 */
public record IntentResult(ChatIntent intent, boolean blocked, String blockMessage) {

    public static IntentResult of(ChatIntent intent) {
        return new IntentResult(intent, false, null);
    }

    public static IntentResult blocked(ChatIntent intent, String message) {
        return new IntentResult(intent, true, message);
    }
}
