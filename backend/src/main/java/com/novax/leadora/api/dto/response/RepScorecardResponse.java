package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UC-23.6 — Rep Performance Scorecard: per-rep metrics, a score out of 100, and the evidence behind
 * both.
 *
 * <p>The raw metrics travel <b>next to</b> the score on purpose. A single number is easy to act on
 * and easy to be wrong about; shipping it without the counts it came from invites a manager to
 * treat 62/100 as a fact about a person rather than a summary of twelve measurements, some of which
 * may rest on four events. {@code lowConfidence}, {@code ranked} and {@code dataGaps} exist for the
 * same reason.
 *
 * <p>Like the other cached report responses, the Lombok builders here carry
 * {@code @JsonDeserialize} — without it every Redis read throws, the configured
 * {@code CacheErrorHandler} swallows it as a warning, and the cache silently never hits.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = RepScorecardResponse.RepScorecardResponseBuilder.class)
public class RepScorecardResponse {

    /** The resolved period. Unlike the other reports this is never open-ended — see the use case. */
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String timezone;
    /** Length of the period in months, the scale absolute revenue and deal targets are held to. */
    private double periodMonths;

    /** The weights actually applied, echoed so a score can be recomputed by hand from this payload. */
    private Weights weights;
    /** Team-wide rates, both as context for a reader and as the mean each rep's rates shrink toward. */
    private TeamBaseline team;
    private List<RepScorecard> reps;

    @JsonPOJOBuilder(withPrefix = "")
    public static class RepScorecardResponseBuilder {
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = Weights.WeightsBuilder.class)
    public static class Weights {
        private double outcome;
        private double efficiency;
        private double velocity;
        private double discipline;
        private double quality;

        @JsonPOJOBuilder(withPrefix = "")
        public static class WeightsBuilder {
        }
    }

    @Getter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = TeamBaseline.TeamBaselineBuilder.class)
    public static class TeamBaseline {
        private int repCount;
        private Double winRate;
        private Double leadConversionRate;
        private Double quotationAcceptanceRate;
        private Double slaComplianceRate;
        private Double taskCompletionRate;
        private Double collectionOnTimeRate;
        private Double forecastAccuracyRate;
        private Double csat;
        private Double medianScore;

        @JsonPOJOBuilder(withPrefix = "")
        public static class TeamBaselineBuilder {
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = RepScorecard.RepScorecardBuilder.class)
    public static class RepScorecard {
        private UUID userId;
        private String name;

        private RepMetrics metrics;
        private RepScore score;

        /** Fewer settled outcomes than the configured minimum — read the score as a hint, not a verdict. */
        private boolean lowConfidence;
        /** False when the rep was active too few days in the period to be compared with the others. */
        private boolean ranked;
        /** Position by total score among ranked reps; null when {@code ranked} is false. */
        private Integer rank;

        /**
         * Plain-language notes about what could not be measured, e.g. "no SLA records in this
         * period". A metric that is missing and a metric that is bad look identical once they are
         * averaged into an axis, so the difference is stated rather than left to be inferred.
         */
        private List<String> dataGaps;
        private List<LostReason> topLostReasons;

        @JsonPOJOBuilder(withPrefix = "")
        public static class RepScorecardBuilder {
        }
    }

    /** Raw measurements. Nulls mean "not measurable in this period", never zero. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = RepMetrics.RepMetricsBuilder.class)
    public static class RepMetrics {
        // ── Outcome ──
        private BigDecimal revenue;
        private BigDecimal wonValue;
        private long dealsWon;
        private long dealsLost;
        private long bookingsConfirmed;
        private long leadsConverted;

        // ── Efficiency (numerators and denominators kept, so the rates can be checked) ──
        private long leadsCreated;
        private long cohortConverted;
        private Double leadConversionRate;
        private long dealsClosed;
        private Double winRate;
        private long quotationsCreated;
        private long quotationsAccepted;
        private Double quotationAcceptanceRate;

        // ── Velocity ──
        /** Hours from lead creation to the rep moving it off NEW. */
        private Double firstResponseHours;
        private long firstResponseSamples;
        /** Share of this period's leads that have a first-response event at all. */
        private Double firstResponseCoveragePct;
        private Double leadToConversionDays;
        private Double quotationTurnaroundHours;
        private Double dealCycleDays;

        // ── Discipline ──
        private long slaDecided;
        private long slaOnTime;
        private Double slaComplianceRate;
        private long tasksTotal;
        private long tasksCompleted;
        private long tasksOverdue;
        private Double taskCompletionRate;
        private Double taskOverdueRate;
        private Double avgDiscountPercent;
        private long quotationRoots;
        private long quotationRevisions;
        private Double revisionsPerQuotation;
        private long collectionTotal;
        private long collectionOnTime;
        private Double collectionOnTimeRate;
        private long forecastTotal;
        private long forecastHit;
        private Double forecastAccuracyRate;

        // ── Quality ──
        private Double csat;
        private long csatSamples;
        private Double csatAttitude;
        private Double csatSpeed;
        private Double csatAccuracy;

        /** Distinct days in the period on which this rep did anything the audit log recorded. */
        private long activeDays;
        /** Settled outcomes backing the score — the number {@code lowConfidence} is judged on. */
        private long sampleSize;

        @JsonPOJOBuilder(withPrefix = "")
        public static class RepMetricsBuilder {
        }
    }

    /** Axis scores out of 100. Null means the axis had nothing to measure and was left out. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = RepScore.RepScoreBuilder.class)
    public static class RepScore {
        private Double outcome;
        private Double efficiency;
        private Double velocity;
        private Double discipline;
        private Double quality;
        /** Weighted total, renormalised over the axes that had data. Null when nothing was measurable. */
        private Double total;
        /** Sum of the weights that actually contributed, out of 100. */
        private double weightCovered;

        @JsonPOJOBuilder(withPrefix = "")
        public static class RepScoreBuilder {
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = LostReason.LostReasonBuilder.class)
    public static class LostReason {
        private String reason;
        private long count;

        @JsonPOJOBuilder(withPrefix = "")
        public static class LostReasonBuilder {
        }
    }
}
