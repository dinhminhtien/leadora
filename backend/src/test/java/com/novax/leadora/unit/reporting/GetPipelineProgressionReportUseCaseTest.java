package com.novax.leadora.unit.reporting;
import com.novax.leadora.application.usecase.reporting.*;

import com.novax.leadora.api.dto.response.PipelineProgressionReportResponse;
import com.novax.leadora.api.dto.response.PipelineProgressionReportResponse.StageRow;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealStageHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** UC-23.4 — stage distribution, aging, and the stall ranking behind the bottleneck call. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetPipelineProgressionReportUseCaseTest {

    private static final OffsetDateTime NOW = OffsetDateTime.now();

    @Mock
    private DealRepository dealRepository;
    @Mock
    private DealStageHistoryRepository dealStageHistoryRepository;

    private GetPipelineProgressionReportUseCase useCase;
    private List<Object[]> rows;
    private List<Object[]> history;

    @BeforeEach
    void setUp() {
        rows = new ArrayList<>();
        history = new ArrayList<>();
        useCase = new GetPipelineProgressionReportUseCase(
                dealRepository, dealStageHistoryRepository, new ReportRangeFactory("Asia/Ho_Chi_Minh"));
        when(dealRepository.findStageAgingRows(any(), any())).thenReturn(rows);
        when(dealStageHistoryRepository.findTransitionsForDeals(any())).thenReturn(history);
    }

    /** Deal row: [dealId, pipelineStage, expectedRevenue, createdAt, closedAt] */
    private UUID deal(DealPipelineStage stage, long value, long createdDaysAgo, Long closedDaysAgo) {
        UUID dealId = UUID.randomUUID();
        rows.add(new Object[] {
                dealId, stage, BigDecimal.valueOf(value),
                NOW.minusDays(createdDaysAgo),
                closedDaysAgo == null ? null : NOW.minusDays(closedDaysAgo)
        });
        return dealId;
    }

    /** History row: [dealId, fromStage, toStage, changedAt] — must be added in chronological order. */
    private void moved(UUID dealId, DealPipelineStage from, DealPipelineStage to, long daysAgo) {
        history.add(new Object[] { dealId, from, to, NOW.minusDays(daysAgo) });
    }

    /**
     * {@code count} deals that entered {@code stage} and left it for the archive, giving the stage
     * that many completed legs of {@code enteredDaysAgo - leftDaysAgo} days each.
     */
    private void crossed(DealPipelineStage stage, int count, long enteredDaysAgo, long leftDaysAgo) {
        for (int i = 0; i < count; i++) {
            UUID id = deal(DealPipelineStage.CLOSED_LOST, 100, enteredDaysAgo, leftDaysAgo);
            moved(id, null, stage, enteredDaysAgo);
            moved(id, stage, DealPipelineStage.CLOSED_LOST, leftDaysAgo);
        }
    }

    private PipelineProgressionReportResponse run() {
        return useCase.execute(LocalDate.now().minusDays(90), LocalDate.now());
    }

    private StageRow stage(PipelineProgressionReportResponse report, DealPipelineStage stage) {
        return report.getStages().stream()
                .filter(row -> row.getStage().equals(stage.name()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("time in stage is measured from recorded transitions, not from deal age")
    void dwellTimeComesFromRecordedTransitions() {
        // The worked example: 29-day deal, 18 of them stuck in NEGOTIATION.
        UUID id = deal(DealPipelineStage.CLOSED_WON, 100, 29, 0L);
        moved(id, null, DealPipelineStage.INQUIRY, 29);
        moved(id, DealPipelineStage.INQUIRY, DealPipelineStage.QUALIFICATION, 27);
        moved(id, DealPipelineStage.QUALIFICATION, DealPipelineStage.QUOTATION_SENT, 20);
        moved(id, DealPipelineStage.QUOTATION_SENT, DealPipelineStage.NEGOTIATION, 18);
        moved(id, DealPipelineStage.NEGOTIATION, DealPipelineStage.CLOSED_WON, 0);

        PipelineProgressionReportResponse report = run();

        assertThat(report.isHistoryMeasured()).isTrue();
        assertThat(stage(report, DealPipelineStage.INQUIRY).getAvgDaysToMoveOn()).isEqualTo(2.0);
        assertThat(stage(report, DealPipelineStage.QUALIFICATION).getAvgDaysToMoveOn()).isEqualTo(7.0);
        assertThat(stage(report, DealPipelineStage.QUOTATION_SENT).getAvgDaysToMoveOn()).isEqualTo(2.0);
        assertThat(stage(report, DealPipelineStage.NEGOTIATION).getAvgDaysToMoveOn()).isEqualTo(18.0);
        assertThat(report.getBottleneckStage()).isEqualTo("Negotiation");
        assertThat(report.getBottleneckBasis()).contains("measured time to leave the stage");
    }

    @Test
    @DisplayName("time still being served is reported as a queue, not folded into the stage average")
    void openLegsAreKeptOutOfTheStageAverage() {
        // Three deals crossed Qualification in a day each; four more have been parked there for
        // three weeks. Averaging all seven together produced the old headline figure of ~13 days,
        // which is neither how long the stage takes nor how long the queue has waited.
        for (int i = 0; i < 3; i++) {
            UUID quick = deal(DealPipelineStage.NEGOTIATION, 100, 10, null);
            moved(quick, null, DealPipelineStage.QUALIFICATION, 10);
            moved(quick, DealPipelineStage.QUALIFICATION, DealPipelineStage.NEGOTIATION, 9);
        }
        for (int i = 0; i < 4; i++) {
            UUID stuck = deal(DealPipelineStage.QUALIFICATION, 100, 21, null);
            moved(stuck, null, DealPipelineStage.QUALIFICATION, 21);
        }

        StageRow qualification = stage(run(), DealPipelineStage.QUALIFICATION);

        assertThat(qualification.getAvgDaysToMoveOn()).isEqualTo(1.0);
        assertThat(qualification.getCompletedLegs()).isEqualTo(3);
        assertThat(qualification.getDealsWaitingNow()).isEqualTo(4);
        assertThat(qualification.getAvgDaysWaiting()).isEqualTo(21.0);
    }

    @Test
    @DisplayName("a queue that outlasts the stage's normal pace is called out, not hidden by it")
    void backlogIsReportedEvenWhenCompletedLegsLookFast() {
        // The hole a completed-legs-only ranking leaves: the stage looks like the fastest in the
        // pipeline because the one deal that escaped did so quickly, while four sit in it.
        UUID escaped = deal(DealPipelineStage.NEGOTIATION, 100, 30, null);
        moved(escaped, null, DealPipelineStage.QUOTATION_SENT, 30);
        moved(escaped, DealPipelineStage.QUOTATION_SENT, DealPipelineStage.NEGOTIATION, 30);
        for (int i = 0; i < 4; i++) {
            UUID stuck = deal(DealPipelineStage.QUOTATION_SENT, 100, 28, null);
            moved(stuck, null, DealPipelineStage.QUOTATION_SENT, 28);
        }

        PipelineProgressionReportResponse report = run();

        assertThat(stage(report, DealPipelineStage.QUOTATION_SENT).getAvgDaysToMoveOn()).isZero();
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap)
                        .contains("4 deal(s) have been waiting in Quotation Sent")
                        .contains("longer than the"));
    }

    @Test
    @DisplayName("a stage nothing has left yet reads as unknown, not as instant")
    void aStageNoDealHasLeftIsUnknownRatherThanFast() {
        for (int i = 0; i < 3; i++) {
            UUID stuck = deal(DealPipelineStage.NEGOTIATION, 100, 21, null);
            moved(stuck, null, DealPipelineStage.NEGOTIATION, 21);
        }

        PipelineProgressionReportResponse report = run();
        StageRow negotiation = stage(report, DealPipelineStage.NEGOTIATION);

        assertThat(negotiation.getAvgDaysToMoveOn()).as("unknown, not zero").isNull();
        assertThat(negotiation.getDealsWaitingNow()).isEqualTo(3);
        assertThat(negotiation.getAvgDaysWaiting()).isEqualTo(21.0);
        assertThat(report.getBottleneckStage()).as("nothing has completed, so nothing is ranked").isNull();
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap).contains("none has left it yet"));
    }

    @Test
    @DisplayName("no bottleneck is claimed when nothing measurably lingered anywhere")
    void aZeroLengthCrossingIsNotABottleneck() {
        // Every transition landing in the same minute — seeded data, or a rep clicking straight
        // through — used to still produce "deals take longest to get out of this stage (0.0 days)".
        crossed(DealPipelineStage.INQUIRY, 4, 10, 10);
        crossed(DealPipelineStage.QUALIFICATION, 3, 10, 10);

        PipelineProgressionReportResponse report = run();

        assertThat(stage(report, DealPipelineStage.INQUIRY).getCompletedLegs()).isEqualTo(4);
        assertThat(report.getBottleneckStage()).isNull();
        assertThat(report.getBottleneckBasis()).isNull();
        // The banner vanishing is not enough — every other path that withholds the bottleneck says
        // why, and an unexplained absence is the silence this list exists to remove.
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap).contains("no stage stands out as a bottleneck"));
    }

    @Test
    @DisplayName("a single deal parked in a stage nothing has left is still disclosed")
    void aShortQueueWithNoExitsIsStillDisclosed() {
        // The stage is unrankable by construction, so the disclosure cannot wait for the queue to
        // grow to the size that justifies the "queue outlasts the pace" warning.
        crossed(DealPipelineStage.INQUIRY, 5, 20, 14);
        UUID parked = deal(DealPipelineStage.NEGOTIATION, 100, 40, null);
        moved(parked, null, DealPipelineStage.NEGOTIATION, 40);

        PipelineProgressionReportResponse report = run();

        assertThat(stage(report, DealPipelineStage.NEGOTIATION).getDealsWaitingNow()).isEqualTo(1);
        assertThat(report.getBottleneckStage()).as("ranked on crossings only").isEqualTo("Inquiry");
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap)
                        .contains("1 deal(s) are sitting in Negotiation")
                        .contains("cannot be ranked as the bottleneck"));
    }

    @Test
    @DisplayName("timings drawn from part of the cohort say how much of it they cover")
    void partialHistoryCoverageIsDisclosed() {
        // Stage history only starts being written when the feature ships, so any window reaching
        // back before that measures a subset while the totals count everything.
        crossed(DealPipelineStage.INQUIRY, 2, 20, 15);
        deal(DealPipelineStage.QUALIFICATION, 100, 60, null);
        deal(DealPipelineStage.NEGOTIATION, 100, 60, null);

        PipelineProgressionReportResponse report = run();

        assertThat(report.getTotalDeals()).isEqualTo(4);
        assertThat(report.isHistoryMeasured()).isTrue();
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap).contains("Stage timings cover 2 of 4 deals"));
    }

    @Test
    @DisplayName("a stage counts every deal that passed through, not only those sitting there now")
    void dwellCoversDealsThatHaveMovedOn() {
        UUID id = deal(DealPipelineStage.CLOSED_WON, 100, 10, 0L);
        moved(id, null, DealPipelineStage.QUALIFICATION, 10);
        moved(id, DealPipelineStage.QUALIFICATION, DealPipelineStage.CLOSED_WON, 0);

        StageRow qualification = stage(run(), DealPipelineStage.QUALIFICATION);

        assertThat(qualification.getCount()).as("nothing is sitting in it now").isZero();
        assertThat(qualification.getCompletedLegs()).isEqualTo(1);
        assertThat(qualification.getAvgDaysToMoveOn()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("with no history recorded yet, the report says so instead of claiming measurement")
    void fallsBackHonestlyWhenNoHistoryExists() {
        deal(DealPipelineStage.NEGOTIATION, 100, 20, null);

        PipelineProgressionReportResponse report = run();

        assertThat(report.isHistoryMeasured()).isFalse();
        assertThat(report.getBottleneckStage()).isNull();
        StageRow negotiation = stage(report, DealPipelineStage.NEGOTIATION);
        assertThat(negotiation.getCompletedLegs()).isZero();
        assertThat(negotiation.getAvgDaysToMoveOn()).isNull();
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap).contains("No stage-change history"));
    }

    @Test
    @DisplayName("one slow crossing does not outrank a stage measured eight times")
    void thinEvidenceIsShrunkBeforeRankingTheBottleneck() {
        // Negotiation has the highest raw average — 14 days — on a single crossing. Qualification is
        // slower in aggregate: 9 days, eight times over. Ranking on the raw number handed the title
        // to the single observation, which is how live data crowned Pending Confirmation on two
        // visits, one of which had not even finished.
        crossed(DealPipelineStage.INQUIRY, 10, 20, 18);        // 10 legs × 2 days
        crossed(DealPipelineStage.QUALIFICATION, 8, 20, 11);   //  8 legs × 9 days
        crossed(DealPipelineStage.NEGOTIATION, 1, 20, 6);      //  1 leg  × 14 days

        PipelineProgressionReportResponse report = run();

        assertThat(stage(report, DealPipelineStage.NEGOTIATION).getAvgDaysToMoveOn())
                .as("highest raw average").isEqualTo(14.0);
        assertThat(stage(report, DealPipelineStage.QUALIFICATION).getAvgDaysToMoveOn()).isEqualTo(9.0);
        assertThat(report.getBottleneckStage())
                .as("but one crossing does not outweigh eight").isEqualTo("Qualification");
        assertThat(report.getBottleneckBasis()).contains("8 completed stage exit(s)");
    }

    @Test
    @DisplayName("a closed deal stops ageing at the point it closed")
    void closedDealsDoNotAgeForever() {
        // Created 100 days ago, closed 90 days ago: a 10-day deal, not a 100-day one.
        deal(DealPipelineStage.CLOSED_WON, 500, 100, 90L);

        StageRow won = stage(run(), DealPipelineStage.CLOSED_WON);

        assertThat(won.getAvgAgeDays()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("an open deal ages up to now")
    void openDealsAgeToNow() {
        deal(DealPipelineStage.NEGOTIATION, 500, 20, null);

        StageRow negotiation = stage(run(), DealPipelineStage.NEGOTIATION);

        assertThat(negotiation.getAvgAgeDays()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("a closed stage is never proposed as the bottleneck")
    void closedStagesAreNotBottlenecks() {
        // Moved through Inquiry in a day, then sat closed-lost in the archive for 299 days.
        UUID lost = deal(DealPipelineStage.CLOSED_LOST, 100, 300, 299L);
        moved(lost, null, DealPipelineStage.INQUIRY, 300);
        moved(lost, DealPipelineStage.INQUIRY, DealPipelineStage.CLOSED_LOST, 299);

        UUID open = deal(DealPipelineStage.QUALIFICATION, 100, 2, null);
        moved(open, null, DealPipelineStage.QUALIFICATION, 2);

        PipelineProgressionReportResponse report = run();

        // CLOSED_LOST has by far the longest stay (299 days), but it is terminal: sitting in the
        // archive is not a bottleneck. Inquiry wins on the only completed crossing there is —
        // Qualification's two days are still being served, so they are not a crossing time at all.
        assertThat(report.getBottleneckStage()).isEqualTo("Inquiry");
        assertThat(stage(report, DealPipelineStage.CLOSED_LOST).getAvgDaysToMoveOn())
                .as("a terminal stage publishes no crossing time").isNull();
    }

    @Test
    @DisplayName("pipeline value covers open stages only, and totals reconcile")
    void openPipelineValueExcludesClosedStages() {
        deal(DealPipelineStage.INQUIRY, 100, 5, null);
        deal(DealPipelineStage.NEGOTIATION, 200, 5, null);
        deal(DealPipelineStage.CLOSED_WON, 900, 5, 1L);
        deal(DealPipelineStage.CLOSED_LOST, 700, 5, 1L);

        PipelineProgressionReportResponse report = run();

        assertThat(report.getPipelineValue()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(report.getTotalDeals()).isEqualTo(4);
        assertThat(report.getOpenDeals()).isEqualTo(2);
        assertThat(report.getClosedWon()).isEqualTo(1);
        assertThat(report.getClosedLost()).isEqualTo(1);
        assertThat(report.getCohortWinRate()).isEqualTo(50.0);
        assertThat(report.getCohortDecided()).as("the denominator travels with the rate").isEqualTo(2);
    }

    @Test
    @DisplayName("a cohort with nothing settled yet has no win rate, rather than a rate of zero")
    void unsettledCohortHasNoWinRate() {
        deal(DealPipelineStage.NEGOTIATION, 100, 5, null);
        deal(DealPipelineStage.QUALIFICATION, 100, 5, null);

        PipelineProgressionReportResponse report = run();

        assertThat(report.getCohortWinRate()).isNull();
        assertThat(report.getCohortDecided()).isZero();
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap).contains("has settled yet"));
    }

    @Test
    @DisplayName("deals closed here but opened earlier are counted and named, not silently dropped")
    void dealsOutsideTheCohortAreDisclosed() {
        // The structural blind spot of an opening cohort, and the reason this report's win rate can
        // disagree with the one UC-23.1 publishes over deals closed in the period.
        when(dealRepository.countClosedInRangeOpenedBefore(any(), any())).thenReturn(3L);
        deal(DealPipelineStage.CLOSED_WON, 100, 5, 1L);

        PipelineProgressionReportResponse report = run();

        assertThat(report.getClosedHereOpenedEarlier()).isEqualTo(3);
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap)
                        .contains("3 deal(s) closed during this period were opened before it")
                        .contains("Sales Performance"));
    }

    @Test
    @DisplayName("a deal with no stage is surfaced instead of silently unbalancing the totals")
    void unstagedDealsAreSurfaced() {
        deal(null, 100, 5, null);
        deal(DealPipelineStage.INQUIRY, 100, 5, null);

        PipelineProgressionReportResponse report = run();

        assertThat(report.getTotalDeals()).isEqualTo(2);
        assertThat(report.getStages())
                .filteredOn(row -> row.getStage().equals("UNKNOWN"))
                .singleElement()
                .satisfies(row -> assertThat(row.getCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("every stage is listed even when empty, so the funnel keeps its shape")
    void allStagesAreListed() {
        deal(DealPipelineStage.INQUIRY, 100, 1, null);

        PipelineProgressionReportResponse report = run();

        assertThat(report.getStages()).hasSize(DealPipelineStage.values().length);
        assertThat(stage(report, DealPipelineStage.CLOSED_WON).getCount()).isZero();
    }

    @Test
    @DisplayName("an empty period renders zeroes rather than dividing by zero")
    void emptyPeriodIsSafe() {
        PipelineProgressionReportResponse report = run();

        assertThat(report.getTotalDeals()).isZero();
        assertThat(report.getCohortWinRate()).isNull();
        assertThat(report.getBottleneckStage()).isNull();
        assertThat(report.getPipelineValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.getDataGaps())
                .anySatisfy(gap -> assertThat(gap).contains("No deals were opened"));
    }
}
