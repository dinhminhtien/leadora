package com.novax.leadora.unit.reporting;

import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse;
import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse;
import com.novax.leadora.application.usecase.quotation.QuotationOutcome;
import com.novax.leadora.application.usecase.quotation.QuotationOutcomeClassifier;
import com.novax.leadora.application.usecase.reporting.GetQuotationOutcomeReportUseCase;
import com.novax.leadora.application.usecase.reporting.GetSalesPerformanceReportUseCase;
import com.novax.leadora.application.usecase.reporting.SalesAggregateKinds;
import com.novax.leadora.application.usecase.reporting.SalesPerformanceFilter;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * One population of quotations, fed to UC-23.1 and UC-23.5, must produce the same counts.
 *
 * <p>This exists because they did not. The two reports each decided what a quotation's fate was in
 * their own code, and on the live July data the same eleven accepted quotations were published as a
 * 91.7% rate on one screen and 52.4% on another — a disagreement nobody could adjudicate from the
 * outside, about a figure that scores people. Both now fold through
 * {@link QuotationOutcomeClassifier}; this is what notices if one of them stops.
 *
 * <p>The fixture is deliberately awkward. It contains the three cases the reports used to differ on:
 * a row replaced by a revision whose status does not say so, a quotation nobody has answered yet,
 * and one that was never sent.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotationFigureConsistencyTest {

    private static final boolean SENT = true;
    private static final boolean UNSENT = false;
    private static final boolean REPLACED = true;
    private static final boolean CURRENT = false;

    @Mock
    private DealRepository dealRepository;
    @Mock
    private QuotationOutcomeRepository quotationOutcomeRepository;

    private GetSalesPerformanceReportUseCase salesPerformance;
    private GetQuotationOutcomeReportUseCase quotationOutcome;

    /** The shared population: facts → how many quotations carry them. */
    private final Map<String, Long> population = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        ReportRangeFactory zone = new ReportRangeFactory("Asia/Ho_Chi_Minh");
        salesPerformance = new GetSalesPerformanceReportUseCase(dealRepository, zone);
        quotationOutcome = new GetQuotationOutcomeReportUseCase(quotationOutcomeRepository, zone);
    }

    private void quotations(QuotationStatus status, boolean sent, boolean replaced, long count) {
        population.merge(QuotationOutcomeClassifier.bucket(status, sent, replaced), count, Long::sum);
    }

    /** The same facts, in the row shape each report's own query hands back. */
    private void publishPopulation() {
        List<Object[]> salesRows = new ArrayList<>();
        List<Object[]> outcomeRows = new ArrayList<>();
        population.forEach((bucket, count) -> {
            salesRows.add(new Object[] {
                    SalesAggregateKinds.QUOTATION, bucket, null, null, count, BigDecimal.ZERO });
            outcomeRows.add(new Object[] {
                    "COHORT_FACT", bucket, null, null, count, BigDecimal.ZERO });
        });
        when(dealRepository.salesPerformanceAggregates(
                any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(salesRows);
        when(quotationOutcomeRepository.quotationOutcomeAggregates(any(), any()))
                .thenReturn(outcomeRows);
    }

    /** How many quotations the shared classifier puts in a given outcome. */
    private long expected(QuotationOutcome outcome) {
        return population.entrySet().stream()
                .filter(e -> QuotationOutcomeClassifier.classifyBucket(e.getKey()) == outcome)
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    @Test
    @DisplayName("both reports count the same live quotations and the same wins")
    void theTwoReportsAgree() {
        quotations(QuotationStatus.CONVERTED, SENT, CURRENT, 8);
        quotations(QuotationStatus.ACCEPTED, SENT, CURRENT, 3);
        quotations(QuotationStatus.REJECTED, SENT, CURRENT, 1);
        // The three cases they used to disagree on.
        quotations(QuotationStatus.EXPIRED, SENT, REPLACED, 2);   // replaced, status says otherwise
        quotations(QuotationStatus.SENT, SENT, CURRENT, 1);       // nobody has answered yet
        quotations(QuotationStatus.EXPIRED, UNSENT, CURRENT, 6);  // never left the building
        publishPopulation();

        SalesPerformanceReportResponse sales = salesPerformance.execute(
                SalesPerformanceFilter.period(LocalDate.now().minusDays(30), LocalDate.now()));
        QuotationOutcomeReportResponse outcome = quotationOutcome.execute(
                LocalDate.now().minusDays(30), LocalDate.now());

        long live = expected(QuotationOutcome.WON) + expected(QuotationOutcome.LOST)
                + expected(QuotationOutcome.ABANDONED) + expected(QuotationOutcome.OPEN);

        assertThat(sales.getQuotationsCreated())
                .as("the live population is the classifier's, in both reports")
                .isEqualTo(live)
                .isEqualTo(outcome.getTotal());
        assertThat(sales.getQuotationsAccepted())
                .as("and so is the count of wins")
                .isEqualTo(expected(QuotationOutcome.WON))
                .isEqualTo(outcome.getWon());
        assertThat(outcome.getRevisedAway())
                .isEqualTo(expected(QuotationOutcome.SUPERSEDED));
    }

    @Test
    @DisplayName("the two rates differ only by their denominator, and each says which one it used")
    void theRatesDifferOnlyByDenominator() {
        quotations(QuotationStatus.CONVERTED, SENT, CURRENT, 11);
        quotations(QuotationStatus.REJECTED, SENT, CURRENT, 1);
        quotations(QuotationStatus.SENT, SENT, CURRENT, 1);
        quotations(QuotationStatus.EXPIRED, UNSENT, CURRENT, 6);
        publishPopulation();

        SalesPerformanceReportResponse sales = salesPerformance.execute(
                SalesPerformanceFilter.period(LocalDate.now().minusDays(30), LocalDate.now()));
        QuotationOutcomeReportResponse outcome = quotationOutcome.execute(
                LocalDate.now().minusDays(30), LocalDate.now());

        // Yield divides by everything written; the win rate divides by what a customer decided.
        // Both are legitimate questions — they are only a bug when they share a name.
        assertThat(sales.getQuotationAcceptanceRate())
                .as("11 of 19 written")
                .isEqualTo(57.89);
        assertThat(outcome.getWinRate())
                .as("11 of 12 decided")
                .isEqualTo(91.67);
        assertThat(outcome.getStillOpen() + outcome.getAbandoned())
                .as("and the gap between the denominators is fully accounted for")
                .isEqualTo(sales.getQuotationsCreated() - (outcome.getWon() + outcome.getLost()));
    }

    @Test
    @DisplayName("a population of nothing but replaced revisions is empty in both reports")
    void replacedOnlyPopulationIsEmptyEverywhere() {
        quotations(QuotationStatus.CLOSED, SENT, REPLACED, 4);
        publishPopulation();

        SalesPerformanceReportResponse sales = salesPerformance.execute(
                SalesPerformanceFilter.period(LocalDate.now().minusDays(30), LocalDate.now()));
        QuotationOutcomeReportResponse outcome = quotationOutcome.execute(
                LocalDate.now().minusDays(30), LocalDate.now());

        assertThat(sales.getQuotationsCreated()).isZero();
        assertThat(outcome.getTotal()).isZero();
        assertThat(outcome.getRevisedAway()).isEqualTo(4);
        assertThat(outcome.getWinRate()).as("no denominator, so no rate").isNull();
    }
}
