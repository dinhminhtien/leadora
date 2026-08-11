package com.novax.leadora.unit.reporting;

import com.novax.leadora.api.dto.response.RepScorecardResponse.RepMetrics;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepScore;
import com.novax.leadora.api.dto.response.RepScorecardResponse.TeamBaseline;
import com.novax.leadora.application.usecase.reporting.scoring.ScoringEngine;
import com.novax.leadora.application.usecase.reporting.scoring.ScoringProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** UC-23.6 — the rules a person's score is produced by, tested without a database in sight. */
class ScoringEngineTest {

    private ScoringProperties props;
    private ScoringEngine engine;

    @BeforeEach
    void setUp() {
        props = new ScoringProperties();
        engine = new ScoringEngine(props);
    }

    private static TeamBaseline team() {
        return TeamBaseline.builder()
                .winRate(50.0)
                .leadConversionRate(25.0)
                .quotationAcceptanceRate(40.0)
                .slaComplianceRate(80.0)
                .taskCompletionRate(70.0)
                .collectionOnTimeRate(75.0)
                .forecastAccuracyRate(50.0)
                .build();
    }

    // ── Shrinkage ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("two wins out of two is not a 100% win rate")
    void smallSampleIsPulledTowardTheTeam() {
        double shrunk = engine.shrunkRate(2, 2, 50.0);

        // (2 + 5×0.50) / (2 + 5) = 4.5 / 7
        assertThat(shrunk).isCloseTo(64.29, within(0.01));
        assertThat(shrunk)
                .as("without this correction the smallest sample tops the table every time")
                .isLessThan(100.0);
    }

    @Test
    @DisplayName("a large sample keeps its own number")
    void largeSampleIsBarelyMoved() {
        assertThat(engine.shrunkRate(40, 40, 50.0)).isCloseTo(94.44, within(0.01));
    }

    @Test
    @DisplayName("shrinkage lifts an unlucky small sample as well as deflating a lucky one")
    void shrinkageCutsBothWays() {
        assertThat(engine.shrunkRate(0, 2, 50.0))
                .as("nought from two is not a 0% closer")
                .isCloseTo(35.71, within(0.01));
    }

    @Test
    @DisplayName("with no team rate to lean on the raw rate is used unchanged")
    void withoutATeamRateNothingIsBorrowed() {
        assertThat(engine.shrunkRate(1, 4, null)).isEqualTo(25.0);
    }

    @Test
    @DisplayName("no events of a kind scores null, not zero — nothing happened is not a failure")
    void absentEventsScoreNull() {
        assertThat(engine.rateScore(0, 0, 50.0, 20, 60)).isNull();
    }

    // ── The curves ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a descending threshold pair scores 'lower is better' with no special case")
    void inverseMetricsNeedNoSeparateFunction() {
        // First response: 48h scores nothing, 4h scores full marks.
        assertThat(ScoringEngine.linear(48.0, 48, 4)).isEqualTo(0.0);
        assertThat(ScoringEngine.linear(4.0, 48, 4)).isEqualTo(100.0);
        assertThat(ScoringEngine.linear(26.0, 48, 4)).isEqualTo(50.0);
    }

    @Test
    @DisplayName("values beyond either end are clamped rather than allowed past 100 or below 0")
    void scoresAreClamped() {
        assertThat(ScoringEngine.linear(1.0, 48, 4)).isEqualTo(100.0);
        assertThat(ScoringEngine.linear(500.0, 48, 4)).isEqualTo(0.0);
        assertThat(ScoringEngine.linear(90.0, 20, 60)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("discount is scored on a bell, so restraint and generosity both fall off")
    void discountIsBadAtBothEnds() {
        assertThat(ScoringEngine.bell(10.0, 10, 10)).isEqualTo(100.0);
        assertThat(ScoringEngine.bell(15.0, 10, 10)).isEqualTo(50.0);
        assertThat(ScoringEngine.bell(5.0, 10, 10)).isEqualTo(50.0);
        assertThat(ScoringEngine.bell(35.0, 10, 10))
                .as("buying revenue out of the company's margin")
                .isEqualTo(0.0);
        assertThat(ScoringEngine.bell(0.0, 10, 10))
                .as("never discounting is usually never negotiating, not iron discipline")
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("an average ignores the components that do not exist")
    void averageSkipsNulls() {
        assertThat(ScoringEngine.average(100.0, null, 50.0)).isEqualTo(75.0);
        assertThat(ScoringEngine.average(null, null)).isNull();
    }

    // ── Weighting and coverage ────────────────────────────────────────────────

    @Test
    @DisplayName("an axis with nothing to measure is dropped and the rest are renormalised")
    void missingAxisDoesNotScoreZero() {
        RepMetrics noFeedback = perfectExceptFeedback();

        RepScore score = engine.score(noFeedback, team(), 1.0);

        assertThat(score.getQuality()).as("no feedback means no quality score").isNull();
        assertThat(score.getWeightCovered())
                .as("100 minus the quality weight")
                .isEqualTo(90.0);
        assertThat(score.getTotal())
                .as("scoring the missing axis as zero would have cost this rep ten points")
                .isGreaterThan(90.0);
    }

    @Test
    @DisplayName("selling nothing scores zero, but converting nothing out of nothing scores null")
    void measuredZeroAndMissingDataAreDifferentThings() {
        RepScore score = engine.score(RepMetrics.builder().build(), team(), 1.0);

        // Rates have no denominator: no leads were worked, no deal reached a decision, no quotation
        // was sent. There is nothing to be good or bad at, so these axes are left out entirely.
        assertThat(score.getEfficiency()).isNull();
        assertThat(score.getVelocity()).isNull();
        assertThat(score.getQuality()).isNull();

        // Outcome is different in kind: zero deals won over a scored period is a measurement, not a
        // gap. Treating it as missing would let a rep who sold nothing be scored purely on how
        // tidily they closed their tasks.
        assertThat(score.getOutcome()).isEqualTo(0.0);
        assertThat(score.getTotal()).isEqualTo(0.0);
        assertThat(score.getWeightCovered()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("absolute targets scale with the length of the period being scored")
    void targetsScaleWithThePeriod() {
        RepMetrics m = RepMetrics.builder()
                .revenue(BigDecimal.valueOf(200_000_000))
                .dealsWon(5)
                .build();

        assertThat(engine.score(m, team(), 1.0).getOutcome())
                .as("exactly one month's target in one month")
                .isEqualTo(100.0);
        assertThat(engine.score(m, team(), 2.0).getOutcome())
                .as("the same output over two months is half the target")
                .isEqualTo(50.0);
    }

    @Test
    @DisplayName("changing a weight changes the score, which is the point of it being configurable")
    void weightsAreHonoured() {
        RepMetrics m = perfectExceptFeedback();
        double before = engine.score(m, team(), 1.0).getTotal();

        props.getWeights().setOutcome(0);
        double after = engine.score(m, team(), 1.0).getTotal();

        assertThat(after).isNotEqualTo(before);
    }

    // ── Median ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the team median ignores unscored reps")
    void medianSkipsNulls() {
        assertThat(ScoringEngine.median(Arrays.asList(10.0, null, 30.0, 50.0))).isEqualTo(30.0);
        assertThat(ScoringEngine.median(Arrays.asList(10.0, 20.0, 30.0, 40.0))).isEqualTo(25.0);
        assertThat(ScoringEngine.median(List.of())).isNull();
    }

    /** Strong on every axis the data supports, with no customer feedback at all. */
    private static RepMetrics perfectExceptFeedback() {
        return RepMetrics.builder()
                .revenue(BigDecimal.valueOf(400_000_000))
                .dealsWon(20)
                .dealsClosed(22)
                .leadsCreated(40)
                .cohortConverted(30)
                .quotationsCreated(20)
                .quotationsAccepted(18)
                .firstResponseHours(2.0)
                .quotationTurnaroundHours(3.0)
                .dealCycleDays(10.0)
                .slaDecided(30)
                .slaOnTime(30)
                .tasksTotal(20)
                .tasksCompleted(20)
                .taskOverdueRate(0.0)
                .avgDiscountPercent(10.0)
                .collectionTotal(10)
                .collectionOnTime(10)
                .forecastTotal(10)
                .forecastHit(10)
                .csatSamples(0)
                .build();
    }
}
