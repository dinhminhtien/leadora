package com.novax.leadora.application.usecase.quotation;

/**
 * What became of a quotation, once the question is settled.
 *
 * <p>Shared by UC-23.1 (sales performance), UC-23.5 (quotation outcome) and the rep scorecard. It
 * lives on its own for the same reason {@link com.novax.leadora.application.usecase.sla.SlaOutcome}
 * does: three reports were each deciding "did this quotation succeed" in their own code, and they
 * disagreed. On the live July data the same eleven accepted quotations were published as a 91.7%
 * rate on one screen and 52.4% on another, and the figure is used to score people.
 */
public enum QuotationOutcome {

    /**
     * Replaced by a newer version, so it is not an opportunity of its own.
     *
     * <p>BR-22 makes an edit a new version row and is supposed to mark the old one SUPERSEDED. It
     * frequently does not — whatever touches the row next leaves it at EXPIRED or CLOSED — so the
     * durable signal is structural: a revision exists that points at this row. Both signals are
     * honoured because they fail in opposite directions.
     */
    SUPERSEDED,

    /** The customer accepted it, or it became a booking. */
    WON,

    /** It went out to the customer and closed without a sale. */
    LOST,

    /**
     * It ended without ever being sent — rejected at approval, or expired while still a draft.
     *
     * <p>Counted apart from {@link #LOST} because no customer was ever involved. Folded together, a
     * rep who wrote twenty quotations and submitted none scores a near-zero win rate for work
     * nobody outside the company saw. It is a discipline problem, not a lost negotiation.
     */
    ABANDONED,

    /** Still in flight — nobody has decided anything yet. */
    OPEN;

    /**
     * True for the outcomes a customer actually settled, i.e. the win-rate denominator.
     *
     * <p>OPEN is excluded because an unanswered quotation is not a loss, and counting it as one
     * drags the rate down hardest in the most recent period — exactly when it gets read. ABANDONED
     * is excluded because no customer saw it. Both are reported on their own so neither is hidden
     * by being left out here.
     */
    public boolean isDecided() {
        return this == WON || this == LOST;
    }

    /** True for the quotations that count as a live opportunity in the period. */
    public boolean isLive() {
        return this != SUPERSEDED;
    }
}
