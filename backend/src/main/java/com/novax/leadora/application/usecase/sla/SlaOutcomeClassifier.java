package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;

import java.time.OffsetDateTime;

/**
 * The single definition of "was this SLA met", used by UC-23.3 and by the per-rep scorecard.
 *
 * <p>Records are classified on <b>whether the deadline was met</b>, not on the status column. An
 * earlier classifier tested {@code status == RESOLVED} first, so resolving a breach moved it out of
 * the breach count entirely: the breach rate improved as the team worked through its backlog, and a
 * period could end at 0% breaches having missed every deadline in it.
 */
public final class SlaOutcomeClassifier {

    private SlaOutcomeClassifier() {
    }

    /**
     * {@code status} only decides <em>whether</em> the record finished; {@code resolvedAt} against
     * {@code deadlineAt} decides whether it finished in time.
     */
    public static SlaOutcome classify(SlaStatus status, OffsetDateTime warningAt,
                                      OffsetDateTime deadlineAt, OffsetDateTime resolvedAt,
                                      OffsetDateTime now) {
        boolean finished = status == SlaStatus.RESOLVED || resolvedAt != null;
        if (finished) {
            if (resolvedAt == null || deadlineAt == null) {
                // Closed, but nothing records when — neither met nor missed can be shown.
                return SlaOutcome.UNDETERMINED;
            }
            return resolvedAt.isAfter(deadlineAt) ? SlaOutcome.RESOLVED_LATE : SlaOutcome.RESOLVED_ON_TIME;
        }
        if (status == SlaStatus.BREACHED || (deadlineAt != null && now.isAfter(deadlineAt))) {
            return SlaOutcome.OPEN_BREACHED;
        }
        if (warningAt != null && now.isAfter(warningAt)) {
            return SlaOutcome.WARNING;
        }
        return SlaOutcome.WITHIN_SLA;
    }
}
