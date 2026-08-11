package com.novax.leadora.application.usecase.reporting.scoring;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Every number the rep scorecard judges people against, in one editable place.
 *
 * <p>The defaults below are <b>starting points, not truths</b>. A score out of 100 looks objective
 * in a way the thresholds behind it are not, so they belong in configuration where a manager can
 * see them, argue with them and change them — not buried as literals in a scoring routine where
 * they would quietly acquire the authority of arithmetic.
 *
 * <p>Scoring is absolute rather than ranked-within-team: a percentile model always produces a top
 * performer, including in a period where the whole team missed. Rank is reported alongside the
 * score for context, but it does not produce the score.
 */
@Component
@ConfigurationProperties(prefix = "leadora.scoring")
@Getter
@Setter
public class ScoringProperties {

    /** Below this many settled outcomes, a rep's score is flagged as thin evidence. */
    private int minSample = 5;

    /**
     * Bayesian shrinkage strength: every rate is blended with the team average as though the rep
     * had this many extra, exactly average, events. Two deals won out of two is not a 100% win
     * rate, and without this the smallest sample on the team tops the table every time.
     */
    private double shrinkage = 5;

    /** A rep active fewer days than this in the period is measured but not ranked. */
    private int minActiveDays = 10;

    /** How many days early or late a deal may close and still count as forecast correctly. */
    private int forecastToleranceDays = 7;

    /**
     * Which roles this scorecard is about.
     *
     * <p>Not cosmetic. The underlying queries group by whoever owns a record or wrote an audit
     * entry, and every logged-in user writes audit entries — so without this filter a receptionist
     * who signed in on twenty days appeared on a sales leaderboard, scored 0 out of 100 for revenue
     * they were never expected to bring in, and dragged the team median down with them.
     */
    private List<String> scoredRoles = List.of("SALES");

    private Weights weights = new Weights();
    private Targets targets = new Targets();
    private Thresholds thresholds = new Thresholds();

    /** Axis weights. They should sum to 100; if an axis has no data its weight is redistributed. */
    @Getter
    @Setter
    public static class Weights {
        /** Results delivered. Deliberately not the whole score: it rewards whoever got the best leads. */
        private double outcome = 30;
        /** Conversion skill, which is largely independent of how many leads a rep was handed. */
        private double efficiency = 25;
        /** Speed of response and of closing. */
        private double velocity = 15;
        /** SLA, task hygiene, discount restraint — the part that is coachable. */
        private double discipline = 20;
        /** What the customer said. Low weight because the sample is small and self-selecting. */
        private double quality = 10;

        public double total() {
            return outcome + efficiency + velocity + discipline + quality;
        }
    }

    /**
     * Absolute targets, expressed per month and scaled to the length of the period being scored.
     * Set these to the real quota; the defaults are placeholders and will flatter or punish
     * everybody uniformly until they are.
     */
    @Getter
    @Setter
    public static class Targets {
        private double revenuePerMonth = 200_000_000;
        private double dealsWonPerMonth = 5;
    }

    /**
     * Each pair is {@code floor → target}: the value scoring 0 and the value scoring 100, with a
     * straight line between and clamping outside. Where lower is better the pair simply runs
     * downwards, which needs no special case.
     */
    @Getter
    @Setter
    public static class Thresholds {
        private double winRateFloor = 20;
        private double winRateTarget = 60;
        private double leadConversionFloor = 10;
        private double leadConversionTarget = 40;
        private double quotationAcceptanceFloor = 25;
        private double quotationAcceptanceTarget = 70;

        private double firstResponseHoursFloor = 48;
        private double firstResponseHoursTarget = 4;
        private double quotationTurnaroundHoursFloor = 72;
        private double quotationTurnaroundHoursTarget = 8;
        private double dealCycleDaysFloor = 60;
        private double dealCycleDaysTarget = 20;

        private double slaComplianceFloor = 60;
        private double slaComplianceTarget = 95;
        private double taskCompletionFloor = 50;
        private double taskCompletionTarget = 95;
        private double taskOverdueFloor = 40;
        private double taskOverdueTarget = 5;
        private double collectionOnTimeFloor = 50;
        private double collectionOnTimeTarget = 95;
        private double forecastAccuracyFloor = 30;
        private double forecastAccuracyTarget = 80;

        private double csatFloor = 3.0;
        private double csatTarget = 4.5;

        /**
         * Discount is scored on a bell rather than a line, because both ends are bad: heavy
         * discounting buys revenue at the company's expense, and a rep who never discounts at all
         * is often one who never closes a negotiation.
         */
        private double discountOptimumPct = 10;
        private double discountTolerancePct = 10;
    }
}
