package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.PipelineProgressionReportResponse;
import com.novax.leadora.api.dto.response.PipelineProgressionReportResponse.StageRow;
import com.novax.leadora.common.util.ReportRange;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.common.util.ReportingUtils;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealStageHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UC-23.4 — View Sales Pipeline Progression Report.
 *
 * <p>Covers the cohort of deals <em>opened</em> in the period: "of the deals we started here, where
 * did they get to, and how long did each leg take". Time-in-stage comes from
 * {@code deal_stage_history}, so it is measured rather than inferred.
 */
@Service
@RequiredArgsConstructor
public class GetPipelineProgressionReportUseCase {

    /** Stages a deal is still moving through; the other two are terminal. */
    private static final Set<DealPipelineStage> OPEN_STAGES = EnumSet.of(
            DealPipelineStage.INQUIRY, DealPipelineStage.QUALIFICATION,
            DealPipelineStage.QUOTATION_SENT, DealPipelineStage.NEGOTIATION,
            DealPipelineStage.PENDING_CONFIRMATION, DealPipelineStage.BOOKING_CONFIRMED);

    private static final String BOTTLENECK_BASIS_MEASURED =
            "Ranked by measured average time deals spent in the stage, from recorded stage changes.";
    /**
     * Used only while a deployment has no recorded history yet — the first report after the
     * migration runs against a table that is still filling up.
     */
    private static final String BOTTLENECK_BASIS_PROXY =
            "No stage-change history recorded yet, so this falls back to average days since a deal "
                    + "was last updated (idle time), which understates a stage where deals are "
                    + "edited but not advanced.";

    private final DealRepository dealRepository;
    private final DealStageHistoryRepository dealStageHistoryRepository;
    private final ReportRangeFactory reportRangeFactory;

    @Cacheable(value = "pipeline-progression-report", key = "#from + '_' + #to", unless = "#result == null")
    @Transactional(readOnly = true)
    public PipelineProgressionReportResponse execute(LocalDate from, LocalDate to) {
        ReportRange range = reportRangeFactory.resolve(from, to);
        OffsetDateTime now = OffsetDateTime.now();

        List<Object[]> dealRows = dealRepository.findStageAgingRows(range.start(), range.endExclusive());

        Map<DealPipelineStage, StageAgg> byStage = new EnumMap<>(DealPipelineStage.class);
        List<UUID> dealIds = new ArrayList<>(dealRows.size());
        long totalDeals = 0;
        long unstaged = 0;

        for (Object[] row : dealRows) {
            totalDeals++;
            UUID dealId = (UUID) row[0];
            DealPipelineStage stage = (DealPipelineStage) row[1];
            if (dealId != null) {
                dealIds.add(dealId);
            }
            if (stage == null) {
                // pipeline_stage is NOT NULL in the schema, but a row that slipped past it must not
                // vanish: it would leave totalDeals larger than the stages it is broken into.
                unstaged++;
                continue;
            }
            BigDecimal value = ReportingUtils.toBigDecimal(row[2]);
            OffsetDateTime createdAt = (OffsetDateTime) row[3];
            OffsetDateTime closedAt = (OffsetDateTime) row[4];

            StageAgg agg = byStage.computeIfAbsent(stage, key -> new StageAgg());
            agg.count++;
            agg.value = agg.value.add(value);
            // A closed deal stops ageing at the moment it closed. Measuring to now() made last
            // quarter's won deals look older every day they sat in the archive.
            boolean closed = !OPEN_STAGES.contains(stage);
            agg.totalAgeDays += daysBetween(createdAt, closed && closedAt != null ? closedAt : now);
        }

        StageDwell dwell = measureDwellTimes(dealIds, now);
        long closedWon = countIn(byStage, DealPipelineStage.CLOSED_WON);
        long closedLost = countIn(byStage, DealPipelineStage.CLOSED_LOST);
        long openDeals = totalDeals - closedWon - closedLost;

        BigDecimal pipelineValue = BigDecimal.ZERO;
        List<StageRow> stages = new ArrayList<>();
        String bottleneck = null;
        double worstDwell = -1;

        for (DealPipelineStage stage : DealPipelineStage.values()) {
            StageAgg agg = byStage.getOrDefault(stage, new StageAgg());
            boolean closed = !OPEN_STAGES.contains(stage);
            double avgAge = agg.count == 0 ? 0 : (double) agg.totalAgeDays / agg.count;
            double avgDwell = dwell.averageFor(stage);

            if (!closed) {
                pipelineValue = pipelineValue.add(agg.value);
                // Bottleneck is ranked on dwell time across every deal that passed through the
                // stage, not on the deals sitting in it now — a stage can be slow and still be
                // empty at the instant the report runs.
                if (dwell.sampleCountFor(stage) > 0 && avgDwell > worstDwell) {
                    worstDwell = avgDwell;
                    bottleneck = label(stage);
                }
            }

            stages.add(StageRow.builder()
                    .stage(stage.name())
                    .label(label(stage))
                    .count(agg.count)
                    .value(agg.value)
                    .avgAgeDays(ReportingUtils.round2(avgAge))
                    .avgDaysInStage(ReportingUtils.round2(avgDwell))
                    .dwellSamples(dwell.sampleCountFor(stage))
                    .closed(closed)
                    .build());
        }

        if (unstaged > 0) {
            stages.add(StageRow.builder()
                    .stage("UNKNOWN")
                    .label("No stage set")
                    .count(unstaged)
                    .value(BigDecimal.ZERO)
                    .closed(false)
                    .build());
        }

        return PipelineProgressionReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .totalDeals(totalDeals)
                .openDeals(openDeals)
                .closedWon(closedWon)
                .closedLost(closedLost)
                .winRate(ReportingUtils.calculateRate(closedWon, closedWon + closedLost))
                .pipelineValue(pipelineValue)
                .bottleneckStage(bottleneck)
                .bottleneckBasis(bottleneck == null
                        ? null
                        : (dwell.measured ? BOTTLENECK_BASIS_MEASURED : BOTTLENECK_BASIS_PROXY))
                .historyMeasured(dwell.measured)
                .stages(stages)
                .build();
    }

    /**
     * Turns each deal's recorded transitions into time spent per stage.
     *
     * <p>The dwell for a stage is the gap between entering it and leaving it; the stage a deal is
     * currently sitting in is measured up to now, so a deal stuck for three weeks counts as stuck
     * rather than being ignored until it finally moves.
     */
    private StageDwell measureDwellTimes(List<UUID> dealIds, OffsetDateTime now) {
        StageDwell dwell = new StageDwell();
        if (dealIds.isEmpty()) {
            return dwell;
        }

        List<Object[]> rows = dealStageHistoryRepository.findTransitionsForDeals(dealIds);
        if (rows.isEmpty()) {
            return dwell;
        }
        dwell.measured = true;

        // Rows arrive ordered by (dealId, changedAt), so a deal's journey is a contiguous run and
        // consecutive rows can be paired without sorting anything here.
        UUID currentDeal = null;
        DealPipelineStage pendingStage = null;
        OffsetDateTime pendingSince = null;

        for (Object[] row : rows) {
            UUID dealId = (UUID) row[0];
            DealPipelineStage toStage = (DealPipelineStage) row[2];
            OffsetDateTime changedAt = (OffsetDateTime) row[3];

            if (!dealId.equals(currentDeal)) {
                // Close out the previous deal: it is still sitting in its last recorded stage.
                dwell.add(pendingStage, pendingSince, now);
                currentDeal = dealId;
                pendingStage = toStage;
                pendingSince = changedAt;
                continue;
            }
            dwell.add(pendingStage, pendingSince, changedAt);
            pendingStage = toStage;
            pendingSince = changedAt;
        }
        dwell.add(pendingStage, pendingSince, now);

        return dwell;
    }

    private long countIn(Map<DealPipelineStage, StageAgg> byStage, DealPipelineStage stage) {
        StageAgg agg = byStage.get(stage);
        return agg == null ? 0 : agg.count;
    }

    private long daysBetween(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, Duration.between(start, end).toDays());
    }

    private String label(DealPipelineStage stage) {
        return switch (stage) {
            case INQUIRY -> "Inquiry";
            case QUALIFICATION -> "Qualification";
            case QUOTATION_SENT -> "Quotation Sent";
            case NEGOTIATION -> "Negotiation";
            case PENDING_CONFIRMATION -> "Pending Confirmation";
            case BOOKING_CONFIRMED -> "Booking Confirmed";
            case CLOSED_WON -> "Closed Won";
            case CLOSED_LOST -> "Closed Lost";
        };
    }

    private static class StageAgg {
        long count;
        BigDecimal value = BigDecimal.ZERO;
        long totalAgeDays;
    }

    /** Accumulated dwell time per stage, in hours, plus how many legs each average is built from. */
    private static final class StageDwell {
        /** False when no history exists yet, so callers can label the number as a fallback. */
        boolean measured;

        private final Map<DealPipelineStage, double[]> hours = new HashMap<>();
        private final Map<DealPipelineStage, Long> samples = new HashMap<>();

        void add(DealPipelineStage stage, OffsetDateTime from, OffsetDateTime to) {
            if (stage == null || from == null || to == null || to.isBefore(from)) {
                return;
            }
            double legHours = Duration.between(from, to).toMinutes() / 60.0;
            hours.computeIfAbsent(stage, key -> new double[1])[0] += legHours;
            samples.merge(stage, 1L, Long::sum);
        }

        double averageFor(DealPipelineStage stage) {
            long count = sampleCountFor(stage);
            if (count == 0) {
                return 0;
            }
            return hours.get(stage)[0] / count / 24.0;
        }

        long sampleCountFor(DealPipelineStage stage) {
            Long count = samples.get(stage);
            return count == null ? 0L : count;
        }
    }
}
