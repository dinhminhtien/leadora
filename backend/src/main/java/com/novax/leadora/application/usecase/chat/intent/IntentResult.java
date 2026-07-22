package com.novax.leadora.application.usecase.chat.intent;

import java.util.Set;

/**
 * Outcome of intent classification. When {@code blocked} is true the guardrail
 * supplies a ready-to-return {@code blockMessage} and the assistant makes no LLM call.
 *
 * @param areas subject areas the question refers to. Every area still contributes its counts to
 *              the snapshot; these are the ones that also get a row-by-row listing, so the prompt
 *              stays proportionate to what was actually asked.
 */
public record IntentResult(ChatIntent intent, boolean blocked, String blockMessage,
                           Set<CrmArea> areas) {

    public static IntentResult of(ChatIntent intent, Set<CrmArea> areas) {
        return new IntentResult(intent, false, null, areas);
    }

    /** For intents that need no CRM listing at all (greetings, meta-requests). */
    public static IntentResult of(ChatIntent intent) {
        return new IntentResult(intent, false, null, CrmArea.defaults());
    }

    public static IntentResult blocked(ChatIntent intent, String message) {
        return new IntentResult(intent, true, message, Set.of());
    }
}
