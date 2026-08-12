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
    CUSTOMERS("Customer Profiles", "/customer-profiles"),
    // Appended rather than slotted next to DEALS (which it hangs off) so the order of the
    // existing sections in the reference block does not shift.
    SLA("SLA Control", "/sla"),
    // Not a record area like the others: it carries no per-user rows and nothing in
    // ChatCounts, so its section builds its own figures. Listed here anyway because the
    // question-to-area matching is what decides whether it is worth including at all.
    ROOM_AVAILABILITY("Room Availability", "/room-availability", false);

    private final String screenLabel;
    private final String screenPath;
    private final boolean countedPerUser;

    CrmArea(String screenLabel, String screenPath) {
        this(screenLabel, screenPath, true);
    }

    CrmArea(String screenLabel, String screenPath, boolean countedPerUser) {
        this.screenLabel = screenLabel;
        this.screenPath = screenPath;
        this.countedPerUser = countedPerUser;
    }

    /**
     * Whether this area has per-user rows counted by the batched aggregate.
     *
     * <p>Almost everything here is a record with an assignee, so its counts are scoped and
     * batched into one statement. Room allotment is not: it is reference data owned by the
     * hotel, identical for every user, and its section builds its own figures from a bounded
     * forward window.
     *
     * <p>Declared here rather than special-cased in the test that guards the aggregate, so the
     * next area added has to state which kind it is instead of quietly slipping past a check
     * that would otherwise have caught it reporting zero forever.
     */
    public boolean countedPerUser() {
        return countedPerUser;
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
