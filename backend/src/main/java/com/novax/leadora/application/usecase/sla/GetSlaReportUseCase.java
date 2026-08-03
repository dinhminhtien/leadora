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

    /** How a record is counted once the deadline question is settled. */
    private enum Outcome {
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
         * <p>These used to be counted as on-time. That is the same flattering bias this report was
         * rewritten to remove — it credits compliance to records that carry no evidence of it, and
         * on the live data it was inflating the on-time count by five. They are now held out of the
         * compliance rate entirely and surfaced on their own, so a gap in the data reads as a gap
         * rather than as a good result.
         */
        UNDETERMINED
    }

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

            Outcome outcome = classify(status, warningAt, deadlineAt, resolvedAt, now);
            Double processingHours = processingHours(startedAt, resolvedAt);

            overall.add(outcome, processingHours);
            byActivity.computeIfAbsent(activity, key -> new Tally()).add(outcome, processingHours);
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
                    .breached(tally.breached())
                    .warning(tally.warning)
                    .withinSla(tally.withinSla)
                    .breachRatePct(ReportingUtils.calculateRate(tally.breached(), tally.total()))
                    .complianceRatePct(tally.complianceRatePct())
                    .avgProcessingHours(tally.avgProcessingHours())
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
                .breachRatePct(ReportingUtils.calculateRate(overall.breached(), overall.total()))
                .complianceRatePct(overall.complianceRatePct())
                .resolutionRatePct(ReportingUtils.calculateRate(
                        overall.resolved(), overall.resolved() + overall.openBreached))
                .avgProcessingHours(overall.avgProcessingHours())
                .byActivityType(breakdown)
                .build();
    }

    /**
     * A missed deadline is a breach whether or not somebody has since resolved it. {@code status}
     * only decides <em>whether</em> the record finished; {@code resolvedAt vs deadlineAt} decides
     * whether it finished in time.
     */
    private static Outcome classify(SlaStatus status, OffsetDateTime warningAt,
                                    OffsetDateTime deadlineAt, OffsetDateTime resolvedAt,
                                    OffsetDateTime now) {
        boolean finished = status == SlaStatus.RESOLVED || resolvedAt != null;
        if (finished) {
            if (resolvedAt == null || deadlineAt == null) {
                // Closed, but nothing records when — neither met nor missed can be shown.
                return Outcome.UNDETERMINED;
            }
            return resolvedAt.isAfter(deadlineAt) ? Outcome.RESOLVED_LATE : Outcome.RESOLVED_ON_TIME;
        }
        if (status == SlaStatus.BREACHED || (deadlineAt != null && now.isAfter(deadlineAt))) {
            return Outcome.OPEN_BREACHED;
        }
        if (warningAt != null && now.isAfter(warningAt)) {
            return Outcome.WARNING;
        }
        return Outcome.WITHIN_SLA;
    }

    private static Double processingHours(OffsetDateTime startedAt, OffsetDateTime resolvedAt) {
        if (startedAt == null || resolvedAt == null) {
            return null;
        }
        double hours = Duration.between(startedAt, resolvedAt).toMinutes() / 60.0;
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
        double processingHours;
        int processingSamples;

        void add(Outcome outcome, Double hours) {
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
        double complianceRatePct() {
            return ReportingUtils.calculateRate(resolvedOnTime, decided());
        }

        double avgProcessingHours() {
            return processingSamples == 0 ? 0 : ReportingUtils.round2(processingHours / processingSamples);
        }
    }
}
