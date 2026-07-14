package com.novax.leadora.application.usecase.chat;

/**
 * Canned assistant replies for guardrail outcomes. The assistant UI is
 * English-only, so every message is English regardless of the caller's
 * language flag (kept for signature compatibility).
 */
public final class GuardrailMessages {

    private GuardrailMessages() {
    }

    /** BR-35 — read-only assistant; action-oriented requests are refused. */
    public static final String MUTATION_REFUSAL_EN =
            "I'm a read-only assistant — I can only look up and summarise data. "
                    + "I can't create, edit, delete, send, approve or confirm any records. "
                    + "Please perform this action directly on the relevant screen.";

    /** Out-of-scope (non-business) questions. */
    public static final String OFF_TOPIC_REFUSAL_EN =
            "I'm Leadora's internal assistant and can only help with sales/CRM data and company "
                    + "documents. This question is outside my scope.";

    /** MSG-30 — no authorized data matched the request. */
    public static final String NO_DATA_EN =
            "No authorized information was found for your request.";

    /** MSG-31 — generic system error (e.g. the LLM is unreachable / out of quota). */
    public static final String SYSTEM_ERROR_EN =
            "An unexpected error occurred. Please try again later.";

    /** The AI provider rejected the request for quota / rate-limit reasons (e.g. Gemini HTTP 429). */
    public static final String QUOTA_EXCEEDED_EN =
            "The AI assistant has run out of queries (Gemini quota / rate limit reached). "
                    + "Please try again in a few minutes, or check the API key's quota.";

    /** The AI provider is unreachable or misconfigured (bad/missing API key, auth error, timeout). */
    public static final String AI_UNAVAILABLE_EN =
            "Could not reach the AI service (the API key or configuration may be invalid). "
                    + "Please try again later or contact an administrator.";

    public static String mutationRefusal(boolean vietnamese) {
        return MUTATION_REFUSAL_EN;
    }

    public static String offTopicRefusal(boolean vietnamese) {
        return OFF_TOPIC_REFUSAL_EN;
    }

    public static String noData(boolean vietnamese) {
        return NO_DATA_EN;
    }

    public static String systemError(boolean vietnamese) {
        return SYSTEM_ERROR_EN;
    }

    public static String quotaExceeded(boolean vietnamese) {
        return QUOTA_EXCEEDED_EN;
    }

    public static String aiUnavailable(boolean vietnamese) {
        return AI_UNAVAILABLE_EN;
    }
}
