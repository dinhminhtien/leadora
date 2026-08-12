package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.api.dto.response.SlaReportResponse;
import com.novax.leadora.api.dto.response.SlaReportResponse.ActivityBreakdown;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.common.util.ReportRange;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.common.util.ReportingUtils;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UC-23.3 / UC-17.6 — SLA Compliance Report.
 *
 * <p>Records are classified on <b>whether the deadline was met</b>, not on the status column. The
 * previous classifier tested {@code status == RESOLVED} first, so resolving a breach moved it out
 * of the breach count entirely: the breach rate improved as the team worked through its backlog and
 * a period could end at 0% breaches having missed every deadline in it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetSlaReportUseCase {

    /**
     * Display names for the activity types SLA rules are written against.
     *
     * <p>Anything missing here falls through to the raw enum name, which is how {@code
     * BOOKING_CONFIRM} and the {@code ROOM_REQUEST*} family were showing up shouting in the report.
     * {@code Map.of} caps at ten pairs, hence the explicit map.
     */
    private static final Map<String, String> ACTIVITY_LABELS = Map.ofEntries(
            Map.entry("LEAD_RESPONSE", "Lead Response"),
            Map.entry("QUOTATION_SENT", "Quotation Dispatch"),
            Map.entry("QUOTATION_APPROVAL", "Quotation Approval"),
            Map.entry("FOLLOW_UP_TASK", "Follow-up Task"),
            Map.entry("PAYMENT_DEPOSIT", "Payment Deposit"),
            Map.entry("HANDOVER_SUBMISSION", "Handover Submission"),
            Map.entry("CUSTOMER_FEEDBACK_RESPONSE", "Customer Feedback Response"),
            Map.entry("BOOKING_CONFIRM", "Booking Confirmation"),
            Map.entry("ROOM_REQUEST", "Room Request"),
            Map.entry("ROOM_REQUESTED", "Room Request Raised"),
            Map.entry("ROOM_REQUEST_RAISED", "Room Request Raised"),
            Map.entry("ROOM_REQUEST_ANSWERED", "Room Request Answered"));

    /** A still-open queue worth naming in {@code dataGaps}, rather than one stray record. */
    private static final int OPEN_QUEUE_DISCLOSURE_THRESHOLD = 3;

    private final SlaTrackingRepository slaTrackingRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SystemAuditLogService systemAuditLogService;
    private final ReportRangeFactory reportRangeFactory;

    /**
     * <p>Deliberately not {@code @Cacheable}, unlike the other four UC-23 reports: BR-37 requires
     * every view of this report to be audited, and a cache hit returns before the method body — and
     * therefore before the audit write — so caching would drop exactly the accesses a compliance
     * report most needs recorded.
     *
     * @param from         report start date, inclusive; null means "since the beginning"
     * @param to           report end date, inclusive; null means "up to now"
     * @param activityType optional filter — null means all activity types
     * @param entityType   optional filter — null means all entity types
     */
    @Transactional(readOnly = true)
    public SlaReportResponse execute(LocalDate from, LocalDate to,
                                     String activityType, String entityType) {
        ReportRange range = reportRangeFactory.resolve(from, to);
        OffsetDateTime now = OffsetDateTime.now();

        List<Object[]> rows = slaTrackingRepository.findComplianceRows(
                range.start(), range.endExclusive(), entityType, activityType);

        Tally overall = new Tally();
        Map<String, Tally> byActivity = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String activity = (String) row[0];
            SlaStatus status = (SlaStatus) row[1];
            OffsetDateTime startedAt = (OffsetDateTime) row[2];
            OffsetDateTime warningAt = (OffsetDateTime) row[3];
            OffsetDateTime deadlineAt = (OffsetDateTime) row[4];
            OffsetDateTime resolvedAt = (OffsetDateTime) row[5];

            SlaOutcome outcome = SlaOutcomeClassifier.classify(status, warningAt, deadlineAt, resolvedAt, now);
            Double processingHours = processingHours(startedAt, resolvedAt);
            // Only the still-running records age against now(). UNDETERMINED also lacks a
            // resolvedAt, but it finished — ageing it here would grow the queue for ever with work
            // that is already done.
            Double openHours = outcome.isUnresolved() ? hoursBetween(startedAt, now) : null;

            overall.add(outcome, processingHours, openHours);
            byActivity.computeIfAbsent(activity, key -> new Tally())
                    .add(outcome, processingHours, openHours);
        }

        auditAccess(range, activityType, entityType, rows.size());

        List<ActivityBreakdown> breakdown = new ArrayList<>();
        for (Map.Entry<String, Tally> entry : byActivity.entrySet()) {
            Tally tally = entry.getValue();
            breakdown.add(ActivityBreakdown.builder()
                    .activityType(entry.getKey())
                    .activityLabel(ACTIVITY_LABELS.getOrDefault(entry.getKey(), entry.getKey()))
                    .total(tally.total())
                    .resolved(tally.resolved())
                    .resolvedOnTime(tally.resolvedOnTime)
                    .resolvedLate(tally.resolvedLate)
                    .openBreached(tally.openBreached)
                    .undetermined(tally.undetermined)
                    .breached(tally.breached())
                    .warning(tally.warning)
                    .withinSla(tally.withinSla)
                    .decided(tally.decided())
                    // Same denominator as the compliance rate beside it, so the two complement.
                    .breachRatePct(rateOrNull(tally.breached(), tally.decided()))
                    .complianceRatePct(tally.complianceRatePct())
                    .avgProcessingHours(tally.avgProcessingHours())
                    .processingSamples(tally.processingSamples)
                    .avgOpenAgeHours(tally.avgOpenAgeHours())
                    .openAgeSamples(tally.openAgeSamples)
                    .build());
        }

        return SlaReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .totalTracked(overall.total())
                .resolvedCount(overall.resolved())
                .resolvedOnTimeCount(overall.resolvedOnTime)
                .resolvedLateCount(overall.resolvedLate)
                .undeterminedCount(overall.undetermined)
                .breachedCount(overall.breached())
                .openBreachedCount(overall.openBreached)
                .warningCount(overall.warning)
                .withinSlaCount(overall.withinSla)
                .inFlightCount(overall.inFlight())
                .decidedCount(overall.decided())
                .breachRatePct(rateOrNull(overall.breached(), overall.decided()))
                .complianceRatePct(overall.complianceRatePct())
                .resolutionRatePct(rateOrNull(
                        overall.resolved(), overall.resolved() + overall.openBreached))
                .avgProcessingHours(overall.avgProcessingHours())
                .processingSamples(overall.processingSamples)
                .avgOpenAgeHours(overall.avgOpenAgeHours())
                .openAgeSamples(overall.openAgeSamples)
                .dataGaps(dataGaps(overall, breakdown))
                .byActivityType(breakdown)
                .build();
    }

    /**
     * What this period could not establish, in the words a reader needs to discount a figure by.
     *
     * <p>Each entry marks a place the report would otherwise publish a confident number over almost
     * nothing, or stay quiet about a population its headline cannot see.
     */
    private static List<String> dataGaps(Tally overall, List<ActivityBreakdown> breakdown) {
        List<String> gaps = new ArrayList<>();

        if (overall.total() == 0) {
            gaps.add("No SLA record started in this period, so the figures are empty rather than "
                    + "zero.");
            return gaps;
        }
        if (overall.decided() == 0) {
            gaps.add("None of the " + overall.total() + " SLA records started here has reached an "
                    + "outcome yet, so neither the compliance rate nor the breach rate can be "
                    + "established.");
        }
        if (overall.undetermined > 0) {
            gaps.add(overall.undetermined + " record(s) are marked resolved but carry no resolution "
                    + "time, so whether they met their deadline cannot be established. They are "
                    + "held out of both rates rather than assumed compliant.");
        }
        // Only the in-flight records are outside the rates. An open breach is also still running,
        // but its deadline has already passed, so it is counted — as a breach, in the numerator and
        // the denominator both. Lumping the two together here would tell the reader that the very
        // records driving the breach rate up were excluded from it.
        if (overall.inFlight() > 0) {
            gaps.add(overall.inFlight() + " record(s) are still inside their deadline and so sit "
                    + "outside both rates; one started near the end of the period may yet land in "
                    + "either column.");
        }
        if (overall.openBreached > 0) {
            gaps.add(overall.openBreached + " record(s) are past their deadline and still unresolved. "
                    + "They already count as breaches — the deadline was missed whether or not "
                    + "anyone closes them later.");
        }
        // Compared against the records whose outcome IS established, not against every finished
        // record: the undetermined ones have no resolution time by definition, so measuring against
        // them would restate the line above as though it were a second, separate problem.
        int timeable = overall.resolvedOnTime + overall.resolvedLate;
        if (overall.processingSamples < timeable) {
            gaps.add("Processing time covers " + overall.processingSamples + " of " + timeable
                    + " record(s) whose deadline outcome is known — the rest are missing a usable "
                    + "start or resolution timestamp.");
        }

        // The queue a resolve-time average cannot show: work that has been open far longer than the
        // same activity normally takes to finish, and so never enters that average at all.
        for (ActivityBreakdown row : breakdown) {
            Double open = row.getAvgOpenAgeHours();
            Double resolve = row.getAvgProcessingHours();
            if (open == null || row.getOpenAgeSamples() < OPEN_QUEUE_DISCLOSURE_THRESHOLD) {
                continue;
            }
            if (resolve == null) {
                gaps.add(row.getOpenAgeSamples() + " " + row.getActivityLabel() + " record(s) are "
                        + "still open, averaging " + ReportingUtils.round2(open) + "h, and none has "
                        + "been resolved — there is no processing time to compare them against.");
            } else if (open > resolve) {
                gaps.add(row.getOpenAgeSamples() + " " + row.getActivityLabel() + " record(s) have "
                        + "been open " + ReportingUtils.round2(open) + "h on average, longer than "
                        + "the " + ReportingUtils.round2(resolve) + "h this activity usually takes "
                        + "to resolve — a backlog the processing time does not show.");
            }
        }
        return gaps;
    }

    /**
     * A rate, or null when the denominator holds nothing that could establish one.
     *
     * <p>{@code ReportingUtils.calculateRate} answers 0.0 for an empty denominator, which is the
     * right default for a count but the wrong one for a compliance figure: it renders as a full
     * red meter reading "0% compliant" for a period in which nothing has yet been decided.
     */
    private static Double rateOrNull(long part, long whole) {
        return whole <= 0 ? null : ReportingUtils.calculateRate(part, whole);
    }

    private static Double processingHours(OffsetDateTime startedAt, OffsetDateTime resolvedAt) {
        return hoursBetween(startedAt, resolvedAt);
    }

    /** Null when either end is missing or the pair runs backwards — an unusable measurement. */
    private static Double hoursBetween(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            return null;
        }
        double hours = Duration.between(from, to).toMinutes() / 60.0;
        return hours >= 0 ? hours : null;
    }

    /** BR-37 — report access is auditable. A logging failure must not fail the report. */
    private void auditAccess(ReportRange range, String activityType, String entityType, int recordCount) {
        UserEntity actor = null;
        try {
            actor = currentUserProvider.resolve(null);
        } catch (RuntimeException ex) {
            log.warn("Could not resolve actor for SLA report audit log: {}", ex.getMessage());
        }
        try {
            systemAuditLogService.log("SLA", "SLA_REPORT", UUID.randomUUID(), "VIEWED", actor,
                    null, null, String.format("from=%s, to=%s, activityType=%s, entityType=%s, records=%d",
                            range.from(), range.to(), activityType, entityType, recordCount));
        } catch (RuntimeException ex) {
            log.warn("SLA report audit log failed: {}", ex.getMessage());
        }
        log.info("SLA report viewed: from={}, to={}, activityType={}, entityType={}, totalRecords={}",
                range.from(), range.to(), activityType, entityType, recordCount);
    }

    /** Running counts for one population (overall, or one activity type). */
    private static final class Tally {
        int resolvedOnTime;
        int resolvedLate;
        int openBreached;
        int warning;
        int withinSla;
        int undetermined;

        // Two separate books. A finished record contributes a duration; a running one contributes
        // only how long it has waited so far. Adding them together produces a number that tracks
        // the size of the backlog rather than how long the work takes.
        double processingHours;
        int processingSamples;
        double openAgeHours;
        int openAgeSamples;

        void add(SlaOutcome outcome, Double hours, Double openHours) {
            switch (outcome) {
                case RESOLVED_ON_TIME -> resolvedOnTime++;
                case RESOLVED_LATE -> resolvedLate++;
                case OPEN_BREACHED -> openBreached++;
                case WARNING -> warning++;
                case WITHIN_SLA -> withinSla++;
                case UNDETERMINED -> undetermined++;
            }
            if (hours != null) {
                processingHours += hours;
                processingSamples++;
            }
            if (openHours != null) {
                openAgeHours += openHours;
                openAgeSamples++;
            }
        }

        /** Everything that finished, including the ones whose timing cannot be established. */
        int resolved() {
            return resolvedOnTime + resolvedLate + undetermined;
        }

        /** Every missed deadline, resolved late or still running over. */
        int breached() {
            return resolvedLate + openBreached;
        }

        int inFlight() {
            return warning + withinSla;
        }

        int total() {
            return resolved() + openBreached + inFlight();
        }

        /** Records whose deadline outcome is knowable — the compliance rate's denominator. */
        int decided() {
            return resolvedOnTime + breached();
        }

        /**
         * Measured only over records that reached an outcome. Counting still-running records as
         * compliant credits the team for deadlines that have not arrived yet, which moves the
         * headline number with the size of the open queue rather than with performance.
         */
        Double complianceRatePct() {
            return rateOrNull(resolvedOnTime, decided());
        }

        /** Null rather than zero: nothing resolved means unknown, not instant. */
        Double avgProcessingHours() {
            return processingSamples == 0 ? null : ReportingUtils.round2(processingHours / processingSamples);
        }

        /** How long the still-running records have been waiting. Null when none are. */
        Double avgOpenAgeHours() {
            return openAgeSamples == 0 ? null : ReportingUtils.round2(openAgeHours / openAgeSamples);
        }
    }
}
