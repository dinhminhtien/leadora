package com.novax.leadora.unit.reporting;

import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse;
import com.novax.leadora.application.usecase.quotation.QuotationOutcomeClassifier;
import com.novax.leadora.application.usecase.reporting.GetQuotationOutcomeReportUseCase;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationOutcomeRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UC-23.5 — the outcome figures a status snapshot on a single time axis cannot produce.
 *
 * <p>The repository is mocked, so these tests pin the folding rules and the arithmetic. They cannot
 * prove the SQL selects the right rows; that is checked by running the statement against Postgres.
 *
 * <p>Cohort rows are built through {@link QuotationOutcomeClassifier#bucket}, the same encoding the
 * query emits. A test can no longer describe a quotation that is CONVERTED and lost at once, which
 * the earlier fixtures — status and outcome set independently — allowed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetQuotationOutcomeReportUseCaseTest {

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BEN = UUID.randomUUID();

    private static final boolean SENT = true;
    private static final boolean UNSENT = false;
    private static final boolean REPLACED = true;
    private static final boolean CURRENT = false;

    @Mock
    private QuotationOutcomeRepository quotationOutcomeRepository;

    private GetQuotationOutcomeReportUseCase useCase;
    private List<Object[]> rows;

    @BeforeEach
    void setUp() {
        rows = new ArrayList<>();
        useCase = new GetQuotationOutcomeReportUseCase(
                quotationOutcomeRepository, new ReportRangeFactory("Asia/Ho_Chi_Minh"));
        when(quotationOutcomeRepository.quotationOutcomeAggregates(any(), any())).thenReturn(rows);
    }

    /** One aggregate row: {@code [metric, bucket, ownerId, ownerName, count, value]}. */
    private void row(String metric, String bucket, UUID ownerId, String ownerName,
                     long count, double value) {
        rows.add(new Object[] {
                metric, bucket, ownerId, ownerName, count, BigDecimal.valueOf(value) });
    }

    private void row(String metric, String bucket, long count) {
        row(metric, bucket, ANNA, "Anna", count, 0);
    }

    /** A group of cohort quotations sharing the three facts the classifier decides on. */
    private void cohort(QuotationStatus status, boolean sent, boolean replaced,
                        UUID ownerId, String ownerName, long count, double value) {
        row(COHORT_FACT, QuotationOutcomeClassifier.bucket(status, sent, replaced),
                ownerId, ownerName, count, value);
    }

    private void cohort(QuotationStatus status, boolean sent, boolean replaced, long count) {
        cohort(status, sent, replaced, ANNA, "Anna", count, 0);
    }

    private QuotationOutcomeReportResponse run() {
        return useCase.execute(LocalDate.now().minusDays(30), LocalDate.now());
    }

    @Test
    @DisplayName("a quotation replaced by a revision leaves the cohort even when its status does not say so")
    void revisedQuotationsLeaveTheDenominator() {
        // The replaced row was left at EXPIRED by whatever touched it next, which is exactly why the
        // status cannot be trusted: it is excluded structurally and reported separately.
        cohort(QuotationStatus.CONVERTED, SENT, CURRENT, 2);
        cohort(QuotationStatus.ACCEPTED, SENT, CURRENT, 1);
        cohort(QuotationStatus.EXPIRED, SENT, REPLACED, 1);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getTotal()).as("only the live versions count").isEqualTo(3);
        assertThat(report.getRevisedAway()).as("and the excluded row is still shown").isEqualTo(1);
        assertThat(report.getLost()).as("a replaced row is not also a loss").isZero();
    }

    @Test
    @DisplayName("the win rate divides by decided quotations, not by everything raised")
    void winRateExcludesTheUndecided() {
        cohort(QuotationStatus.CONVERTED, SENT, CURRENT, ANNA, "Anna", 3, 30_000);
        cohort(QuotationStatus.REJECTED, SENT, CURRENT, ANNA, "Anna", 1, 5_000);
        cohort(QuotationStatus.SENT, SENT, CURRENT, ANNA, "Anna", 6, 60_000);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getWinRate())
                .as("six quotations nobody has answered yet are not six losses")
                .isEqualTo(75.0);
        assertThat(report.getStillOpen()).isEqualTo(6);
        assertThat(report.getWonValue()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("nothing decided yet yields a null win rate rather than 0%")
    void winRateIsNullWhenNothingIsDecided() {
        cohort(QuotationStatus.SENT, SENT, CURRENT, 4);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getWinRate()).as("0% would read as four losses").isNull();
        assertThat(report.getTotal()).isEqualTo(4);
    }

    @Test
    @DisplayName("a quotation that never reached a customer is not counted as a loss")
    void abandonedQuotationsStayOutOfTheWinRate() {
        // Nine drafts the scheduler expired before anyone submitted them, plus one real
        // negotiation that was won. Folding the nine into losses would score this 10%.
        cohort(QuotationStatus.EXPIRED, UNSENT, CURRENT, ANNA, "Anna", 9, 45_000);
        cohort(QuotationStatus.CONVERTED, SENT, CURRENT, ANNA, "Anna", 1, 5_000);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getAbandoned()).isEqualTo(9);
        assertThat(report.getLost()).as("nobody outside the company saw them").isZero();
        assertThat(report.getWinRate()).isEqualTo(100.0);
        assertThat(report.getAbandonedValue()).isEqualByComparingTo("45000");
        assertThat(report.getStaff().get(0).getAbandoned()).isEqualTo(9);
        assertThat(report.getDataGaps())
                .anyMatch(gap -> gap.contains("9 quotation(s) ended without ever being sent"));
    }

    @Test
    @DisplayName("the same terminal status is a loss when it was sent and an abandonment when it was not")
    void dispatchSeparatesLossFromAbandonment() {
        cohort(QuotationStatus.REJECTED, SENT, CURRENT, ANNA, "Anna", 2, 0);
        cohort(QuotationStatus.REJECTED, UNSENT, CURRENT, ANNA, "Anna", 3, 0);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getLost()).isEqualTo(2);
        assertThat(report.getAbandoned()).isEqualTo(3);
        assertThat(report.getTotal()).as("both are live quotations of the period").isEqualTo(5);
    }

    @Test
    @DisplayName("a revision request is a decision that did not approve, so it stays in the denominator")
    void revisionRequestsCountAgainstFirstPassApproval() {
        row(ACT_DECISION, "APPROVED", 6);
        row(ACT_DECISION, "REJECTED", 2);
        row(ACT_DECISION, "REVISION_REQUESTED", 2);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getDecisions()).isEqualTo(10);
        assertThat(report.getDecisionsRevisionRequested()).isEqualTo(2);
        assertThat(report.getFirstPassApprovalRate()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("activity figures count events in the period, whenever the quotation was written")
    void activityIsIndependentOfTheCohort() {
        // Nothing was written this period; the team still approved, sent and converted work
        // carried over from earlier — the figures a created_at-only report showed as zero.
        row(ACT_APPROVED_STAMP, "APPROVED", ANNA, "Anna", 4, 12.5);
        row(ACT_SENT, "SENT", 5);
        row(ACT_CONVERTED, "CONVERTED", ANNA, "Anna", 3, 90_000);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getTotal()).as("no cohort").isZero();
        assertThat(report.getApprovalsStamped()).isEqualTo(4);
        assertThat(report.getSentInPeriod()).isEqualTo(5);
        assertThat(report.getConvertedInPeriod()).isEqualTo(3);
        assertThat(report.getConvertedValue()).isEqualByComparingTo("90000");
        assertThat(report.getAvgHoursToApprove()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("group averages are re-weighted by group size when they are pooled")
    void averagesArePooledByWeight() {
        // Anna sent one quotation after 10h, Ben three after 2h each. The mean is 4h, not 6h.
        row(COHORT_SEND, "SENT", ANNA, "Anna", 1, 10.0);
        row(COHORT_SEND, "SENT", BEN, "Ben", 3, 2.0);
        row(COHORT_SEND, "NEVER_SENT", ANNA, "Anna", 2, 0);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getAvgHoursToSend())
                .as("an unweighted mean of the group means would say 6.0")
                .isEqualTo(4.0);
        assertThat(report.getCohortNeverSent())
                .as("and the unsent rows must not drag the average toward zero")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("reply speed is averaged over the replies that could be timed")
    void replyLatencyReportsItsOwnCoverage() {
        row(ACT_REPLY, "ACCEPTED", 5);
        row(ACT_REPLY, "REJECTED", 3);
        row(ACT_REPLY_TIMED, "TIMED", ANNA, "Anna", 4, 6.0);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getReplies()).isEqualTo(8);
        assertThat(report.getRepliesTimed()).isEqualTo(4);
        assertThat(report.getAvgHoursToReply()).isEqualTo(6.0);
        assertThat(report.getReplyAcceptanceRate()).isEqualTo(62.5);
        assertThat(report.getDataGaps()).anyMatch(gap -> gap.contains("4 of 8 replies"));
    }

    @Test
    @DisplayName("loss-reason coverage is measured against replies, not against the cohort")
    void lossReasonCoverageComparesLikeWithLike() {
        // 40 rejection replies logged this period, all against quotations written earlier, while
        // the cohort holds 3 losses. Comparing 40 reasons with 3 losses would suppress the warning.
        cohort(QuotationStatus.REJECTED, SENT, CURRENT, 3);
        row(ACT_REPLY, "REJECTED", ANNA, "Anna", 40, 0);
        row(ACT_LOST_REASON, "Price too high", 12);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getDataGaps()).anyMatch(gap -> gap.contains("12 of 40 rejection(s)"));
    }

    @Test
    @DisplayName("a thin loss-reason record is declared rather than charted as if complete")
    void sparseLossReasonsAreDeclared() {
        cohort(QuotationStatus.ACCEPTED, SENT, CURRENT, 2);
        cohort(QuotationStatus.EXPIRED, SENT, CURRENT, 9);
        row(ACT_REPLY, "REJECTED", ANNA, "Anna", 9, 0);
        row(ACT_LOST_REASON, "Price too high", 1);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getLostReasons()).hasSize(1);
        assertThat(report.getDataGaps()).anyMatch(gap -> gap.contains("1 of 9 rejection(s)"));
    }

    @Test
    @DisplayName("the preparer table reconciles with the headline and ranks on outcome, not volume")
    void staffRowsFoldFromTheSameRows() {
        cohort(QuotationStatus.CONVERTED, SENT, CURRENT, ANNA, "Anna", 2, 20_000);
        cohort(QuotationStatus.EXPIRED, SENT, CURRENT, BEN, "Ben", 6, 0);
        cohort(QuotationStatus.CONVERTED, SENT, CURRENT, BEN, "Ben", 2, 5_000);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getTotal()).isEqualTo(10);
        assertThat(report.getStaff()).hasSize(2);
        assertThat(report.getStaff().get(0).getName())
                .as("Anna wins everything she decides; Ben wrote four times as many")
                .isEqualTo("Anna");
        assertThat(report.getStaff().get(0).getWinRate()).isEqualTo(100.0);
        assertThat(report.getStaff().get(1).getWinRate()).isEqualTo(25.0);
        assertThat(report.getStaff().stream().mapToLong(s -> s.getPrepared()).sum())
                .as("the table adds up to the headline")
                .isEqualTo(report.getTotal());
    }

    @Test
    @DisplayName("a preparer's replaced revisions are excluded from their row as well as the headline")
    void staffRowsExcludeReplacedRevisions() {
        cohort(QuotationStatus.CONVERTED, SENT, CURRENT, ANNA, "Anna", 2, 20_000);
        cohort(QuotationStatus.CLOSED, SENT, REPLACED, ANNA, "Anna", 3, 0);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getStaff()).singleElement()
                .satisfies(row -> assertThat(row.getPrepared()).isEqualTo(2));
        assertThat(report.getTotal()).isEqualTo(2);
        assertThat(report.getRevisedAway()).isEqualTo(3);
    }

    @Test
    @DisplayName("quotations with no recorded preparer keep their own row and stay in the totals")
    void unattributedRowsAreKept() {
        cohort(QuotationStatus.SENT, SENT, CURRENT, null, null, 3, 0);
        cohort(QuotationStatus.SENT, SENT, CURRENT, ANNA, "Anna", 1, 0);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getTotal()).isEqualTo(4);
        assertThat(report.getStaff()).hasSize(2);
        assertThat(report.getStaff().get(report.getStaff().size() - 1).isUnattributed())
                .as("the unattributed row sorts last so it never reads as a person")
                .isTrue();
    }

    @Test
    @DisplayName("an empty period declares itself empty instead of publishing zeroes")
    void emptyPeriodIsSafe() {
        QuotationOutcomeReportResponse report = run();

        assertThat(report.getTotal()).isZero();
        assertThat(report.getWinRate()).isNull();
        assertThat(report.getFirstPassApprovalRate()).isNull();
        assertThat(report.getAvgHoursToSend()).isNull();
        assertThat(report.getConversionRate()).isZero();
        assertThat(report.getByStatus()).isEmpty();
        assertThat(report.getDataGaps()).anyMatch(gap -> gap.contains("No quotations were written"));
    }

    @Test
    @DisplayName("the status breakdown lists only non-empty statuses, in enum order")
    void breakdownSkipsEmptyStatuses() {
        cohort(QuotationStatus.CONVERTED, SENT, CURRENT, 2);
        cohort(QuotationStatus.DRAFT, UNSENT, CURRENT, 1);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getByStatus()).hasSize(2);
        assertThat(report.getByStatus().get(0).getStatus()).isEqualTo("DRAFT");
        assertThat(report.getByStatus().get(1).getStatus()).isEqualTo("CONVERTED");
    }

    // Discriminators, mirroring the ones the query emits.
    private static final String COHORT_FACT = "COHORT_FACT";
    private static final String COHORT_SEND = "COHORT_SEND";
    private static final String ACT_DECISION = "ACT_DECISION";
    private static final String ACT_APPROVED_STAMP = "ACT_APPROVED_STAMP";
    private static final String ACT_REPLY = "ACT_REPLY";
    private static final String ACT_REPLY_TIMED = "ACT_REPLY_TIMED";
    private static final String ACT_LOST_REASON = "ACT_LOST_REASON";
    private static final String ACT_SENT = "ACT_SENT";
    private static final String ACT_CONVERTED = "ACT_CONVERTED";
}
