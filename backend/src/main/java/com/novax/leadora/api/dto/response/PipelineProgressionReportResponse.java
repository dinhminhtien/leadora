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
    private double winRate;            // % won / (won + lost)
    private BigDecimal pipelineValue;  // expected revenue of still-open deals

    /**
     * The open stage deals spend longest in, or null.
     *
     * <p>Ranked by {@code avgDaysInStage}, not by deal age. Ranking by age is structurally biased:
     * a deal in NEGOTIATION has by definition lived through every earlier stage, so the oldest
     * deals always pile up at the end of the funnel and the "bottleneck" would point there
     * regardless of where work actually stalls.
     */
    private String bottleneckStage;

    /** Where {@code avgDaysInStage} comes from, so the UI can qualify the claim it makes. */
    private String bottleneckBasis;

    /**
     * True when the timings come from recorded stage changes; false while no history exists yet and
     * the report falls back to idle time.
     */
    private boolean historyMeasured;

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

        /**
         * Average days a deal spends in this stage, measured from {@code deal_stage_history}.
         *
         * <p>Counts every deal in the cohort that passed <em>through</em> the stage, not just the
         * ones sitting in it right now — a slow stage can be empty at the instant the report runs.
         * The stage a deal currently occupies is measured up to now, so a deal stuck for three
         * weeks counts as stuck rather than being ignored until it finally moves.
         */
        private double avgDaysInStage;

        /** How many stage visits {@code avgDaysInStage} averages over. Zero means no data. */
        private long dwellSamples;

        private boolean closed;        // CLOSED_WON / CLOSED_LOST

        @JsonPOJOBuilder(withPrefix = "")
        public static class StageRowBuilder {
        }
    }
}
