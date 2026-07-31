package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** UC-23.1 — headline KPIs and the per-rep breakdown that has to reconcile with them. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetSalesPerformanceReportUseCaseTest {

    @Mock
    private DealRepository dealRepository;

    private GetSalesPerformanceReportUseCase useCase;

    /** [kind, bucket, count, amount] */
    private final List<Object[]> aggregates = new ArrayList<>();
    /** [kind, ownerId, ownerName, count, amount] */
    private final List<Object[]> byOwner = new ArrayList<>();

    @BeforeEach
    void setUp() {
        useCase = new GetSalesPerformanceReportUseCase(
                dealRepository, new ReportRangeFactory("Asia/Ho_Chi_Minh"));
        when(dealRepository.salesPerformanceAggregates(any(), any())).thenReturn(aggregates);
        when(dealRepository.salesPerformanceByOwner(any(), any())).thenReturn(byOwner);
    }

    private void agg(String kind, String bucket, long count, long amount) {
        aggregates.add(new Object[] { kind, bucket, count, BigDecimal.valueOf(amount) });
    }

    private void owner(String kind, UUID id, String name, long count, long amount) {
        byOwner.add(new Object[] { kind, id, name, count, BigDecimal.valueOf(amount) });
    }

    private SalesPerformanceReportResponse run() {
        return useCase.execute(LocalDate.now().minusDays(30), LocalDate.now());
    }

    @Test
    @DisplayName("the whole report costs two database round trips")
    void reportIsTwoRoundTrips() {
        run();

        verify(dealRepository, times(1)).salesPerformanceAggregates(any(), any());
        verify(dealRepository, times(1)).salesPerformanceByOwner(any(), any());
        verifyNoMoreInteractions(dealRepository);
    }

    @Test
    @DisplayName("outcomes are counted by close date, acquisition by creation date")
    void dealsUseTwoSeparateTimeAxes() {
        // Ten deals opened in the period, six of them still running.
        agg("DEAL_OPENED", "WON", 3, 900);
        agg("DEAL_OPENED", "LOST", 1, 100);
        agg("DEAL_OPENED", "OPEN", 6, 600);
        // Deals that *closed* in the period — includes older deals that finally landed.
        agg("DEAL_CLOSED", "WON", 8, 4000);
        agg("DEAL_CLOSED", "LOST", 2, 500);

        SalesPerformanceReportResponse report = run();

        assertThat(report.getDealsTotal()).as("opened in period").isEqualTo(10);
        assertThat(report.getDealsOpen()).isEqualTo(6);
        assertThat(report.getPipelineValue()).isEqualByComparingTo(BigDecimal.valueOf(600));

        assertThat(report.getDealsWon()).as("closed in period, whenever opened").isEqualTo(8);
        assertThat(report.getDealsLost()).isEqualTo(2);
        assertThat(report.getWinRate()).isEqualTo(80.0);                       // 8 / (8 + 2)
        assertThat(report.getWonValue()).isEqualByComparingTo(BigDecimal.valueOf(4000));
    }

    @Test
    @DisplayName("a deal won in the period counts even though it was opened before it")
    void dealWonThisPeriodButOpenedEarlierStillCounts() {
        agg("DEAL_CLOSED", "WON", 1, 1000);

        SalesPerformanceReportResponse report = run();

        assertThat(report.getDealsTotal()).isZero();
        assertThat(report.getDealsWon())
                .as("measuring outcomes on created_at would have lost this deal entirely")
                .isEqualTo(1);
        assertThat(report.getWinRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("deals opened but still running do not drag the win rate down")
    void openDealsDoNotDiluteTheWinRate() {
        agg("DEAL_OPENED", "OPEN", 50, 5000);
        agg("DEAL_CLOSED", "WON", 1, 100);

        assertThat(run().getWinRate())
                .as("the 50 in-flight deals have no outcome yet")
                .isEqualTo(100.0);
    }

    @Test
    @DisplayName("lead totals sum every status bucket")
    void leadTotalsSumAcrossStatuses() {
        agg("LEAD", "NEW", 7, 0);
        agg("LEAD", "QUALIFIED", 1, 0);
        agg("LEAD", "CONVERTED", 21, 0);
        agg("LEAD", "LOST", 12, 0);

        SalesPerformanceReportResponse report = run();

        assertThat(report.getLeadsCreated()).isEqualTo(41);
        assertThat(report.getQualifiedLeads()).isEqualTo(1);
        assertThat(report.getLeadsConverted()).isEqualTo(21);
        assertThat(report.getLeadConversionRate()).isEqualTo(51.22);
    }

    @Test
    @DisplayName("superseded revisions leave the quotation denominator, matching UC-23.5")
    void quotationDenominatorExcludesSupersededRevisions() {
        agg("QUOTATION", "SUPERSEDED", 4, 0);
        agg("QUOTATION", "ACCEPTED", 1, 0);
        agg("QUOTATION", "CONVERTED", 1, 0);

        SalesPerformanceReportResponse report = run();

        assertThat(report.getQuotationsCreated()).isEqualTo(2);
        assertThat(report.getQuotationsAccepted())
                .as("CONVERTED is an acceptance too — UC-23.5 must agree with this number")
                .isEqualTo(2);
        assertThat(report.getQuotationAcceptanceRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("quotation-to-booking is a rate within one population, so it cannot exceed 100%")
    void conversionRateStaysWithinItsOwnPopulation() {
        agg("QUOTATION", "SENT", 8, 0);
        agg("QUOTATION", "CONVERTED", 2, 0);
        // Far more bookings than quotations — walk-ins, or bookings raised without a quote.
        agg("BOOKING", "CONFIRMED", 40, 0);

        SalesPerformanceReportResponse report = run();

        assertThat(report.getBookingsConfirmed()).isEqualTo(40);
        assertThat(report.getQuotationToBookingRate()).isEqualTo(20.0);   // 2 of 10 quotations
    }

    @Test
    @DisplayName("confirmed bookings include checked-in and checked-out")
    void bookingsCountEveryWonState() {
        agg("BOOKING", "CONFIRMED", 2, 0);
        agg("BOOKING", "CHECKED_IN", 3, 0);
        agg("BOOKING", "CHECKED_OUT", 4, 0);
        agg("BOOKING", "CANCELLED", 9, 0);

        assertThat(run().getBookingsConfirmed()).isEqualTo(9);
    }

    @Test
    @DisplayName("revenue is read from the PAID bucket")
    void revenueComesFromPaidPayments() {
        agg("REVENUE", "PAID", 6, 255_595_292L);

        assertThat(run().getRevenue()).isEqualByComparingTo(BigDecimal.valueOf(255_595_292L));
    }

    @Test
    @DisplayName("unassigned records get their own row so the breakdown reconciles with the total")
    void unassignedRecordsGetTheirOwnRow() {
        agg("LEAD", "NEW", 10, 0);
        owner("LEADS", UUID.randomUUID(), "Mai Anh", 7, 0);
        owner("LEADS", null, null, 3, 0);   // nobody assigned

        SalesPerformanceReportResponse report = run();

        assertThat(report.getLeadsCreated()).isEqualTo(10);
        assertThat(report.getReps()).hasSize(2);
        assertThat(report.getReps().stream().mapToLong(SalesPerformanceReportResponse.RepRow::getLeads).sum())
                .as("the rows must add up to the headline figure")
                .isEqualTo(report.getLeadsCreated());
        assertThat(report.getReps())
                .filteredOn(SalesPerformanceReportResponse.RepRow::isUnassigned)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getName()).isEqualTo("(Unassigned)");
                    assertThat(row.getLeads()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("one owner's four metrics merge into a single row")
    void metricsMergePerOwner() {
        UUID id = UUID.randomUUID();
        owner("LEADS", id, "Mai Anh", 4, 0);
        owner("DEALS_WON", id, "Mai Anh", 3, 37_000);
        owner("BOOKINGS", id, "Mai Anh", 4, 0);
        owner("REVENUE", id, "Mai Anh", 2, 255_036_292L);

        assertThat(run().getReps()).singleElement().satisfies(row -> {
            assertThat(row.getName()).isEqualTo("Mai Anh");
            assertThat(row.getLeads()).isEqualTo(4);
            assertThat(row.getDealsWon()).isEqualTo(3);
            assertThat(row.getWonValue()).isEqualByComparingTo(BigDecimal.valueOf(37_000));
            assertThat(row.getBookings()).isEqualTo(4);
            assertThat(row.getRevenue()).isEqualByComparingTo(BigDecimal.valueOf(255_036_292L));
        });
    }

    @Test
    @DisplayName("an owner id arriving as a string still merges into one row")
    void ownerIdAsStringIsAccepted() {
        // A native query hands back whatever the driver mapped uuid to.
        UUID id = UUID.randomUUID();
        byOwner.add(new Object[] { "LEADS", id.toString(), "Mai Anh", 4L, BigDecimal.ZERO });
        byOwner.add(new Object[] { "DEALS_WON", id, "Mai Anh", 1L, BigDecimal.TEN });

        assertThat(run().getReps()).singleElement().satisfies(row -> {
            assertThat(row.getLeads()).isEqualTo(4);
            assertThat(row.getDealsWon()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("an unassigned bucket with nothing in it is not rendered")
    void emptyUnassignedBucketIsDropped() {
        owner("LEADS", null, null, 0, 0);
        owner("LEADS", UUID.randomUUID(), "Mai Anh", 5, 0);

        assertThat(run().getReps()).hasSize(1);
    }

    @Test
    @DisplayName("reps are ranked by revenue, with the unassigned row kept last")
    void repsAreRankedByRevenue() {
        owner("REVENUE", UUID.randomUUID(), "Low", 1, 100);
        owner("REVENUE", UUID.randomUUID(), "High", 1, 900);
        owner("REVENUE", null, null, 1, 500);

        assertThat(run().getReps()).extracting(SalesPerformanceReportResponse.RepRow::getName)
                .containsExactly("High", "Low", "(Unassigned)");
    }

    @Test
    @DisplayName("an unknown discriminator is ignored rather than corrupting another bucket")
    void unknownKindIsIgnored() {
        UUID id = UUID.randomUUID();
        owner("LEADS", id, "Mai Anh", 4, 0);
        owner("SOMETHING_NEW", id, "Mai Anh", 99, 99);

        assertThat(run().getReps()).singleElement()
                .satisfies(row -> assertThat(row.getLeads()).isEqualTo(4));
    }

    @Test
    @DisplayName("an empty period renders zeroes rather than dividing by zero")
    void emptyPeriodIsSafe() {
        SalesPerformanceReportResponse report = run();

        assertThat(report.getLeadsCreated()).isZero();
        assertThat(report.getWinRate()).isZero();
        assertThat(report.getLeadConversionRate()).isZero();
        assertThat(report.getQuotationToBookingRate()).isZero();
        assertThat(report.getRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.getReps()).isEmpty();
    }
}
