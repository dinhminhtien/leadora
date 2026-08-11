package com.novax.leadora.unit.reporting;
import com.novax.leadora.api.dto.response.*;

import com.novax.leadora.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The UC-23 report responses are cached in Redis as JSON, so they must survive a serialize →
 * deserialize round trip through exactly the serializer {@code CacheConfig} installs.
 *
 * <p>This is the test that was missing. A Lombok {@code @Builder} class exposes no constructor
 * Jackson can call and, with only {@code @Getter}, no setters either — so every cache read threw,
 * the configured {@code CacheErrorHandler} logged it as a warning and fell back to the database,
 * and the caches sat there costing a Redis round trip per request while never once hitting. A
 * failure mode whose only symptom is a WARN line needs a test, not vigilance.
 */
class ReportResponseCacheRoundTripTest {

    /** The exact serializer the cache manager installs — not a lookalike built here. */
    private final GenericJackson2JsonRedisSerializer serializer = CacheConfig.valueSerializer();

    private <T> T roundTrip(T value, Class<T> type) {
        return type.cast(serializer.deserialize(serializer.serialize(value)));
    }

    @Test
    @DisplayName("UC-23.1 survives the cache round trip with its nested rows")
    void salesPerformanceRoundTrips() {
        SalesPerformanceReportResponse original = SalesPerformanceReportResponse.builder()
                .dateFrom(LocalDate.of(2026, 7, 1))
                .dateTo(LocalDate.of(2026, 7, 31))
                .timezone("Asia/Ho_Chi_Minh")
                .leadsCreated(10)
                .qualifiedLeads(6)
                .cohortConverted(4)
                .dealsWon(3)
                .winRate(75.0)
                .wonValue(BigDecimal.valueOf(900))
                .revenue(BigDecimal.valueOf(1234.56))
                .reps(List.of(
                        SalesPerformanceReportResponse.RepRow.builder()
                                .name("Mai Anh").leads(7).dealsWon(2)
                                .wonValue(BigDecimal.valueOf(600))
                                .revenue(BigDecimal.valueOf(1000)).build(),
                        SalesPerformanceReportResponse.RepRow.builder()
                                .name("(Unassigned)").leads(3).unassigned(true)
                                .wonValue(BigDecimal.ZERO).revenue(BigDecimal.ZERO).build()))
                .build();

        SalesPerformanceReportResponse restored = roundTrip(original, SalesPerformanceReportResponse.class);

        assertThat(restored.getDateFrom()).isEqualTo(original.getDateFrom());
        assertThat(restored.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(restored.getLeadsCreated()).isEqualTo(10);
        assertThat(restored.getQualifiedLeads()).isEqualTo(6);
        assertThat(restored.getCohortConverted()).isEqualTo(4);
        assertThat(restored.getWinRate()).isEqualTo(75.0);
        assertThat(restored.getWonValue()).isEqualByComparingTo(BigDecimal.valueOf(900));
        assertThat(restored.getReps()).hasSize(2);
        assertThat(restored.getReps().get(0).getName()).isEqualTo("Mai Anh");
        assertThat(restored.getReps().get(1).isUnassigned()).isTrue();
    }

    @Test
    @DisplayName("UC-23.2 survives the cache round trip")
    void taskPerformanceRoundTrips() {
        TaskPerformanceReportResponse original = TaskPerformanceReportResponse.builder()
                .totalTasks(10).completed(6).overdue(2)
                .completionRate(60.0).overdueRate(20.0)
                .ownScope(true)
                .staff(List.of(TaskPerformanceReportResponse.StaffRow.builder()
                        .name("Mai Anh").total(7).completed(5).overdue(1)
                        .completionRate(71.43).build()))
                .build();

        TaskPerformanceReportResponse restored = roundTrip(original, TaskPerformanceReportResponse.class);

        assertThat(restored.getTotalTasks()).isEqualTo(10);
        assertThat(restored.isOwnScope()).isTrue();
        assertThat(restored.getStaff()).singleElement()
                .satisfies(row -> assertThat(row.getCompletionRate()).isEqualTo(71.43));
    }

    @Test
    @DisplayName("UC-23.4 survives the cache round trip")
    void pipelineProgressionRoundTrips() {
        PipelineProgressionReportResponse original = PipelineProgressionReportResponse.builder()
                .totalDeals(4).openDeals(2).closedWon(1).closedLost(1).winRate(50.0)
                .pipelineValue(BigDecimal.valueOf(300))
                .bottleneckStage("Qualification")
                .bottleneckBasis("Ranked by average days since last updated.")
                .stages(List.of(PipelineProgressionReportResponse.StageRow.builder()
                        .stage("QUALIFICATION").label("Qualification").count(2)
                        .value(BigDecimal.valueOf(300))
                        .avgAgeDays(30.0).avgDaysInStage(25.0).closed(false).build()))
                .build();

        PipelineProgressionReportResponse restored =
                roundTrip(original, PipelineProgressionReportResponse.class);

        assertThat(restored.getBottleneckStage()).isEqualTo("Qualification");
        assertThat(restored.getPipelineValue()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(restored.getStages()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getAvgDaysInStage()).isEqualTo(25.0);
                    assertThat(row.isClosed()).isFalse();
                });
    }

    @Test
    @DisplayName("UC-23.5 survives the cache round trip")
    void quotationOutcomeRoundTrips() {
        QuotationOutcomeReportResponse original = QuotationOutcomeReportResponse.builder()
                .total(8).superseded(3).approved(8).rejectedByApprover(2)
                .accepted(1).converted(3)
                .approvalRate(80.0).acceptanceRate(50.0).conversionRate(37.5)
                .byStatus(List.of(QuotationOutcomeReportResponse.StatusRow.builder()
                        .status("CONVERTED").label("Converted").count(3).build()))
                .build();

        QuotationOutcomeReportResponse restored =
                roundTrip(original, QuotationOutcomeReportResponse.class);

        assertThat(restored.getTotal()).isEqualTo(8);
        assertThat(restored.getSuperseded()).isEqualTo(3);
        assertThat(restored.getApprovalRate()).isEqualTo(80.0);
        assertThat(restored.getByStatus()).singleElement()
                .satisfies(row -> assertThat(row.getCount()).isEqualTo(3));
    }

    @Test
    @DisplayName("UC-23.3 survives the cache round trip even though it is not cached today")
    void slaComplianceRoundTrips() {
        SlaReportResponse original = SlaReportResponse.builder()
                .dateFrom(LocalDate.of(2026, 7, 1))
                .dateTo(LocalDate.of(2026, 7, 31))
                .totalTracked(3).resolvedCount(1).resolvedLateCount(1)
                .breachedCount(1).inFlightCount(2)
                .breachRatePct(33.33).complianceRatePct(0.0)
                .byActivityType(List.of(SlaReportResponse.ActivityBreakdown.builder()
                        .activityType("LEAD_RESPONSE").activityLabel("Lead Response")
                        .total(3).breached(1).breachRatePct(33.33).build()))
                .build();

        SlaReportResponse restored = roundTrip(original, SlaReportResponse.class);

        assertThat(restored.getDateFrom()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(restored.getBreachedCount()).isEqualTo(1);
        assertThat(restored.getByActivityType()).singleElement()
                .satisfies(row -> assertThat(row.getActivityLabel()).isEqualTo("Lead Response"));
    }

    @Test
    @DisplayName("UC-23.6 survives the round trip with all five levels of nesting")
    void repScorecardRoundTrips() {
        RepScorecardResponse original = RepScorecardResponse.builder()
                .dateFrom(LocalDate.of(2026, 7, 1))
                .dateTo(LocalDate.of(2026, 7, 30))
                .timezone("Asia/Ho_Chi_Minh")
                .periodMonths(1.0)
                .weights(RepScorecardResponse.Weights.builder()
                        .outcome(30).efficiency(25).velocity(15).discipline(20).quality(10).build())
                .team(RepScorecardResponse.TeamBaseline.builder()
                        .repCount(2).winRate(55.0).csat(4.1).medianScore(63.5).build())
                .reps(List.of(RepScorecardResponse.RepScorecard.builder()
                        .userId(java.util.UUID.randomUUID())
                        .name("Mai Anh")
                        .metrics(RepScorecardResponse.RepMetrics.builder()
                                .revenue(BigDecimal.valueOf(90_000_000))
                                .dealsWon(6).dealsClosed(8).winRate(75.0)
                                .firstResponseHours(3.5).csat(4.5).csatSamples(4)
                                .activeDays(22).sampleSize(9)
                                .build())
                        .score(RepScorecardResponse.RepScore.builder()
                                .outcome(80.0).efficiency(70.0).velocity(65.0)
                                .discipline(null).quality(90.0)
                                .total(74.2).weightCovered(80.0).build())
                        .lowConfidence(false)
                        .ranked(true)
                        .rank(1)
                        .dataGaps(List.of("No SLA reached an outcome in this period."))
                        .topLostReasons(List.of(RepScorecardResponse.LostReason.builder()
                                .reason("Price too high").count(7).build()))
                        .build()))
                .build();

        RepScorecardResponse restored = roundTrip(original, RepScorecardResponse.class);

        assertThat(restored.getPeriodMonths()).isEqualTo(1.0);
        assertThat(restored.getWeights().getOutcome()).isEqualTo(30.0);
        assertThat(restored.getTeam().getMedianScore()).isEqualTo(63.5);
        assertThat(restored.getReps()).singleElement().satisfies(rep -> {
            assertThat(rep.getName()).isEqualTo("Mai Anh");
            assertThat(rep.getRank()).isEqualTo(1);
            assertThat(rep.getMetrics().getWinRate()).isEqualTo(75.0);
            assertThat(rep.getMetrics().getRevenue()).isEqualByComparingTo(BigDecimal.valueOf(90_000_000));
            assertThat(rep.getScore().getTotal()).isEqualTo(74.2);
            assertThat(rep.getScore().getDiscipline())
                    .as("an unscored axis must come back unscored, not as zero")
                    .isNull();
            assertThat(rep.getDataGaps()).hasSize(1);
            assertThat(rep.getTopLostReasons()).singleElement()
                    .satisfies(r -> assertThat(r.getCount()).isEqualTo(7));
        });
    }
}
