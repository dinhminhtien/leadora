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

        assertThat(run().getAvgProcessingHours()).isEqualTo(5.0);
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
    @DisplayName("an empty period renders zeroes rather than dividing by zero")
    void emptyPeriodIsSafe() {
        SlaReportResponse report = run();

        assertThat(report.getTotalTracked()).isZero();
        assertThat(report.getBreachRatePct()).isZero();
        assertThat(report.getComplianceRatePct()).isZero();
        assertThat(report.getResolutionRatePct()).isZero();
        assertThat(report.getAvgProcessingHours()).isZero();
        assertThat(report.getByActivityType()).isEmpty();
    }
}
