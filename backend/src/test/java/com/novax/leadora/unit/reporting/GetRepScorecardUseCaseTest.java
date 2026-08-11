package com.novax.leadora.unit.reporting;

import com.novax.leadora.api.dto.response.RepScorecardResponse;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepScorecard;
import com.novax.leadora.application.usecase.reporting.GetRepScorecardUseCase;
import com.novax.leadora.application.usecase.reporting.scoring.ScoringEngine;
import com.novax.leadora.application.usecase.reporting.scoring.ScoringProperties;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.RepScorecardRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-23.6 — folding five result sets into one scorecard per rep. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetRepScorecardUseCaseTest {

    @Mock
    private DealRepository dealRepository;
    @Mock
    private RepScorecardRepository scorecardRepository;
    @Mock
    private UserRepository userRepository;

    private GetRepScorecardUseCase useCase;

    private final List<Object[]> sales = new ArrayList<>();
    private final List<Object[]> velocity = new ArrayList<>();
    private final List<Object[]> discipline = new ArrayList<>();
    private final List<Object[]> quality = new ArrayList<>();
    private final List<Object[]> slaRows = new ArrayList<>();

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BINH = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetRepScorecardUseCase(
                dealRepository, scorecardRepository, userRepository,
                new ReportRangeFactory("Asia/Ho_Chi_Minh"),
                new ScoringEngine(new ScoringProperties()), new ScoringProperties());
        when(dealRepository.salesPerformanceAggregates(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(sales);
        when(scorecardRepository.velocityByOwner(any(), any())).thenReturn(velocity);
        when(scorecardRepository.disciplineByOwner(any(), any(), any(), anyInt())).thenReturn(discipline);
        when(scorecardRepository.qualityByOwner(any(), any())).thenReturn(quality);
        when(scorecardRepository.slaRowsByOwner(any(), any())).thenReturn(slaRows);
        // Only sales reps are scored; everyone else who merely logged in must stay out.
        when(userRepository.findByRoleName("SALES")).thenReturn(List.of(salesUser(ANNA), salesUser(BINH)));
    }

    /** [kind, bucket, ownerId, ownerName, count, amount] */
    private void sales(String kind, String bucket, UUID id, String name, long count, long amount) {
        sales.add(new Object[] { kind, bucket, id, name, count, BigDecimal.valueOf(amount) });
    }

    /** [metric, ownerId, ownerName, samples, value] */
    private void velocity(String metric, UUID id, String name, long samples, double value) {
        velocity.add(new Object[] { metric, id, name, samples, BigDecimal.valueOf(value) });
    }

    /** [metric, ownerId, ownerName, count, value] */
    private void discipline(String metric, UUID id, String name, long count, double value) {
        discipline.add(new Object[] { metric, id, name, count, BigDecimal.valueOf(value) });
    }

    /** [metric, ownerId, ownerName, label, count, value] */
    private void quality(String metric, UUID id, String name, String label, long count, double value) {
        quality.add(new Object[] { metric, id, name, label, count, BigDecimal.valueOf(value) });
    }

    /** [ownerId, ownerName, status, warningAt, deadlineAt, resolvedAt] */
    private void sla(UUID id, String name, String status, OffsetDateTime deadline, OffsetDateTime resolved) {
        slaRows.add(new Object[] { id, name, status, deadline.minusHours(1), deadline, resolved });
    }

    private static UserEntity salesUser(UUID id) {
        UserEntity user = new UserEntity();
        user.setUserId(id);
        RoleEntity role = new RoleEntity();
        role.setRoleName("SALES");
        user.setRole(role);
        return user;
    }

    private RepScorecardResponse run() {
        return useCase.execute(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30));
    }

    private static RepScorecard rep(RepScorecardResponse report, UUID id) {
        return report.getReps().stream()
                .filter(r -> id.equals(r.getUserId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("the report costs five round trips, one of them UC-23.1's own statement")
    void readsFiveResultSets() {
        run();

        verify(dealRepository, times(1)).salesPerformanceAggregates(
                any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(scorecardRepository, times(1)).velocityByOwner(any(), any());
        verify(scorecardRepository, times(1)).disciplineByOwner(any(), any(), any(), anyInt());
        verify(scorecardRepository, times(1)).qualityByOwner(any(), any());
        verify(scorecardRepository, times(1)).slaRowsByOwner(any(), any());
    }

    @Test
    @DisplayName("outcome and efficiency are read from the Sales Performance statement, unfiltered")
    void outcomeComesFromTheSharedStatement() {
        sales("DEAL_CLOSED", "WON", ANNA, "Anna", 6, 60_000);
        sales("DEAL_CLOSED", "LOST", ANNA, "Anna", 2, 5_000);
        sales("REVENUE", "PAID", ANNA, "Anna", 3, 90_000_000);
        sales("LEAD", "NEW", ANNA, "Anna", 20, 0);
        sales("LEAD_COHORT_CONVERTED", "CONVERTED", ANNA, "Anna", 8, 0);

        RepScorecard anna = rep(run(), ANNA);

        assertThat(anna.getMetrics().getDealsWon()).isEqualTo(6);
        assertThat(anna.getMetrics().getDealsLost()).isEqualTo(2);
        assertThat(anna.getMetrics().getDealsClosed()).isEqualTo(8);
        assertThat(anna.getMetrics().getWinRate()).isEqualTo(75.0);
        assertThat(anna.getMetrics().getLeadConversionRate()).isEqualTo(40.0);
        assertThat(anna.getMetrics().getRevenue()).isEqualByComparingTo(BigDecimal.valueOf(90_000_000));
    }

    @Test
    @DisplayName("superseded revisions leave the quotation denominator, as in UC-23.1 and UC-23.5")
    void supersededRevisionsAreNotCounted() {
        sales("QUOTATION", "SUPERSEDED", ANNA, "Anna", 4, 0);
        sales("QUOTATION", "SENT", ANNA, "Anna", 6, 0);
        sales("QUOTATION", "ACCEPTED", ANNA, "Anna", 2, 0);
        sales("QUOTATION", "CONVERTED", ANNA, "Anna", 2, 0);

        RepScorecard anna = rep(run(), ANNA);

        assertThat(anna.getMetrics().getQuotationsCreated()).isEqualTo(10);
        assertThat(anna.getMetrics().getQuotationsAccepted()).isEqualTo(4);
        assertThat(anna.getMetrics().getQuotationAcceptanceRate()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("SLA rows are classified the way UC-23.3 classifies them")
    void slaFollowsTheSharedClassifier() {
        OffsetDateTime deadline = OffsetDateTime.now().minusDays(2);
        sla(ANNA, "Anna", "RESOLVED", deadline, deadline.minusHours(3));   // on time
        sla(ANNA, "Anna", "RESOLVED", deadline, deadline.plusHours(5));    // resolved, but late
        sla(ANNA, "Anna", "IN_PROGRESS", deadline, null);                  // unresolved, overdue
        sla(ANNA, "Anna", "RESOLVED", deadline, null);                     // no timestamp: undetermined

        RepScorecard anna = rep(run(), ANNA);

        assertThat(anna.getMetrics().getSlaDecided())
                .as("the undetermined row is held out of the denominator, not counted as compliant")
                .isEqualTo(3);
        assertThat(anna.getMetrics().getSlaOnTime()).isEqualTo(1);
        assertThat(anna.getMetrics().getSlaComplianceRate()).isEqualTo(33.33);
    }

    @Test
    @DisplayName("a resolved breach is still a breach")
    void resolvingABreachDoesNotEraseIt() {
        OffsetDateTime deadline = OffsetDateTime.now().minusDays(1);
        sla(ANNA, "Anna", "RESOLVED", deadline, deadline.plusHours(2));

        assertThat(rep(run(), ANNA).getMetrics().getSlaComplianceRate()).isZero();
    }

    @Test
    @DisplayName("velocity, discipline and quality merge into the same rep row")
    void everyResultSetLandsOnOneRep() {
        sales("LEAD", "NEW", ANNA, "Anna", 10, 0);
        velocity("FIRST_RESPONSE_HOURS", ANNA, "Anna", 8, 3.5);
        velocity("DEAL_CYCLE_DAYS", ANNA, "Anna", 4, 18.0);
        discipline("TASKS_TOTAL", ANNA, "Anna", 10, 0);
        discipline("TASKS_COMPLETED", ANNA, "Anna", 9, 0);
        discipline("TASKS_OVERDUE", ANNA, "Anna", 1, 0);
        discipline("DISCOUNT_PCT", ANNA, "Anna", 5, 12.5);
        discipline("ACTIVE_DAYS", ANNA, "Anna", 22, 0);
        quality("CSAT", ANNA, "Anna", null, 4, 4.5);

        RepScorecard anna = rep(run(), ANNA);

        assertThat(anna.getMetrics().getFirstResponseHours()).isEqualTo(3.5);
        assertThat(anna.getMetrics().getFirstResponseCoveragePct())
                .as("8 of 10 leads have a response event")
                .isEqualTo(80.0);
        assertThat(anna.getMetrics().getDealCycleDays()).isEqualTo(18.0);
        assertThat(anna.getMetrics().getTaskCompletionRate()).isEqualTo(90.0);
        assertThat(anna.getMetrics().getTaskOverdueRate()).isEqualTo(10.0);
        assertThat(anna.getMetrics().getAvgDiscountPercent()).isEqualTo(12.5);
        assertThat(anna.getMetrics().getCsat()).isEqualTo(4.5);
        assertThat(anna.getMetrics().getActiveDays()).isEqualTo(22);
    }

    @Test
    @DisplayName("unassigned records belong to nobody and produce no scorecard")
    void unassignedRecordsAreNotAPerson() {
        sales("DEAL_CLOSED", "WON", null, null, 5, 50_000);
        sales("DEAL_CLOSED", "WON", ANNA, "Anna", 1, 10_000);

        RepScorecardResponse report = run();

        assertThat(report.getReps()).hasSize(1);
        assertThat(report.getReps().get(0).getName()).isEqualTo("Anna");
    }

    @Test
    @DisplayName("thin evidence is flagged rather than quietly scored")
    void thinEvidenceIsFlagged() {
        sales("DEAL_CLOSED", "WON", ANNA, "Anna", 2, 20_000);
        discipline("ACTIVE_DAYS", ANNA, "Anna", 20, 0);

        RepScorecard anna = rep(run(), ANNA);

        assertThat(anna.getMetrics().getSampleSize()).isEqualTo(2);
        assertThat(anna.isLowConfidence()).isTrue();
    }

    @Test
    @DisplayName("a rep barely active in the period is measured but not ranked")
    void barelyActiveRepsAreNotRanked() {
        sales("DEAL_CLOSED", "WON", ANNA, "Anna", 9, 90_000);
        discipline("ACTIVE_DAYS", ANNA, "Anna", 2, 0);
        sales("DEAL_CLOSED", "WON", BINH, "Binh", 3, 30_000);
        discipline("ACTIVE_DAYS", BINH, "Binh", 25, 0);

        RepScorecardResponse report = run();

        assertThat(rep(report, ANNA).isRanked())
                .as("two days of work is not a period you can compare with a full month")
                .isFalse();
        assertThat(rep(report, ANNA).getRank()).isNull();
        assertThat(rep(report, BINH).isRanked()).isTrue();
        assertThat(rep(report, BINH).getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("what could not be measured is spelled out, not left as a gap in a chart")
    void dataGapsAreStated() {
        sales("LEAD", "NEW", ANNA, "Anna", 10, 0);
        velocity("FIRST_RESPONSE_HOURS", ANNA, "Anna", 6, 5.0);

        List<String> gaps = rep(run(), ANNA).getDataGaps();

        assertThat(gaps).anySatisfy(gap -> assertThat(gap).contains("6 of 10 leads"));
        assertThat(gaps).anySatisfy(gap -> assertThat(gap).contains("No customer feedback"));
        assertThat(gaps).anySatisfy(gap -> assertThat(gap).contains("No SLA"));
    }

    @Test
    @DisplayName("lost reasons come back as this rep's top three")
    void lostReasonsAreRanked() {
        sales("LEAD", "NEW", ANNA, "Anna", 1, 0);
        quality("LOST_REASON", ANNA, "Anna", "Price too high", 7, 0);
        quality("LOST_REASON", ANNA, "Anna", "Chose competitor", 4, 0);
        quality("LOST_REASON", ANNA, "Anna", "No availability", 2, 0);
        quality("LOST_REASON", ANNA, "Anna", "Timing", 1, 0);

        assertThat(rep(run(), ANNA).getTopLostReasons())
                .hasSize(3)
                .extracting(RepScorecardResponse.LostReason::getReason)
                .containsExactly("Price too high", "Chose competitor", "No availability");
    }

    @Test
    @DisplayName("the team baseline pools numerators and denominators rather than averaging rates")
    void teamBaselineIsPooled() {
        // Anna: 1 of 1 converted. Binh: 10 of 100. Averaging the two percentages gives 55%; the
        // pooled rate is 11 of 101, which is what a thin sample should actually be pulled toward.
        sales("LEAD", "NEW", ANNA, "Anna", 1, 0);
        sales("LEAD_COHORT_CONVERTED", "CONVERTED", ANNA, "Anna", 1, 0);
        sales("LEAD", "NEW", BINH, "Binh", 100, 0);
        sales("LEAD_COHORT_CONVERTED", "CONVERTED", BINH, "Binh", 10, 0);

        assertThat(run().getTeam().getLeadConversionRate()).isEqualTo(10.89);
    }

    @Test
    @DisplayName("the period is never open-ended — an unbounded request becomes the last 30 days")
    void unboundedPeriodIsNarrowed() {
        RepScorecardResponse report = useCase.execute(null, null);

        assertThat(report.getDateFrom()).isEqualTo(report.getDateTo().minusDays(29));
        assertThat(report.getPeriodMonths()).isEqualTo(1.0);
        assertThat(report.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    @DisplayName("the weights used are echoed back, so a score can be recomputed from the payload")
    void weightsAreEchoed() {
        RepScorecardResponse.Weights weights = run().getWeights();

        assertThat(weights.getOutcome()).isEqualTo(30.0);
        assertThat(weights.getEfficiency()).isEqualTo(25.0);
        assertThat(weights.getVelocity()).isEqualTo(15.0);
        assertThat(weights.getDiscipline()).isEqualTo(20.0);
        assertThat(weights.getQuality()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("an empty period produces no reps rather than a table of zeroes")
    void emptyPeriodIsSafe() {
        RepScorecardResponse report = run();

        assertThat(report.getReps()).isEmpty();
        assertThat(report.getTeam().getRepCount()).isZero();
        assertThat(report.getTeam().getMedianScore()).isNull();
    }

    @Test
    @DisplayName("a user who is not a sales rep never reaches the leaderboard")
    void nonSalesStaffAreNotScored() {
        UUID receptionist = UUID.randomUUID();
        // The only trace this person leaves is audit rows from signing in — which is what every
        // logged-in user leaves, and what used to be enough to put them on a sales scorecard with
        // 0 revenue, a hard 0/100, and a vote in the team median.
        discipline("ACTIVE_DAYS", receptionist, "Front desk", 20, 0);
        sales("DEAL_CLOSED", "WON", ANNA, "Anna", 3, 30_000);
        discipline("ACTIVE_DAYS", ANNA, "Anna", 20, 0);

        RepScorecardResponse report = run();

        assertThat(report.getReps()).extracting(RepScorecard::getName).containsExactly("Anna");
        assertThat(report.getTeam().getRepCount())
                .as("a non-rep must not inflate the team either")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a half-filled date range keeps the end the manager did supply")
    void oneSidedRangeIsHonoured() {
        RepScorecardResponse report = useCase.execute(LocalDate.of(2026, 1, 1), null);

        assertThat(report.getDateFrom())
                .as("replacing both ends made this tab silently disagree with every other one")
                .isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(report.getDateTo()).isAfterOrEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("with no audit trail at all, nobody is disqualified for having no audit trail")
    void missingActivitySignalDoesNotDisqualifyEveryone() {
        // Records imported or seeded straight into the tables leave no activity_log rows, so every
        // rep reads as zero days active. Gating on that ranked nobody and turned "we have no
        // attendance data" into "nobody worked".
        sales("DEAL_CLOSED", "WON", ANNA, "Anna", 5, 50_000);
        sales("DEAL_CLOSED", "WON", BINH, "Binh", 2, 20_000);

        RepScorecardResponse report = run();

        assertThat(rep(report, ANNA).isRanked()).isTrue();
        assertThat(rep(report, ANNA).getRank()).isEqualTo(1);
        assertThat(rep(report, BINH).getRank()).isEqualTo(2);
        assertThat(rep(report, ANNA).getDataGaps())
                .as("the missing signal is stated rather than silently applied")
                .anySatisfy(gap -> assertThat(gap).contains("days worked are unknown"));
    }

    @Test
    @DisplayName("where the signal does exist, a barely-present rep is still held out of the ranking")
    void activityGateStillAppliesWhereItIsMeasured() {
        sales("DEAL_CLOSED", "WON", ANNA, "Anna", 9, 90_000);
        discipline("ACTIVE_DAYS", ANNA, "Anna", 2, 0);
        sales("DEAL_CLOSED", "WON", BINH, "Binh", 3, 30_000);
        discipline("ACTIVE_DAYS", BINH, "Binh", 25, 0);

        RepScorecardResponse report = run();

        assertThat(rep(report, ANNA).isRanked()).isFalse();
        assertThat(rep(report, BINH).getRank()).isEqualTo(1);
    }
}
