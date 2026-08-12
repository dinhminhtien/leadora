package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.4 — Sales Pipeline Progression Report. Deal distribution and aging across pipeline stages,
 * plus closed-won / closed-lost outcomes and a stall indicator.
 *
 * <p>See {@link SalesPerformanceReportResponse} for why the Jackson builder wiring is required.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = PipelineProgressionReportResponse.PipelineProgressionReportResponseBuilder.class)
public class PipelineProgressionReportResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    private long totalDeals;
    private long openDeals;
    private long closedWon;
    private long closedLost;

    /**
     * Won / (won + lost) <b>within the opening cohort</b> — of the deals started in this period, the
     * share of the ones that have since settled that went our way. Null when nothing has settled
     * yet, because "no decisions" is not a zero win rate.
     *
     * <p>Deliberately <em>not</em> called {@code winRate}: {@link SalesPerformanceReportResponse}
     * publishes a figure of that name over the deals <em>closed</em> in the period, whenever they
     * were opened. The two answer different questions and routinely differ — on a 60-day window this
     * cohort read 100% (8 won, 0 lost) while the closing-period figure read 75% (9 won, 3 lost),
     * because the three losses were opened before the window and closed inside it. Sharing a name
     * across two screens is how a manager ends up trusting whichever tab they opened last.
     */
    private Double cohortWinRate;

    /** The denominator behind {@link #cohortWinRate} — cohort deals that have actually settled. */
    private long cohortDecided;

    /**
     * Deals closed inside the period but opened before it, so they sit outside this cohort entirely.
     * Published so the gap against the closing-period win rate is a stated number rather than a
     * discrepancy the reader has to discover.
     */
    private long closedHereOpenedEarlier;

    private BigDecimal pipelineValue;  // expected revenue of still-open deals opened in the period

    /**
     * The open stage deals take longest to get out of, or null.
     *
     * <p>Ranked by {@code avgDaysToMoveOn}, not by deal age. Ranking by age is structurally biased:
     * a deal in NEGOTIATION has by definition lived through every earlier stage, so the oldest
     * deals always pile up at the end of the funnel and the "bottleneck" would point there
     * regardless of where work actually stalls.
     *
     * <p>Ranked on <em>completed</em> legs and shrunk toward the overall average in proportion to
     * how few of them a stage has, the same way {@code ScoringEngine} treats a rep's rates. Without
     * the shrink a stage crossed twice outranked one crossed nineteen times: on live data the
     * bottleneck was Pending Confirmation on two visits, one of which had not finished.
     */
    private String bottleneckStage;

    /**
     * Where {@link StageRow#getAvgDaysToMoveOn()} comes from and how thin it is, so the UI can
     * qualify the claim at the point it makes it.
     */
    private String bottleneckBasis;

    /**
     * True when at least one recorded stage change backs the timings. There is no longer an
     * idle-time fallback: with no history the timings are absent rather than approximated, and
     * {@link #getDataGaps()} says so. Partial coverage is reported there too — this flag alone does
     * not distinguish three measured deals from fifty.
     */
    private boolean historyMeasured;

    /**
     * What this period could not establish, in the words a reader needs to discount a figure by —
     * same contract as {@link QuotationOutcomeReportResponse#getDataGaps()}.
     */
    private List<String> dataGaps;

    private List<StageRow> stages;

    @JsonPOJOBuilder(withPrefix = "")
    public static class PipelineProgressionReportResponseBuilder {
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = StageRow.StageRowBuilder.class)
    public static class StageRow {
        private String stage;          // enum name
        private String label;          // human label
        private long count;
        private BigDecimal value;      // sum of expected revenue in this stage

        /**
         * Average lifetime of the deals in this stage, in days. For open stages that is
         * created → now; for CLOSED_WON / CLOSED_LOST it is created → closed, so a deal settled
         * last quarter stops ageing instead of drifting upward forever.
         */
        private double avgAgeDays;

        // Time in a stage is two measurements, not one, and the old single average blended them.
        //
        // A visit that has ended has a known duration. A visit still running has only a lower bound
        // — the deal may leave tomorrow or never. Averaging both together produced a number that
        // moved with the size of the backlog rather than with how long the work takes: on live data
        // Qualification showed 6.01 days while every deal that actually crossed it did so in 0.22,
        // the difference being seven deals still parked there. One figure cannot answer both "how
        // long does this stage take" and "how big is the queue", so there are two.

        /**
         * Average days to get <em>out</em> of this stage, over visits that have ended. Null when no
         * deal in the cohort has completed the stage — unknown, not zero.
         */
        private Double avgDaysToMoveOn;

        /** How many ended visits {@link #avgDaysToMoveOn} averages over. */
        private long completedLegs;

        /** Cohort deals sitting in this stage right now. */
        private long dealsWaitingNow;

        /** How long those deals have been waiting, on average. Null when none are. */
        private Double avgDaysWaiting;

        private boolean closed;        // CLOSED_WON / CLOSED_LOST

        @JsonPOJOBuilder(withPrefix = "")
        public static class StageRowBuilder {
        }
    }
}
