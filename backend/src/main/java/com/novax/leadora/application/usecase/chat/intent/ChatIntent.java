package com.novax.leadora.application.usecase.chat.intent;

/**
 * Classified intent of a single user chat turn. Drives the hybrid pipeline:
 * blocked intents short-circuit before any LLM call; the rest decide which
 * CRM context / RAG data is prefetched and stuffed into the prompt.
 */
public enum ChatIntent {

    /** Action-oriented request (create/update/delete/send/approve...). Refused per BR-35. */
    MUTATION_BLOCKED,

    /** Not related to sales/CRM/company knowledge. Politely refused. */
    OFF_TOPIC,

    /**
     * A request that operates on THIS conversation rather than on CRM data: translate,
     * summarise, rephrase, shorten or explain an answer the assistant already gave.
     *
     * <p>Needs no fresh data — the conversation history is the whole input — so it skips both
     * the CRM queries and the RAG lookup, making it the cheapest non-blocked intent.
     * Only reachable once the session has a prior turn to operate on.
     */
    META_CONVERSATION,

    /** Question about the current user's own assigned leads/deals/tasks. */
    ASSIGNED_DATA,

    /** Question about team-wide aggregates / summaries. */
    TEAM_SUMMARY,

    /** Question answerable from ingested company documents (policies, handbooks...). */
    DOC_QUERY,

    /** A business question that isn't clearly one of the above — answered with RAG + light CRM context. */
    GENERAL_BUSINESS
}
