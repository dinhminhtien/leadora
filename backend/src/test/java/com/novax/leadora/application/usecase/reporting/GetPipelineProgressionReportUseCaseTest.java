package com.novax.leadora.application.usecase.reporting;

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
        assertThat(stage(report, DealPipelineStage.INQUIRY).getAvgDaysInStage()).isEqualTo(2.0);
        assertThat(stage(report, DealPipelineStage.QUALIFICATION).getAvgDaysInStage()).isEqualTo(7.0);
        assertThat(stage(report, DealPipelineStage.QUOTATION_SENT).getAvgDaysInStage()).isEqualTo(2.0);
        assertThat(stage(report, DealPipelineStage.NEGOTIATION).getAvgDaysInStage()).isEqualTo(18.0);
        assertThat(report.getBottleneckStage()).isEqualTo("Negotiation");
        assertThat(report.getBottleneckBasis()).contains("measured");
    }

    @Test
    @DisplayName("a busy deal that is not advancing still counts as stuck")
    void editsDoNotDisguiseAStalledStage() {
        // The failure mode of the old idle-time proxy: someone edits the notes every other day, so
        // updated_at always looks fresh while the deal has not moved stage in three weeks.
        UUID id = deal(DealPipelineStage.NEGOTIATION, 100, 21, null);
        moved(id, null, DealPipelineStage.INQUIRY, 21);
        moved(id, DealPipelineStage.INQUIRY, DealPipelineStage.NEGOTIATION, 21);

        PipelineProgressionReportResponse report = run();

        assertThat(stage(report, DealPipelineStage.NEGOTIATION).getAvgDaysInStage()).isEqualTo(21.0);
        assertThat(report.getBottleneckStage()).isEqualTo("Negotiation");
    }

    @Test
    @DisplayName("a stage counts every deal that passed through, not only those sitting there now")
    void dwellCoversDealsThatHaveMovedOn() {
        UUID id = deal(DealPipelineStage.CLOSED_WON, 100, 10, 0L);
        moved(id, null, DealPipelineStage.QUALIFICATION, 10);
        moved(id, DealPipelineStage.QUALIFICATION, DealPipelineStage.CLOSED_WON, 0);

        StageRow qualification = stage(run(), DealPipelineStage.QUALIFICATION);

        assertThat(qualification.getCount()).as("nothing is sitting in it now").isZero();
        assertThat(qualification.getDwellSamples()).isEqualTo(1);
        assertThat(qualification.getAvgDaysInStage()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("with no history recorded yet, the report says so instead of claiming measurement")
    void fallsBackHonestlyWhenNoHistoryExists() {
        deal(DealPipelineStage.NEGOTIATION, 100, 20, null);

        PipelineProgressionReportResponse report = run();

        assertThat(report.isHistoryMeasured()).isFalse();
        assertThat(stage(report, DealPipelineStage.NEGOTIATION).getDwellSamples()).isZero();
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

        // CLOSED_LOST has by far the longest dwell (299 days), but it is terminal: sitting in the
        // archive is not a bottleneck. Qualification (2 days) beats Inquiry (1 day).
        assertThat(run().getBottleneckStage()).isEqualTo("Qualification");
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
        assertThat(report.getWinRate()).isEqualTo(50.0);
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
        assertThat(report.getWinRate()).isZero();
        assertThat(report.getBottleneckStage()).isNull();
        assertThat(report.getPipelineValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
