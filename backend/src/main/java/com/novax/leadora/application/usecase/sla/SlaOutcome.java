package com.novax.leadora.application.usecase.sla;

/**
 * How an SLA record is counted once the deadline question is settled.
 *
 * <p>Shared by UC-23.3 (the compliance report) and the per-rep scorecard. It lives on its own so
 * there is exactly one answer to "was this on time": two reports quoting different compliance rates
 * for the same period is the kind of disagreement nobody can adjudicate from the outside, and the
 * numbers are used to judge people.
 */
public enum SlaOutcome {
    /** Finished inside the deadline. */
    RESOLVED_ON_TIME,
    /** Finished, but the deadline had already passed — a breach that was later cleaned up. */
    RESOLVED_LATE,
    /** Unresolved and past the deadline. */
    OPEN_BREACHED,
    /** Unresolved, past the warning threshold, deadline not reached. */
    WARNING,
    /** Unresolved and comfortably inside the deadline. */
    WITHIN_SLA,
    /**
     * Marked RESOLVED but with no {@code resolved_at}, so whether the deadline was met cannot be
     * established either way.
     *
     * <p>These used to be counted as on-time. That is a flattering bias — it credits compliance to
     * records that carry no evidence of it, and on the live data it was inflating the on-time count
     * by five. They are held out of the compliance rate entirely and surfaced on their own, so a
     * gap in the data reads as a gap rather than as a good result.
     */
    UNDETERMINED;

    /** True for the outcomes that settle the deadline question, i.e. the compliance denominator. */
    public boolean isDecided() {
        return this == RESOLVED_ON_TIME || this == RESOLVED_LATE || this == OPEN_BREACHED;
    }

    /** A missed deadline, whether or not somebody has since cleaned it up. */
    public boolean isBreach() {
        return this == RESOLVED_LATE || this == OPEN_BREACHED;
    }
}
