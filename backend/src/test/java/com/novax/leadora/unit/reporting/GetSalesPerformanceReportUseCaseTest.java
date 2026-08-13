package com.novax.leadora.unit.reporting;
import com.novax.leadora.application.usecase.reporting.*;

import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
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

    /** [kind, bucket, ownerId, ownerName, count, amount] */
    private final List<Object[]> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        useCase = new GetSalesPerformanceReportUseCase(
                dealRepository, new ReportRangeFactory("Asia/Ho_Chi_Minh"));
        when(dealRepository.salesPerformanceAggregates(
                any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(rows);
    }

    /** A row nobody is assigned to — the shape most headline-only assertions need. */
    private void agg(String kind, String bucket, long count, long amount) {
        rows.add(new Object[] { kind, bucket, null, null, count, BigDecimal.valueOf(amount) });
    }

    private void agg(String kind, String bucket, UUID id, String name, long count, long amount) {
        rows.add(new Object[] { kind, bucket, id, name, count, BigDecimal.valueOf(amount) });
    }

    private SalesPerformanceReportResponse run() {
        return useCase.execute(SalesPerformanceFilter.period(LocalDate.now().minusDays(30), LocalDate.now()));
    }

    @Test
    @DisplayName("the whole report costs one database round trip")
    void reportIsOneRoundTrip() {
        run();

        verify(dealRepository, times(1)).salesPerformanceAggregates(
                any(), any(), any(), any(), any(), any(), anyBoolean());
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
        agg("LEAD_COHORT_CONVERTED", "CONVERTED", 21, 0);

        SalesPerformanceReportResponse report = run();

        assertThat(report.getLeadsCreated()).isEqualTo(41);
        assertThat(report.getLeadConversionRate()).isEqualTo(51.22);
    }

    @Test
    @DisplayName("qualifying a lead and then converting it does not erase the qualification")
    void qualifiedIsCountedByEventNotByCurrentStatus() {
        // Ten leads raised, every one of them qualified and then converted: by the end of the
        // period not a single lead still *sits* in QUALIFIED.
        agg("LEAD", "CONVERTED", 10, 0);
        agg("LEAD_QUALIFIED", "QUALIFIED", 10, 0);
        agg("LEAD_CONVERTED", "CONVERTED", 10, 0);
        agg("LEAD_COHORT_CONVERTED", "CONVERTED", 10, 0);

        SalesPerformanceReportResponse report = run();

        assertThat(report.getQualifiedLeads())
                .as("counting current status reported 0 here — a perfect period scored as a blank one")
                .isEqualTo(10);
        assertThat(report.getLeadsConverted()).isEqualTo(10);
        assertThat(report.getLeadConversionRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("conversion rate compares one population with itself, so a backlog month cannot exceed 100%")
    void conversionRateIsCohortBased() {
        // A quiet month for new leads, spent converting a backlog raised earlier.
        agg("LEAD", "NEW", 4, 0);
        agg("LEAD_CONVERTED", "CONVERTED", 30, 0);     // conversions that happened in the period
        agg("LEAD_COHORT_CONVERTED", "CONVERTED", 1, 0); // of this month's four, one converted

        SalesPerformanceReportResponse report = run();

        assertThat(report.getLeadsConverted()).as("activity in the period").isEqualTo(30);
        assertThat(report.getCohortConverted()).isEqualTo(1);
        assertThat(report.getLeadConversionRate())
                .as("30/4 would have printed 750%")
                .isEqualTo(25.0);
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
        agg("BOOKING_CONFIRMED", "CONFIRMED", 40, 0);

        SalesPerformanceReportResponse report = run();

        assertThat(report.getBookingsConfirmed()).isEqualTo(40);
        assertThat(report.getQuotationToBookingRate()).isEqualTo(20.0);   // 2 of 10 quotations
    }

    @Test
    @DisplayName("revenue is read from the PAID bucket")
    void revenueComesFromPaidPayments() {
        agg("REVENUE", "PAID", 6, 255_595_292L);

        assertThat(run().getRevenue()).isEqualByComparingTo(BigDecimal.valueOf(255_595_292L));
    }

    @Test
    @DisplayName("the report names the time zone its day boundaries were cut in")
    void reportStatesItsTimeZone() {
        assertThat(run().getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    @DisplayName("unassigned records get their own row so the breakdown reconciles with the total")
    @SuppressWarnings("null")
    void unassignedRecordsGetTheirOwnRow() {
        agg("LEAD", "NEW", UUID.randomUUID(), "Mai Anh", 7, 0);
        agg("LEAD", "NEW", 3, 0);   // nobody assigned

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
        agg("LEAD", "NEW", id, "Mai Anh", 4, 0);
        agg("DEAL_CLOSED", "WON", id, "Mai Anh", 3, 37_000);
        agg("BOOKING_CONFIRMED", "CONFIRMED", id, "Mai Anh", 4, 0);
        agg("REVENUE", "PAID", id, "Mai Anh", 2, 255_036_292L);

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
    @DisplayName("a rep's lost deals stay out of their won column")
    void lostDealsDoNotLandInTheWonColumn() {
        UUID id = UUID.randomUUID();
        agg("DEAL_CLOSED", "WON", id, "Mai Anh", 2, 200);
        agg("DEAL_CLOSED", "LOST", id, "Mai Anh", 5, 900);

        assertThat(run().getReps()).singleElement().satisfies(row -> {
            assertThat(row.getDealsWon()).isEqualTo(2);
            assertThat(row.getWonValue()).isEqualByComparingTo(BigDecimal.valueOf(200));
        });
    }

    @Test
    @DisplayName("the per-rep rows sum to the headline, because they are the same rows")
    void perRepRowsSumToTheHeadline() {
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        agg("DEAL_CLOSED", "WON", one, "Mai Anh", 3, 300);
        agg("DEAL_CLOSED", "WON", two, "Quang", 4, 400);
        agg("DEAL_CLOSED", "WON", 1, 100);              // unassigned

        SalesPerformanceReportResponse report = run();

        assertThat(report.getReps().stream()
                .mapToLong(row -> row.getDealsWon()).sum())
                .isEqualTo(report.getDealsWon())
                .isEqualTo(8);
    }

    @Test
    @DisplayName("an owner id arriving as a string still merges into one row")
    void ownerIdAsStringIsAccepted() {
        // A native query hands back whatever the driver mapped uuid to.
        UUID id = UUID.randomUUID();
        rows.add(new Object[] { "LEAD", "NEW", id.toString(), "Mai Anh", 4L, BigDecimal.ZERO });
        rows.add(new Object[] { "DEAL_CLOSED", "WON", id, "Mai Anh", 1L, BigDecimal.TEN });

        assertThat(run().getReps()).singleElement().satisfies(row -> {
            assertThat(row.getLeads()).isEqualTo(4);
            assertThat(row.getDealsWon()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("an owner the query touched but who did nothing is not rendered")
    void ownerWithNoActivityIsDropped() {
        agg("LEAD", "NEW", 0, 0);                                   // empty unassigned bucket
        agg("LEAD", "NEW", UUID.randomUUID(), "Mai Anh", 5, 0);

        assertThat(run().getReps()).hasSize(1);
    }

    @Test
    @DisplayName("a rep who only appears in a headline-only kind does not become a row")
    void headlineOnlyKindsDoNotCreateRepRows() {
        agg("LEAD_QUALIFIED", "QUALIFIED", UUID.randomUUID(), "Mai Anh", 9, 0);

        SalesPerformanceReportResponse report = run();

        assertThat(report.getQualifiedLeads()).isEqualTo(9);
        assertThat(report.getReps())
                .as("the rep table shows leads, wins, bookings and cash — not every kind counted")
                .isEmpty();
    }

    @Test
    @DisplayName("reps are ranked by revenue, with the unassigned row kept last")
    @SuppressWarnings("null")
    void repsAreRankedByRevenue() {
        agg("REVENUE", "PAID", UUID.randomUUID(), "Low", 1, 100);
        agg("REVENUE", "PAID", UUID.randomUUID(), "High", 1, 900);
        agg("REVENUE", "PAID", 1, 500);

        assertThat(run().getReps()).extracting(SalesPerformanceReportResponse.RepRow::getName)
                .containsExactly("High", "Low", "(Unassigned)");
    }

    @Test
    @DisplayName("an unknown discriminator is ignored rather than corrupting another bucket")
    void unknownKindIsIgnored() {
        UUID id = UUID.randomUUID();
        agg("LEAD", "NEW", id, "Mai Anh", 4, 0);
        agg("SOMETHING_NEW", "X", id, "Mai Anh", 99, 99);

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

    @Test
    @DisplayName("filters reach the query, and an empty segment short-circuits the segment lookup")
    void filtersArePassedThrough() {
        UUID rep = UUID.randomUUID();
        useCase.execute(new SalesPerformanceFilter(
                LocalDate.now().minusDays(7), LocalDate.now(), rep, "Website", "Banquet Hall", true));

        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> source = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> service = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> corporate = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> segmentOff = ArgumentCaptor.forClass(Boolean.class);
        verify(dealRepository).salesPerformanceAggregates(
                any(), any(), owner.capture(), source.capture(), service.capture(),
                corporate.capture(), segmentOff.capture());

        assertThat(owner.getValue()).isEqualTo(rep.toString());
        assertThat(source.getValue()).isEqualTo("Website");
        assertThat(service.getValue()).isEqualTo("Banquet Hall");
        assertThat(corporate.getValue()).isEqualTo("true");
        assertThat(segmentOff.getValue()).isFalse();
    }

    @Test
    @DisplayName("the unfiltered screen sends nulls, not empty strings, so the SQL casts stay NULL")
    void unfilteredRequestSendsNulls() {
        useCase.execute(new SalesPerformanceFilter(null, null, null, "", "  ", null));

        ArgumentCaptor<String> source = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> segmentOff = ArgumentCaptor.forClass(Boolean.class);
        verify(dealRepository).salesPerformanceAggregates(
                any(), any(), any(), source.capture(), any(), any(), segmentOff.capture());

        assertThat(source.getValue()).isNull();
        assertThat(segmentOff.getValue())
                .as("a blank filter box must not turn into a segment nobody matches")
                .isTrue();
    }
}
