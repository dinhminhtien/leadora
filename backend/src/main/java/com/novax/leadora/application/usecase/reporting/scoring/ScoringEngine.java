package com.novax.leadora.application.usecase.reporting.scoring;

import com.novax.leadora.api.dto.response.RepScorecardResponse.RepMetrics;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepScore;
import com.novax.leadora.api.dto.response.RepScorecardResponse.TeamBaseline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a rep's measurements into five axis scores and a total out of 100.
 *
 * <p>Deliberately free of Spring, JPA and the clock: everything it needs arrives as arguments, so
 * every rule below can be tested by calling a method rather than by staging a database. The rules
 * are the part most likely to be argued about, so they are the part that has to be easiest to
 * inspect.
 *
 * <h2>Three corrections that matter more than the weights</h2>
 *
 * <p><b>Small samples.</b> Two deals won out of two is not a 100% win rate. Every rate is blended
 * with the team average in proportion to how little evidence there is, so a rep with two decisions
 * scores near the team mean and a rep with forty scores near their own number. Without this the
 * leaderboard is sorted by inexperience.
 *
 * <p><b>Missing data is not zero.</b> A rep with no SLA records in the period has no SLA score, not
 * a score of nought. Axes with nothing to measure are dropped and the remaining weights are
 * renormalised, so the total still means "out of 100" and the gap is reported separately.
 *
 * <p><b>Some things are bad at both ends.</b> Discount is scored on a bell curve: 40% average
 * discount is buying revenue from the company's own margin, and 0% across a whole period usually
 * means deals that never reached a negotiation, not iron discipline.
 */
@Component
@RequiredArgsConstructor
public class ScoringEngine {

    private final ScoringProperties props;

    /**
     * @param metrics  one rep's raw measurements
     * @param team     the team's rates, which thin evidence is pulled toward
     * @param months   length of the scored period, used to scale the absolute revenue/deal targets
     */
    public RepScore score(RepMetrics metrics, TeamBaseline team, double months) {
        ScoringProperties.Thresholds t = props.getThresholds();
        double scale = months <= 0 ? 1 : months;

        Double outcome = average(
                metrics.getRevenue() == null ? null
                        : linear(toDouble(metrics.getRevenue()), 0, props.getTargets().getRevenuePerMonth() * scale),
                linear((double) metrics.getDealsWon(), 0, props.getTargets().getDealsWonPerMonth() * scale));

        Double efficiency = average(
                rateScore(metrics.getCohortConverted(), metrics.getLeadsCreated(), team.getLeadConversionRate(),
                        t.getLeadConversionFloor(), t.getLeadConversionTarget()),
                rateScore(metrics.getDealsWon(), metrics.getDealsClosed(), team.getWinRate(),
                        t.getWinRateFloor(), t.getWinRateTarget()),
                rateScore(metrics.getQuotationsAccepted(), metrics.getQuotationsCreated(),
                        team.getQuotationAcceptanceRate(),
                        t.getQuotationAcceptanceFloor(), t.getQuotationAcceptanceTarget()));

        Double velocity = average(
                linear(metrics.getFirstResponseHours(), t.getFirstResponseHoursFloor(), t.getFirstResponseHoursTarget()),
                linear(metrics.getQuotationTurnaroundHours(),
                        t.getQuotationTurnaroundHoursFloor(), t.getQuotationTurnaroundHoursTarget()),
                linear(metrics.getDealCycleDays(), t.getDealCycleDaysFloor(), t.getDealCycleDaysTarget()));

        Double discipline = average(
                rateScore(metrics.getSlaOnTime(), metrics.getSlaDecided(), team.getSlaComplianceRate(),
                        t.getSlaComplianceFloor(), t.getSlaComplianceTarget()),
                rateScore(metrics.getTasksCompleted(), metrics.getTasksTotal(), team.getTaskCompletionRate(),
                        t.getTaskCompletionFloor(), t.getTaskCompletionTarget()),
                metrics.getTasksTotal() == 0 ? null
                        : linear(metrics.getTaskOverdueRate(), t.getTaskOverdueFloor(), t.getTaskOverdueTarget()),
                // Punctuality is not derivable from the overdue rate, and leaving it out was how a
                // rep who finished every task days late still scored full marks here: a completed
                // task is no longer open, so it left the overdue count the moment it was done.
                rateScore(metrics.getTasksOnTime(), metrics.getTasksOnTime() + metrics.getTasksLate(),
                        team.getTaskPunctualityRate(),
                        t.getTaskPunctualityFloor(), t.getTaskPunctualityTarget()),
                rateScore(metrics.getCollectionOnTime(), metrics.getCollectionTotal(), team.getCollectionOnTimeRate(),
                        t.getCollectionOnTimeFloor(), t.getCollectionOnTimeTarget()),
                rateScore(metrics.getForecastHit(), metrics.getForecastTotal(), team.getForecastAccuracyRate(),
                        t.getForecastAccuracyFloor(), t.getForecastAccuracyTarget()),
                bell(metrics.getAvgDiscountPercent(), t.getDiscountOptimumPct(), t.getDiscountTolerancePct()));

        Double quality = linear(metrics.getCsat(), t.getCsatFloor(), t.getCsatTarget());

        return weigh(outcome, efficiency, velocity, discipline, quality);
    }

    /** Applies the axis weights, renormalising over whichever axes had something to measure. */
    private RepScore weigh(Double outcome, Double efficiency, Double velocity,
                           Double discipline, Double quality) {
        ScoringProperties.Weights w = props.getWeights();
        double weighted = 0;
        double covered = 0;
        if (outcome != null) {
            weighted += outcome * w.getOutcome();
            covered += w.getOutcome();
        }
        if (efficiency != null) {
            weighted += efficiency * w.getEfficiency();
            covered += w.getEfficiency();
        }
        if (velocity != null) {
            weighted += velocity * w.getVelocity();
            covered += w.getVelocity();
        }
        if (discipline != null) {
            weighted += discipline * w.getDiscipline();
            covered += w.getDiscipline();
        }
        if (quality != null) {
            weighted += quality * w.getQuality();
            covered += w.getQuality();
        }
        return RepScore.builder()
                .outcome(round2(outcome))
                .efficiency(round2(efficiency))
                .velocity(round2(velocity))
                .discipline(round2(discipline))
                .quality(round2(quality))
                .total(covered <= 0 ? null : round2(weighted / covered))
                .weightCovered(round2d(covered))
                .build();
    }

    /**
     * A rate, corrected for how much evidence stands behind it, then scored against its thresholds.
     *
     * <p>Returns null for a rep with no events of this kind at all: nothing happened is not the same
     * claim as nothing went well.
     */
    public Double rateScore(long hits, long total, Double teamRate, double floor, double target) {
        if (total <= 0) {
            return null;
        }
        return linear(shrunkRate(hits, total, teamRate), floor, target);
    }

    /**
     * Blends the observed rate with the team's, as though the rep had {@code shrinkage} additional
     * events that went exactly averagely. With no team rate to lean on it degrades to the raw rate.
     */
    public double shrunkRate(long hits, long total, Double teamRate) {
        if (total <= 0) {
            return 0;
        }
        if (teamRate == null) {
            return 100.0 * hits / total;
        }
        double m = props.getShrinkage();
        return 100.0 * (hits + m * (teamRate / 100.0)) / (total + m);
    }

    /**
     * Straight line from {@code floor} (0 points) to {@code target} (100 points), clamped outside.
     * When lower is better the pair simply runs downwards; no separate "inverse" function is needed,
     * and having only one of these means only one place can get the clamping wrong.
     */
    public static Double linear(Double value, double floor, double target) {
        if (value == null || floor == target) {
            return value == null ? null : 0.0;
        }
        double ratio = (value - floor) / (target - floor);
        return clamp(ratio) * 100.0;
    }

    public static Double linear(double value, double floor, double target) {
        return linear(Double.valueOf(value), floor, target);
    }

    /** 100 at the optimum, falling away linearly in both directions, 0 beyond the tolerance. */
    public static Double bell(Double value, double optimum, double tolerance) {
        if (value == null) {
            return null;
        }
        if (tolerance <= 0) {
            return value == optimum ? 100.0 : 0.0;
        }
        return clamp(1 - Math.abs(value - optimum) / tolerance) * 100.0;
    }

    /** Mean of the components that exist; null when none do. */
    public static Double average(Double... values) {
        double sum = 0;
        int n = 0;
        for (Double value : values) {
            if (value != null) {
                sum += value;
                n++;
            }
        }
        return n == 0 ? null : sum / n;
    }

    /** Median of the reps' totals, reported as context for a single score. */
    public static Double median(List<Double> values) {
        List<Double> present = new ArrayList<>();
        for (Double value : values) {
            if (value != null) {
                present.add(value);
            }
        }
        if (present.isEmpty()) {
            return null;
        }
        present.sort(Double::compareTo);
        int mid = present.size() / 2;
        double result = present.size() % 2 == 1
                ? present.get(mid)
                : (present.get(mid - 1) + present.get(mid)) / 2.0;
        return round2(result);
    }

    private static double clamp(double ratio) {
        return Math.max(0, Math.min(1, ratio));
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    public static Double round2(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }

    private static double round2d(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
