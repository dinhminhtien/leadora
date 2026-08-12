package com.novax.leadora.unit.sla;
import com.novax.leadora.application.usecase.sla.*;

import com.novax.leadora.api.dto.response.SlaReportResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UC-23.3 — the compliance classifier.
 *
 * <p>The behaviour under test is the one that was wrong: a deadline that was missed has to stay
 * counted as a breach even after somebody resolves the record, otherwise the breach rate falls as
 * the backlog gets cleaned up.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetSlaReportUseCaseTest {

    private static final OffsetDateTime NOW = OffsetDateTime.now();

    @Mock
    private SlaTrackingRepository slaTrackingRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private SystemAuditLogService systemAuditLogService;

    private GetSlaReportUseCase useCase;
    private List<Object[]> rows;

    @BeforeEach
    void setUp() {
        rows = new ArrayList<>();
        useCase = new GetSlaReportUseCase(
                slaTrackingRepository, currentUserProvider, systemAuditLogService,
                new ReportRangeFactory("Asia/Ho_Chi_Minh"));
        when(slaTrackingRepository.findComplianceRows(any(), any(), any(), any())).thenReturn(rows);
    }

    /** [activityType, status, startedAt, warningAt, deadlineAt, resolvedAt] */
    private void row(String activity, SlaStatus status, long startedHoursAgo,
                     long warningHoursAgo, long deadlineHoursAgo, Long resolvedHoursAgo) {
        rows.add(new Object[] {
                activity,
                status,
                NOW.minusHours(startedHoursAgo),
                NOW.minusHours(warningHoursAgo),
                NOW.minusHours(deadlineHoursAgo),
                resolvedHoursAgo == null ? null : NOW.minusHours(resolvedHoursAgo)
        });
    }

    private SlaReportResponse run() {
        return useCase.execute(LocalDate.now().minusDays(30), LocalDate.now(), null, null);
    }

    @Test
    @DisplayName("a record resolved after its deadline still counts as a breach")
    void lateResolutionRemainsABreach() {
        // started 10h ago, deadline was 5h ago, resolved only 1h ago → three hours late.
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, 6, 5, 1L);

        SlaReportResponse report = run();

        assertThat(report.getResolvedCount()).isEqualTo(1);
        assertThat(report.getResolvedLateCount()).isEqualTo(1);
        assertThat(report.getResolvedOnTimeCount()).isZero();
        assertThat(report.getBreachedCount()).isEqualTo(1);
        assertThat(report.getBreachRatePct()).isEqualTo(100.0);
        assertThat(report.getComplianceRatePct()).isZero();
    }

    @Test
    @DisplayName("resolving an already-BREACHED record does not erase the breach")
    void resolvingABreachDoesNotImproveTheBreachRate() {
        row("LEAD_RESPONSE", SlaStatus.BREACHED, 10, 6, 5, null);
        SlaReportResponse beforeCleanup = run();

        // Same record, now cleaned up — but it was still resolved after the deadline.
        rows.clear();
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, 6, 5, 1L);
        SlaReportResponse afterCleanup = run();

        assertThat(beforeCleanup.getBreachRatePct()).isEqualTo(100.0);
        assertThat(afterCleanup.getBreachRatePct())
                .as("cleaning up the backlog must not improve the compliance figure")
                .isEqualTo(beforeCleanup.getBreachRatePct());
    }

    @Test
    @DisplayName("a record resolved before its deadline is compliant")
    void onTimeResolutionIsCompliant() {
        // deadline is still 5h in the future; resolved an hour ago.
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, -6, -5, 1L);

        SlaReportResponse report = run();

        assertThat(report.getResolvedOnTimeCount()).isEqualTo(1);
        assertThat(report.getBreachedCount()).isZero();
        assertThat(report.getComplianceRatePct()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("in-flight records are excluded from the compliance rate, not counted as met")
    void inFlightRecordsDoNotInflateCompliance() {
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, 6, 5, 1L);          // late → breach
        row("LEAD_RESPONSE", SlaStatus.ACTIVE, 2, -1, -4, null);          // within SLA, still open
        row("LEAD_RESPONSE", SlaStatus.ACTIVE, 4, 1, -2, null);           // warning, still open

        SlaReportResponse report = run();

        assertThat(report.getTotalTracked()).isEqualTo(3);
        assertThat(report.getInFlightCount()).isEqualTo(2);
        assertThat(report.getWithinSlaCount()).isEqualTo(1);
        assertThat(report.getWarningCount()).isEqualTo(1);
        // One outcome so far, and it was a breach — the two open records must not turn that into 66%.
        assertThat(report.getComplianceRatePct()).isZero();
        // The breach rate has to be diluted by the open queue no more than the compliance rate is.
        // Dividing by totalTracked put this at 33.3% against a compliance rate of 0%.
        assertThat(report.getDecidedCount()).isEqualTo(1);
        assertThat(report.getBreachRatePct()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("the breach rate and the compliance rate are exact complements")
    void bothHeadlineRatesShareOneDenominator() {
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, -6, -5, 1L);   // on time
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, -6, -5, 1L);   // on time
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, 6, 5, 1L);     // late   → breach
        row("LEAD_RESPONSE", SlaStatus.ACTIVE, 2, -1, -4, null);    // still running
        row("LEAD_RESPONSE", SlaStatus.ACTIVE, 3, -1, -4, null);    // still running

        SlaReportResponse report = run();

        assertThat(report.getTotalTracked()).isEqualTo(5);
        assertThat(report.getDecidedCount()).as("the two open records are outside both").isEqualTo(3);
        assertThat(report.getComplianceRatePct()).isEqualTo(66.67);
        assertThat(report.getBreachRatePct()).isEqualTo(33.33);
        assertThat(report.getComplianceRatePct() + report.getBreachRatePct()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("an unresolved record past its deadline is an open breach")
    void unresolvedPastDeadlineIsAnOpenBreach() {
        row("FOLLOW_UP_TASK", SlaStatus.ACTIVE, 10, 6, 5, null);

        SlaReportResponse report = run();

        assertThat(report.getOpenBreachedCount()).isEqualTo(1);
        assertThat(report.getBreachedCount()).isEqualTo(1);
        assertThat(report.getResolvedCount()).isZero();
    }

    @Test
    @DisplayName("processing hours average only over resolved records")
    void averageProcessingHoursIgnoresUnresolvedRecords() {
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, -6, -5, 6L);   // 4 hours
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, -6, -5, 4L);   // 6 hours
        row("LEAD_RESPONSE", SlaStatus.ACTIVE, 100, 1, -2, null);   // no outcome yet

        SlaReportResponse report = run();

        assertThat(report.getAvgProcessingHours()).isEqualTo(5.0);
        assertThat(report.getProcessingSamples()).isEqualTo(2);
        // The record excluded from that average is not thrown away — it is 100 hours old, which is
        // the fact the old single figure hid.
        assertThat(report.getOpenAgeSamples()).isEqualTo(1);
        assertThat(report.getAvgOpenAgeHours()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("a backlog older than the usual resolve time is called out, not hidden by it")
    void anAgedBacklogIsDisclosedBesideTheProcessingTime() {
        // The live shape of the bug: Booking Confirmation resolved in 24h on average while fifteen
        // records had been open for 455h, and the report called it the fastest process in the system.
        row("BOOKING_CONFIRM", SlaStatus.RESOLVED, 30, -6, -5, 6L);   // 24h to resolve
        for (int i = 0; i < 3; i++) {
            row("BOOKING_CONFIRM", SlaStatus.ACTIVE, 400, 300, -5, null);
        }

        SlaReportResponse report = run();

        assertThat(report.getAvgProcessingHours()).isEqualTo(24.0);
        assertThat(report.getAvgOpenAgeHours()).isEqualTo(400.0);
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap)
                        .contains("3 Booking Confirmation record(s) have been open")
                        .contains("longer than the"));
    }

    @Test
    @DisplayName("nothing resolved means unknown processing time, not zero hours")
    void noResolutionsLeaveTheProcessingTimeUnknown() {
        row("ROOM_REQUEST", SlaStatus.ACTIVE, 50, 10, -5, null);
        row("ROOM_REQUEST", SlaStatus.ACTIVE, 50, 10, -5, null);
        row("ROOM_REQUEST", SlaStatus.ACTIVE, 50, 10, -5, null);

        SlaReportResponse report = run();

        assertThat(report.getAvgProcessingHours()).isNull();
        assertThat(report.getProcessingSamples()).isZero();
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap).contains("none has been resolved"));
    }

    @Test
    @DisplayName("an undetermined record ages in neither book")
    void undeterminedIsNeitherResolvedTimeNorOpenQueue() {
        // Finished, but with no timestamp saying when. Ageing it against now() would grow the open
        // queue for ever with work that is already done.
        rows.add(new Object[] { "LEAD_RESPONSE", SlaStatus.RESOLVED,
                NOW.minusHours(500), NOW.minusHours(6), NOW.minusHours(5), null });

        SlaReportResponse report = run();

        assertThat(report.getUndeterminedCount()).isEqualTo(1);
        assertThat(report.getProcessingSamples()).isZero();
        assertThat(report.getOpenAgeSamples()).as("it is not part of the queue either").isZero();
        assertThat(report.getAvgOpenAgeHours()).isNull();
    }

    @Test
    @DisplayName("each activity row partitions its own total, so the table adds back up")
    void breakdownRowsReconcileWithTheirTotal() {
        row("FOLLOW_UP_TASK", SlaStatus.RESOLVED, 10, -6, -5, 1L);   // on time
        row("FOLLOW_UP_TASK", SlaStatus.RESOLVED, 10, 6, 5, 1L);     // late
        row("FOLLOW_UP_TASK", SlaStatus.ACTIVE, 10, 6, 5, null);     // open breach
        row("FOLLOW_UP_TASK", SlaStatus.ACTIVE, 4, 1, -2, null);     // warning
        row("FOLLOW_UP_TASK", SlaStatus.ACTIVE, 2, -1, -4, null);    // within SLA
        rows.add(new Object[] { "FOLLOW_UP_TASK", SlaStatus.RESOLVED,
                NOW.minusHours(10), NOW.minusHours(6), NOW.minusHours(5), null }); // undetermined

        SlaReportResponse report = run();

        assertThat(report.getByActivityType()).singleElement().satisfies(b -> {
            assertThat(b.getResolvedOnTime() + b.getResolvedLate() + b.getUndetermined()
                    + b.getOpenBreached() + b.getWarning() + b.getWithinSla())
                    .as("the columns partition the total exactly")
                    .isEqualTo(b.getTotal());
            assertThat(b.getBreached()).isEqualTo(b.getResolvedLate() + b.getOpenBreached());
            assertThat(b.getDecided()).isEqualTo(b.getResolvedOnTime() + b.getBreached());
            assertThat(b.getComplianceRatePct() + b.getBreachRatePct()).isEqualTo(100.0);
        });
    }

    @Test
    @DisplayName("breakdown splits by activity type and keeps each one's own rates")
    void breakdownIsPerActivityType() {
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, 6, 5, 1L);      // late
        row("FOLLOW_UP_TASK", SlaStatus.RESOLVED, 10, -6, -5, 1L);   // on time

        SlaReportResponse report = run();

        assertThat(report.getByActivityType()).hasSize(2);
        assertThat(report.getByActivityType())
                .filteredOn(b -> b.getActivityType().equals("LEAD_RESPONSE"))
                .singleElement()
                .satisfies(b -> {
                    assertThat(b.getActivityLabel()).isEqualTo("Lead Response");
                    assertThat(b.getBreached()).isEqualTo(1);
                    assertThat(b.getBreachRatePct()).isEqualTo(100.0);
                });
        assertThat(report.getByActivityType())
                .filteredOn(b -> b.getActivityType().equals("FOLLOW_UP_TASK"))
                .singleElement()
                .satisfies(b -> assertThat(b.getComplianceRatePct()).isEqualTo(100.0));
    }

    @Test
    @DisplayName("RESOLVED with no timestamp is undetermined, not credited as on time")
    void resolvedWithoutTimestampIsNotCreditedAsCompliant() {
        // Found on live data: five rows sit at status=RESOLVED with resolved_at NULL. Treating them
        // as met inflated the on-time count from 43 to 48 — the exact flattery this report removes.
        rows.add(new Object[] { "LEAD_RESPONSE", SlaStatus.RESOLVED,
                NOW.minusHours(10), NOW.minusHours(6), NOW.minusHours(5), null });
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, 6, 5, 1L);   // genuinely late

        SlaReportResponse report = run();

        assertThat(report.getUndeterminedCount()).isEqualTo(1);
        assertThat(report.getResolvedOnTimeCount()).isZero();
        assertThat(report.getResolvedCount()).as("it did finish, so it still counts as resolved").isEqualTo(2);
        assertThat(report.getComplianceRatePct())
                .as("one known outcome, and it was a breach — the unknown row must not soften it")
                .isZero();
    }

    @Test
    @DisplayName("an undetermined record is left out of the compliance denominator")
    void undeterminedIsExcludedFromBothSidesOfTheRate() {
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, -6, -5, 1L);  // on time
        rows.add(new Object[] { "LEAD_RESPONSE", SlaStatus.RESOLVED,
                NOW.minusHours(10), NOW.minusHours(6), NOW.minusHours(5), null });

        SlaReportResponse report = run();

        assertThat(report.getComplianceRatePct())
                .as("1 of 1 decided records was met; the undetermined one is not a half-failure")
                .isEqualTo(100.0);
        assertThat(report.getTotalTracked()).isEqualTo(2);
    }

    @Test
    @DisplayName("a period with nothing settled has no rates, rather than a rate of zero")
    void nothingSettledMeansNoRateAtAll() {
        // Every record comfortably inside its deadline. Publishing 0.0% here renders a full-width
        // empty compliance meter, indistinguishable from a period that missed every deadline.
        row("LEAD_RESPONSE", SlaStatus.ACTIVE, 2, -6, -5, null);
        row("LEAD_RESPONSE", SlaStatus.ACTIVE, 1, -6, -5, null);

        SlaReportResponse report = run();

        assertThat(report.getTotalTracked()).isEqualTo(2);
        assertThat(report.getDecidedCount()).isZero();
        assertThat(report.getComplianceRatePct()).isNull();
        assertThat(report.getBreachRatePct()).isNull();
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap).contains("has reached an outcome yet"));
    }

    @Test
    @DisplayName("an open breach is disclosed as counted, not as excluded from the rates")
    void openBreachesAreDescribedAsCountedNotExcluded() {
        // An open breach is still running AND already counted — in the breach numerator and in both
        // denominators. Describing it as sitting outside the rates would tell the reader that the
        // very records driving the breach rate up had been left out of it.
        row("LEAD_RESPONSE", SlaStatus.ACTIVE, 10, 6, 5, null);      // open breach
        row("LEAD_RESPONSE", SlaStatus.ACTIVE, 2, -1, -4, null);     // in flight
        row("LEAD_RESPONSE", SlaStatus.RESOLVED, 10, -6, -5, 1L);    // on time

        SlaReportResponse report = run();

        assertThat(report.getDecidedCount()).as("the open breach is inside the denominator").isEqualTo(2);
        assertThat(report.getBreachRatePct()).isEqualTo(50.0);
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap)
                        .contains("1 record(s) are past their deadline")
                        .contains("already count as breaches"));
        assertThat(report.getDataGaps())
                .as("only the in-flight record is described as outside the rates")
                .anySatisfy(gap -> assertThat(gap)
                        .contains("1 record(s) are still inside their deadline"));
    }

    @Test
    @DisplayName("an empty period renders zeroes rather than dividing by zero")
    void emptyPeriodIsSafe() {
        SlaReportResponse report = run();

        assertThat(report.getTotalTracked()).isZero();
        assertThat(report.getBreachRatePct()).isNull();
        assertThat(report.getComplianceRatePct()).isNull();
        assertThat(report.getResolutionRatePct()).isNull();
        assertThat(report.getAvgProcessingHours()).as("unknown, not instant").isNull();
        assertThat(report.getAvgOpenAgeHours()).isNull();
        assertThat(report.getByActivityType()).isEmpty();
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap).contains("No SLA record started in this period"));
    }
}
