package com.novax.leadora.application.usecase.chat.intent;

import java.util.EnumSet;
import java.util.Set;

/**
 * A CRM subject area the assistant can report on.
 *
 * <p>Used to keep the prompt proportionate to the question. Counts and totals for every area are
 * cheap — one line each — so the snapshot always includes them and the assistant can answer "how
 * many bookings?" whatever the question was about. Row-by-row listings are not cheap: a handful of
 * areas listed together runs to thousands of tokens, which costs money on every turn, slows the
 * model's prefill, and buries the rows that actually matter among ones that do not.
 *
 * <p>So listings are emitted only for the areas the question mentions. When it mentions none —
 * "how am I doing?" — {@link #defaults()} keeps the assistant's original behaviour.
 */
public enum CrmArea {

    LEADS,
    DEALS,
    TASKS,
    QUOTATIONS,
    BOOKINGS,
    PAYMENTS,
    CUSTOMERS;

    /** Areas listed when a question names none: the day-to-day sales pipeline. */
    public static Set<CrmArea> defaults() {
        return EnumSet.of(LEADS, DEALS, TASKS);
    }
}
