package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.4 — Sales Pipeline Progression Report. Deal distribution and aging across pipeline stages,
 * plus closed-won / closed-lost outcomes and a bottleneck indicator (the open stage where deals sit
 * longest). Aggregated server-side over deals for an optional date range.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineProgressionReportResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    private long totalDeals;
    private long openDeals;
    private long closedWon;
    private long closedLost;
    private double winRate;            // % won / (won + lost)
    private BigDecimal pipelineValue;  // expected revenue of still-open deals

    /** The still-open stage where deals have aged the longest (likely bottleneck), or null. */
    private String bottleneckStage;

    private List<StageRow> stages;

    @Getter
    @Builder
    public static class StageRow {
        private String stage;          // enum name
        private String label;          // human label
        private long count;
        private BigDecimal value;      // sum of expected revenue in this stage
        private double avgAgeDays;     // avg days since a deal entered the pipeline (created)
        private boolean closed;        // CLOSED_WON / CLOSED_LOST
    }
}
