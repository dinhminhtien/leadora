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
 *
 * <p>Two consequences of that cohort choice are published rather than left implicit. Deals closed in
 * the period but opened before it are outside this report — counted as
 * {@code closedHereOpenedEarlier} so the gap against UC-23.1's closing-period win rate is a stated
 * number. And the rate here is {@code cohortWinRate}, not {@code winRate}, because it is a different
 * measurement over a different population than the one that name already refers to.
 */
@Service
@RequiredArgsConstructor
public class GetPipelineProgressionReportUseCase {

    /** Stages a deal is still moving through; the other two are terminal. */
    private static final Set<DealPipelineStage> OPEN_STAGES = EnumSet.of(
            DealPipelineStage.INQUIRY, DealPipelineStage.QUALIFICATION,
            DealPipelineStage.QUOTATION_SENT, DealPipelineStage.NEGOTIATION,
            DealPipelineStage.PENDING_CONFIRMATION, DealPipelineStage.BOOKING_CONFIRMED);

    /**
     * Pseudo-observations mixed into every stage average before ranking, pulling a thinly-evidenced
     * stage toward the overall pace. Matches the role of {@code ScoringProperties.shrinkage} for rep
     * rates; three is the point where a stage has been crossed often enough to speak for itself.
     */
    private static final double BOTTLENECK_SHRINKAGE = 3.0;

    /** A queue worth mentioning in {@code dataGaps} even when the stage clears quickly. */
    private static final long BACKLOG_DEALS_THRESHOLD = 3;

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
        long decided = closedWon + closedLost;

        double overallPace = dwell.averageCompletedLegAcross(OPEN_STAGES);
        BigDecimal pipelineValue = BigDecimal.ZERO;
        List<StageRow> stages = new ArrayList<>();
        String bottleneck = null;
        long bottleneckLegs = 0;
        Double bottleneckDays = null;
        double worstRanked = -1;

        for (DealPipelineStage stage : DealPipelineStage.values()) {
            StageAgg agg = byStage.getOrDefault(stage, new StageAgg());
            boolean closed = !OPEN_STAGES.contains(stage);
            double avgAge = agg.count == 0 ? 0 : agg.totalAgeDays / agg.count;
            Double moveOn = dwell.averageCompletedFor(stage);
            long completedLegs = dwell.completedLegsFor(stage);

            if (!closed) {
                pipelineValue = pipelineValue.add(agg.value);
                // Ranked on completed legs only: a leg still running is a lower bound, and mixing
                // the two ranked stages by how big their queue is rather than by how slow they are.
                // Shrinking toward the overall pace keeps a stage crossed once from outranking one
                // crossed nineteen times on the strength of a single slow deal.
                // A stage nobody measurably lingered in is not a bottleneck. Without this, data
                // whose transitions all land in the same minute — a seeded demo, or a rep clicking
                // through the pipeline in one sitting — still produced "deals take longest to get
                // out of this stage (0.0 days over 1 completed stage exit(s))".
                if (moveOn != null && ReportingUtils.round2(moveOn) > 0) {
                    double ranked = (completedLegs * moveOn + BOTTLENECK_SHRINKAGE * overallPace)
                            / (completedLegs + BOTTLENECK_SHRINKAGE);
                    if (ranked > worstRanked) {
                        worstRanked = ranked;
                        bottleneck = label(stage);
                        bottleneckLegs = completedLegs;
                        bottleneckDays = moveOn;
                    }
                }
            }

            // Time in a terminal stage is just time since the deal settled, so it is left null
            // rather than published as though the archive were a step in the process.
            stages.add(StageRow.builder()
                    .stage(stage.name())
                    .label(label(stage))
                    .count(agg.count)
                    .value(agg.value)
                    .avgAgeDays(ReportingUtils.round2(avgAge))
                    .avgDaysToMoveOn(closed ? null : round2OrNull(moveOn))
                    .completedLegs(closed ? 0 : completedLegs)
                    .dealsWaitingNow(closed ? 0 : dwell.openLegsFor(stage))
                    .avgDaysWaiting(closed ? null : round2OrNull(dwell.averageOpenFor(stage)))
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

        long closedHereOpenedEarlier =
                dealRepository.countClosedInRangeOpenedBefore(range.start(), range.endExclusive());

        return PipelineProgressionReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .totalDeals(totalDeals)
                .openDeals(openDeals)
                .closedWon(closedWon)
                .closedLost(closedLost)
                .cohortWinRate(decided == 0 ? null : ReportingUtils.calculateRate(closedWon, decided))
                .cohortDecided(decided)
                .closedHereOpenedEarlier(closedHereOpenedEarlier)
                .pipelineValue(pipelineValue)
                .bottleneckStage(bottleneck)
                .bottleneckBasis(bottleneckBasis(bottleneck, bottleneckDays, bottleneckLegs))
                .historyMeasured(dwell.measured)
                .dataGaps(dataGaps(totalDeals, decided, closedHereOpenedEarlier, unstaged,
                        dwell, stages, bottleneck))
                .stages(stages)
                .build();
    }

    /** Says what the bottleneck claim rests on, including how thin the evidence is. */
    private String bottleneckBasis(String bottleneck, Double days, long legs) {
        if (bottleneck == null) {
            return null;
        }
        String basis = "Ranked by measured time to leave the stage (" + ReportingUtils.round2(days)
                + " days over " + legs + " completed stage exit(s)), from recorded stage changes. "
                + "Deals still sitting in a stage are excluded from this average because their stay "
                + "has not finished; they are counted under Waiting now instead.";
        if (legs < BOTTLENECK_SHRINKAGE) {
            basis += " This stage has not been crossed often enough to rank confidently, so the "
                    + "call is provisional.";
        }
        return basis;
    }

    /**
     * What this period could not establish, in the words a reader needs to discount a figure by.
     *
     * <p>Each entry is a place the report would otherwise publish a confident number over almost
     * nothing, or stay silent about a population it cannot see.
     */
    private List<String> dataGaps(long totalDeals, long decided, long closedHereOpenedEarlier,
                                  long unstaged, StageDwell dwell, List<StageRow> stages,
                                  String bottleneck) {
        List<String> gaps = new ArrayList<>();

        if (totalDeals == 0) {
            gaps.add("No deals were opened in this period, so the cohort figures are empty rather "
                    + "than zero.");
            return gaps;
        }
        if (decided == 0) {
            gaps.add("None of the " + totalDeals + " deals opened in this period has settled yet, "
                    + "so there is no cohort win rate to report.");
        }
        if (closedHereOpenedEarlier > 0) {
            gaps.add(closedHereOpenedEarlier + " deal(s) closed during this period were opened "
                    + "before it and are not in this cohort. That is why the cohort win rate here "
                    + "can differ from the win rate on the Sales Performance report, which counts "
                    + "deals by when they closed.");
        }
        if (!dwell.measured) {
            gaps.add("No stage-change history has been recorded for this cohort, so no stage timing "
                    + "can be established and no bottleneck is named.");
        } else if (dwell.dealsWithHistory < totalDeals) {
            // Partial coverage is the quiet version of the same problem: the timings look measured
            // because some of them are. Stage history only started being written when the feature
            // shipped, so any window reaching back before that is a mix.
            gaps.add("Stage timings cover " + dwell.dealsWithHistory + " of " + totalDeals
                    + " deals in this cohort — the rest have no recorded stage changes, so they are "
                    + "counted in the totals but contribute nothing to the timings or the "
                    + "bottleneck.");
        }

        // Independent of history coverage: crossings exist, but every one of them rounds to nothing,
        // so the ranking was suppressed rather than won. Every other path that withholds the
        // bottleneck explains itself, and a banner that simply vanishes is the silence this list
        // exists to remove.
        if (bottleneck == null && dwell.measured && dwell.hasAnyCompletedLegIn(OPEN_STAGES)) {
            gaps.add("Every recorded stage crossing in this cohort completed in under half a day, "
                    + "so no stage stands out as a bottleneck. Transitions logged in bulk — a "
                    + "migration or a seeded dataset — look like this.");
        }
        if (unstaged > 0) {
            gaps.add(unstaged + " deal(s) carry no pipeline stage and are listed separately rather "
                    + "than folded into a stage.");
        }

        // A queue is not a slow stage, but it is what a reader will otherwise read the stage timing
        // as. Naming it keeps the two apart — and catches the case the bottleneck ranking cannot
        // see by construction: deals piling up in a stage that, on the few legs that did finish,
        // looks fast. Ranking on completed legs alone would report that stage as one of the
        // quickest in the pipeline.
        for (StageRow row : stages) {
            // Two different thresholds on purpose. "Nothing has ever left this stage" is a hole in
            // the headline — that stage is excluded from the bottleneck ranking by construction —
            // so it is disclosed however short the queue. "The queue has outlasted the stage's usual
            // pace" is a judgement about size, so it waits for a queue worth the words.
            long minimumQueue = row.getAvgDaysToMoveOn() == null ? 1 : BACKLOG_DEALS_THRESHOLD;
            if (row.isClosed() || row.getDealsWaitingNow() < minimumQueue) {
                continue;
            }
            Double waiting = row.getAvgDaysWaiting();
            Double moveOn = row.getAvgDaysToMoveOn();
            if (moveOn == null) {
                gaps.add(row.getDealsWaitingNow() + " deal(s) are sitting in " + row.getLabel()
                        + " and none has left it yet, so its time to move on is unknown rather "
                        + "than fast, and it cannot be ranked as the bottleneck.");
            } else if (waiting != null && waiting > moveOn) {
                gaps.add(row.getDealsWaitingNow() + " deal(s) have been waiting in " + row.getLabel()
                        + " for " + waiting + " days on average, longer than the " + moveOn
                        + " days the stage usually takes to clear — a queue the stage timing does "
                        + "not show.");
            }
        }
        return gaps;
    }

    private static Double round2OrNull(Double value) {
        return value == null ? null : ReportingUtils.round2(value);
    }

    /**
     * Turns each deal's recorded transitions into time spent per stage.
     *
     * <p>The time in a stage is the gap between entering it and leaving it. A stage a deal is still
     * sitting in has no leaving time, so it is measured up to now and filed as an <em>open</em> leg:
     * counted as a queue, kept out of the crossing-time average, and therefore out of the bottleneck
     * ranking. Mixing the two produced an average that tracked the size of the backlog rather than
     * how long the work took.
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
                // Close out the previous deal: it is still sitting in its last recorded stage, so
                // that leg is open-ended rather than a completed crossing.
                dwell.addOpen(pendingStage, pendingSince, now);
                // Guarded: the first row opens a deal rather than closing one, so counting it here
                // would credit the cohort with a deal that has not been walked yet.
                if (currentDeal != null) {
                    dwell.dealsWithHistory++;
                }
                currentDeal = dealId;
                pendingStage = toStage;
                pendingSince = changedAt;
                continue;
            }
            dwell.addCompleted(pendingStage, pendingSince, changedAt);
            pendingStage = toStage;
            pendingSince = changedAt;
        }
        dwell.addOpen(pendingStage, pendingSince, now);
        dwell.dealsWithHistory++;

        return dwell;
    }

    private long countIn(Map<DealPipelineStage, StageAgg> byStage, DealPipelineStage stage) {
        StageAgg agg = byStage.get(stage);
        return agg == null ? 0 : agg.count;
    }

    /**
     * Fractional days. {@code Duration.toDays()} truncates, which quietly shaved up to a day off
     * every deal before the averaging and left this column reading coarser than the stage timings
     * beside it.
     */
    private double daysBetween(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, Duration.between(start, end).toMinutes() / 1440.0);
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
        double totalAgeDays;
    }

    /**
     * Time per stage, kept in two separate books.
     *
     * <p>A leg that ended has a known length. A leg still running has only a lower bound — the deal
     * might move tomorrow or sit for a month. They answer different questions, so they are never
     * added together: {@code completed} measures how long the stage takes, {@code open} measures how
     * long the current queue has been waiting.
     */
    private static final class StageDwell {
        /** False when no history exists at all, so callers can say so instead of showing zeroes. */
        boolean measured;

        /**
         * Cohort deals that contributed at least one recorded transition. Every stage timing is
         * built from these and from no others, so a report whose cohort is larger is measuring a
         * subset — which is worth saying out loud rather than leaving in the difference between two
         * columns.
         */
        long dealsWithHistory;

        private final Map<DealPipelineStage, Leg> completed = new HashMap<>();
        private final Map<DealPipelineStage, Leg> open = new HashMap<>();

        void addCompleted(DealPipelineStage stage, OffsetDateTime from, OffsetDateTime to) {
            accumulate(completed, stage, from, to);
        }

        void addOpen(DealPipelineStage stage, OffsetDateTime from, OffsetDateTime to) {
            accumulate(open, stage, from, to);
        }

        private void accumulate(Map<DealPipelineStage, Leg> book, DealPipelineStage stage,
                                OffsetDateTime from, OffsetDateTime to) {
            if (stage == null || from == null || to == null || to.isBefore(from)) {
                return;
            }
            Leg leg = book.computeIfAbsent(stage, key -> new Leg());
            leg.days += Duration.between(from, to).toMinutes() / 1440.0;
            leg.count++;
        }

        /** Null rather than zero: no completed crossing means unknown, not instant. */
        Double averageCompletedFor(DealPipelineStage stage) {
            return average(completed, stage);
        }

        Double averageOpenFor(DealPipelineStage stage) {
            return average(open, stage);
        }

        private Double average(Map<DealPipelineStage, Leg> book, DealPipelineStage stage) {
            Leg leg = book.get(stage);
            return leg == null || leg.count == 0 ? null : leg.days / leg.count;
        }

        /** True when at least one of the given stages was crossed and finished. */
        boolean hasAnyCompletedLegIn(Set<DealPipelineStage> stages) {
            for (Map.Entry<DealPipelineStage, Leg> entry : completed.entrySet()) {
                if (stages.contains(entry.getKey()) && entry.getValue().count > 0) {
                    return true;
                }
            }
            return false;
        }

        long completedLegsFor(DealPipelineStage stage) {
            Leg leg = completed.get(stage);
            return leg == null ? 0L : leg.count;
        }

        long openLegsFor(DealPipelineStage stage) {
            Leg leg = open.get(stage);
            return leg == null ? 0L : leg.count;
        }

        /**
         * The pace a thinly-crossed stage is shrunk toward when ranking the bottleneck.
         *
         * <p>Restricted to the stages being ranked. A terminal stage only earns a completed leg if a
         * deal leaves the archive, which {@code DealValidation} currently forbids — but a shrink
         * target drawn from a population wider than the one being ranked is wrong on principle, and
         * a single 60-day stay in Closed Lost would drag the target far enough to hand the
         * bottleneck to whichever open stage happened to be thinnest.
         */
        double averageCompletedLegAcross(Set<DealPipelineStage> stages) {
            double days = 0;
            long count = 0;
            for (Map.Entry<DealPipelineStage, Leg> entry : completed.entrySet()) {
                if (!stages.contains(entry.getKey())) {
                    continue;
                }
                days += entry.getValue().days;
                count += entry.getValue().count;
            }
            return count == 0 ? 0 : days / count;
        }

        private static final class Leg {
            double days;
            long count;
        }
    }
}
