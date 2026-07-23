package com.novax.leadora.application.usecase.chat.intent;

import java.util.EnumSet;
import java.util.Set;

/**
 * A CRM subject area the assistant can report on, and where the user can see all of it.
 *
 * <p>Used to keep the prompt proportionate to the question. Counts and totals for every area are
 * cheap — one line each — so the snapshot always includes them and the assistant can answer "how
 * many bookings?" whatever the question was about. Row-by-row listings are not cheap: a handful of
 * areas listed together runs to thousands of tokens, which costs money on every turn, slows the
 * model's prefill, and buries the rows that actually matter among ones that do not.
 *
 * <p>So listings are emitted only for the areas the question mentions. When it mentions none —
 * "how am I doing?" — {@link #defaults()} keeps the assistant's original behaviour.
 *
 * <p>The screen label and path let the assistant hand a long list off to the UI instead of trying
 * to render it in chat. They are supplied to the model as data rather than left to its memory:
 * a link it invents is worse than no link, since it looks authoritative and 404s. These must stay
 * in step with the frontend's {@code ROUTE_PATHS} and its sidebar labels.
 */
public enum CrmArea {

    LEADS("Leads", "/leads"),
    DEALS("Deals List", "/deals"),
    TASKS("Tasks", "/manage-follow-up-tasks"),
    QUOTATIONS("Quotations", "/quotations"),
    BOOKINGS("Bookings", "/booking-confirmation"),
    PAYMENTS("Payments", "/deposit-payment"),
    CUSTOMERS("Customer Profiles", "/customer-profiles");

    private final String screenLabel;
    private final String screenPath;

    CrmArea(String screenLabel, String screenPath) {
        this.screenLabel = screenLabel;
        this.screenPath = screenPath;
    }

    /** Sidebar label of the screen that shows this area in full. */
    public String screenLabel() {
        return screenLabel;
    }

    /** Frontend route of that screen, e.g. {@code /leads}. */
    public String screenPath() {
        return screenPath;
    }

    /** Areas listed when a question names none: the day-to-day sales pipeline. */
    public static Set<CrmArea> defaults() {
        return EnumSet.of(LEADS, DEALS, TASKS);
    }
}
