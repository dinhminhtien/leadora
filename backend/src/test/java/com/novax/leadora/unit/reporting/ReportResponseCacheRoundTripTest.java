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
                .timezone("Asia/Ho_Chi_Minh")
                .totalTasks(10).completed(6).open(4).openOverdue(2)
                .completionRate(60.0).overdueRate(50.0)
                .resolvedTotal(6).resolvedOnTime(4).resolvedLate(2).punctualityRate(66.67)
                .completedUndated(3).punctualityCoverage(50.0)
                .avgDaysOverdue(4.5).avgCycleHours(31.2)
                .orphanTasks(1).orphanRate(10.0)
                .slaDecided(5).slaOnTime(4).slaComplianceRate(80.0)
                .activityMix(List.of(TaskPerformanceReportResponse.CountRow.builder()
                        .key("CALL").label("Call").count(6).build()))
                .overdueAging(List.of(TaskPerformanceReportResponse.CountRow.builder()
                        .key("D1_3").label("1–3 days late").count(2).build()))
                .overdueByPriority(List.of(TaskPerformanceReportResponse.CountRow.builder()
                        .key("HIGH").label("High").count(1).build()))
                .ownScope(true)
                .staff(List.of(TaskPerformanceReportResponse.StaffRow.builder()
                        .name("Mai Anh").total(7).completed(5).openOverdue(1)
                        .resolvedOnTime(4).resolvedLate(1).punctualityRate(80.0)
                        .avgCycleHours(20.0)
                        .completionRate(71.43).build()))
                .build();

        TaskPerformanceReportResponse restored = roundTrip(original, TaskPerformanceReportResponse.class);

        assertThat(restored.getTotalTasks()).isEqualTo(10);
        assertThat(restored.isOwnScope()).isTrue();
        assertThat(restored.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(restored.getResolvedLate()).isEqualTo(2);
        assertThat(restored.getPunctualityRate()).isEqualTo(66.67);
        assertThat(restored.getPunctualityCoverage()).isEqualTo(50.0);
        assertThat(restored.getActivityMix()).singleElement()
                .satisfies(row -> assertThat(row.getLabel()).isEqualTo("Call"));
        assertThat(restored.getOverdueByPriority()).singleElement()
                .satisfies(row -> assertThat(row.getKey()).isEqualTo("HIGH"));
        assertThat(restored.getStaff()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getCompletionRate()).isEqualTo(71.43);
                    assertThat(row.getPunctualityRate()).isEqualTo(80.0);
                });
    }

    @Test
    @DisplayName("UC-23.4 survives the cache round trip")
    void pipelineProgressionRoundTrips() {
        PipelineProgressionReportResponse original = PipelineProgressionReportResponse.builder()
                .totalDeals(4).openDeals(2).closedWon(1).closedLost(1)
                .cohortWinRate(50.0).cohortDecided(2).closedHereOpenedEarlier(3)
                .pipelineValue(BigDecimal.valueOf(300))
                .bottleneckStage("Qualification")
                .bottleneckBasis("Ranked by measured time to leave the stage.")
                .dataGaps(List.of("3 deal(s) closed during this period were opened before it."))
                .stages(List.of(PipelineProgressionReportResponse.StageRow.builder()
                        .stage("QUALIFICATION").label("Qualification").count(2)
                        .value(BigDecimal.valueOf(300))
                        .avgAgeDays(30.0).avgDaysToMoveOn(25.0).completedLegs(4)
                        .dealsWaitingNow(2).avgDaysWaiting(31.5).closed(false).build()))
                .build();

        PipelineProgressionReportResponse restored =
                roundTrip(original, PipelineProgressionReportResponse.class);

        assertThat(restored.getBottleneckStage()).isEqualTo("Qualification");
        assertThat(restored.getPipelineValue()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(restored.getCohortWinRate()).isEqualTo(50.0);
        assertThat(restored.getCohortDecided()).isEqualTo(2);
        assertThat(restored.getClosedHereOpenedEarlier()).isEqualTo(3);
        assertThat(restored.getDataGaps()).hasSize(1);
        assertThat(restored.getStages()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getAvgDaysToMoveOn()).isEqualTo(25.0);
                    assertThat(row.getCompletedLegs()).isEqualTo(4);
                    assertThat(row.getDealsWaitingNow()).isEqualTo(2);
                    assertThat(row.getAvgDaysWaiting()).isEqualTo(31.5);
                    assertThat(row.isClosed()).isFalse();
                });
    }

    @Test
    @DisplayName("UC-23.5 survives the cache round trip")
    void quotationOutcomeRoundTrips() {
        QuotationOutcomeReportResponse original = QuotationOutcomeReportResponse.builder()
                .total(8).revisedAway(3)
                .won(4).lost(1).stillOpen(3)
                .wonValue(BigDecimal.valueOf(20_000)).lostValue(BigDecimal.valueOf(500))
                .winRate(80.0).cohortConverted(3).conversionRate(37.5)
                .avgHoursToSend(4.0).avgHoursToApprove(12.5).avgHoursToReply(6.0)
                .decisions(5).decisionsApproved(3).decisionsRevisionRequested(1)
                .firstPassApprovalRate(60.0)
                .convertedValue(BigDecimal.valueOf(90_000))
                .byStatus(List.of(QuotationOutcomeReportResponse.StatusRow.builder()
                        .status("CONVERTED").label("Converted").count(3).build()))
                .discountBands(List.of(QuotationOutcomeReportResponse.CountRow.builder()
                        .key("D1_10").label("1–10%").count(2)
                        .value(BigDecimal.valueOf(1_500)).build()))
                .staff(List.of(QuotationOutcomeReportResponse.StaffRow.builder()
                        .name("Anna").prepared(4).won(3).lost(1).winRate(75.0)
                        .wonValue(BigDecimal.valueOf(20_000))
                        .sent(4).avgHoursToSend(3.5).unattributed(false).build()))
                .dataGaps(List.of("A loss reason was recorded for 1 of 9 lost quotations."))
                .build();

        QuotationOutcomeReportResponse restored =
                roundTrip(original, QuotationOutcomeReportResponse.class);

        assertThat(restored.getTotal()).isEqualTo(8);
        assertThat(restored.getRevisedAway()).isEqualTo(3);
        assertThat(restored.getWinRate()).isEqualTo(80.0);
        assertThat(restored.getFirstPassApprovalRate()).isEqualTo(60.0);
        assertThat(restored.getAvgHoursToReply()).isEqualTo(6.0);
        assertThat(restored.getWonValue()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(restored.getDataGaps()).hasSize(1);
        assertThat(restored.getByStatus()).singleElement()
                .satisfies(row -> assertThat(row.getCount()).isEqualTo(3));
        assertThat(restored.getDiscountBands()).singleElement()
                .satisfies(row -> assertThat(row.getValue())
                        .isEqualByComparingTo(BigDecimal.valueOf(1_500)));
        assertThat(restored.getStaff()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getWinRate()).isEqualTo(75.0);
                    assertThat(row.getAvgHoursToSend()).isEqualTo(3.5);
                });
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
