package com.novax.leadora.application.usecase.chat;

import java.util.Locale;

/**
 * Turns a low-level AI/LLM failure into a user-facing message that distinguishes the two cases a
 * developer keeps confusing at runtime: "out of tokens/quota" vs "a real error".
 *
 * <p>Spring AI wraps the provider's HTTP error, so the useful signal (status code, Google's
 * {@code RESOURCE_EXHAUSTED} / {@code PERMISSION_DENIED} status, "quota", "API key") is buried in
 * the exception chain's messages. We walk the whole cause chain, concatenate the messages, and
 * pattern-match. Order matters: quota is checked before the generic auth bucket.
 */
final class AiErrorClassifier {

    private AiErrorClassifier() {
    }

    enum Kind {
        /** HTTP 429 / RESOURCE_EXHAUSTED — the free-tier quota or rate limit was hit. */
        QUOTA,
        /** Bad/missing API key, 401/403, or the provider was unreachable (timeout / connection). */
        UNAVAILABLE,
        /** Anything else — treat as a genuine bug. */
        GENERIC
    }

    /** Maps the throwable to a localized, actionable message. */
    static String userMessage(Throwable error, boolean vietnamese) {
        return switch (classify(error)) {
            case QUOTA -> GuardrailMessages.quotaExceeded(vietnamese);
            case UNAVAILABLE -> GuardrailMessages.aiUnavailable(vietnamese);
            case GENERIC -> GuardrailMessages.systemError(vietnamese);
        };
    }

    static Kind classify(Throwable error) {
        String signal = collectMessages(error);

        // Quota / rate limit first — Gemini free tier returns 429 RESOURCE_EXHAUSTED.
        if (containsAny(signal, "429", "resource_exhausted", "resource exhausted",
                "quota", "rate limit", "ratelimit", "too many requests", "exceeded your current quota")) {
            return Kind.QUOTA;
        }

        // Auth / config / connectivity — the assistant is effectively unavailable.
        if (containsAny(signal, "401", "403", "unauthenticated", "unauthorized", "permission_denied",
                "permission denied", "api key", "api_key", "invalid key", "invalid_api_key",
                "connection refused", "timed out", "timeout", "unknownhost", "unable to connect")) {
            return Kind.UNAVAILABLE;
        }

        return Kind.GENERIC;
    }

    /** Concatenates the messages of the whole cause chain (guarded against cyclic causes). */
    private static String collectMessages(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable current = error;
        int guard = 0;
        while (current != null && guard++ < 12) {
            sb.append(current.getClass().getSimpleName()).append(": ");
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(' ');
            }
            if (current.getCause() == current) {
                break; // self-referential cause
            }
            current = current.getCause();
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
