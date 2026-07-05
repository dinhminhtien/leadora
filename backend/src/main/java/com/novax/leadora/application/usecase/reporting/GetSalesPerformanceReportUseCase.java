package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse;
import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse.RepRow;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** UC-23.1 — View Sales Performance Statistics Report. */
@Service
@RequiredArgsConstructor
public class GetSalesPerformanceReportUseCase {

    private static final Set<BookingStatus> CONFIRMED_BOOKINGS =
            EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT);
    private static final Set<QuotationStatus> ACCEPTED_QUOTATIONS =
            EnumSet.of(QuotationStatus.ACCEPTED, QuotationStatus.CONVERTED);
    private static final int MAX_REPS = 50;

    private final LeadRepository leadRepository;
    private final DealRepository dealRepository;
    private final QuotationRepository quotationRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    @Cacheable(value = "sales-performance-report", key = "#from + '_' + #to", unless = "#result == null")
    @Transactional(readOnly = true)
    public SalesPerformanceReportResponse execute(LocalDate from, LocalDate to) {
        OffsetDateTime start = from != null ? from.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)
                : OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        OffsetDateTime end = to != null ? to.atTime(java.time.LocalTime.MAX).atOffset(java.time.ZoneOffset.UTC)
                : OffsetDateTime.of(2100, 12, 31, 23, 59, 59, 999999999, java.time.ZoneOffset.UTC);

        List<LeadEntity> leads = leadRepository.findByCreatedAtRange(start, end);
        List<DealEntity> deals = dealRepository.findByCreatedAtRange(start, end);
        List<QuotationEntity> quotations = quotationRepository.findByCreatedAtRange(start, end);
        List<BookingEntity> bookings = bookingRepository.findByCreatedAtRange(start, end);
        List<PaymentEntity> paidPayments = paymentRepository.findPaidPaymentsForReport(PaymentStatus.PAID, start, end);

        long leadsCreated = leads.size();
        long qualifiedLeads = leads.stream().filter(l -> l.getStatus() == LeadStatus.QUALIFIED).count();
        long leadsConverted = leads.stream().filter(l -> l.getStatus() == LeadStatus.CONVERTED).count();

        long dealsWon = deals.stream().filter(d -> d.getStatus() == DealStatus.WON).count();
        long dealsLost = deals.stream().filter(d -> d.getStatus() == DealStatus.LOST).count();
        long dealsOpen = deals.stream().filter(d -> d.getStatus() == DealStatus.OPEN).count();
        BigDecimal wonValue = sumRevenue(deals.stream().filter(d -> d.getStatus() == DealStatus.WON).toList());
        BigDecimal pipelineValue = sumRevenue(deals.stream().filter(d -> d.getStatus() == DealStatus.OPEN).toList());

        long quotationsCreated = quotations.size();
        long quotationsAccepted = quotations.stream()
                .filter(q -> ACCEPTED_QUOTATIONS.contains(q.getStatus())).count();

        long bookingsConfirmed = bookings.stream()
                .filter(b -> CONFIRMED_BOOKINGS.contains(b.getStatus())).count();
        BigDecimal revenue = paidPayments.stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        return SalesPerformanceReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .leadsCreated(leadsCreated)
                .qualifiedLeads(qualifiedLeads)
                .leadsConverted(leadsConverted)
                .leadConversionRate(rate(leadsConverted, leadsCreated))
                .dealsTotal(deals.size())
                .dealsOpen(dealsOpen)
                .dealsWon(dealsWon)
                .dealsLost(dealsLost)
                .winRate(rate(dealsWon, dealsWon + dealsLost))
                .wonValue(wonValue)
                .pipelineValue(pipelineValue)
                .quotationsCreated(quotationsCreated)
                .quotationsAccepted(quotationsAccepted)
                .quotationAcceptanceRate(rate(quotationsAccepted, quotationsCreated))
                .bookingsConfirmed(bookingsConfirmed)
                .quotationToBookingRate(rate(bookingsConfirmed, quotationsCreated))
                .revenue(revenue)
                .reps(buildReps(leads, deals, bookings, paidPayments))
                .build();
    }

    /** Per-rep breakdown keyed by the record's assigned user. */
    private List<RepRow> buildReps(List<LeadEntity> leads, List<DealEntity> deals,
                                   List<BookingEntity> bookings, List<PaymentEntity> paidPayments) {
        Map<UUID, RepAgg> byUser = new LinkedHashMap<>();

        for (LeadEntity l : leads) {
            RepAgg a = forUser(byUser, l.getAssignedUser());
            if (a != null) a.leads++;
        }
        for (DealEntity d : deals) {
            if (d.getStatus() == DealStatus.WON) {
                RepAgg a = forUser(byUser, d.getAssignedUser());
                if (a != null) {
                    a.dealsWon++;
                    a.wonValue = a.wonValue.add(d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO);
                }
            }
        }
        for (BookingEntity b : bookings) {
            RepAgg a = forUser(byUser, b.getAssignedUser());
            if (a != null) a.bookings++;
        }
        for (PaymentEntity p : paidPayments) {
            UserEntity rep = p.getBooking() != null ? p.getBooking().getAssignedUser() : null;
            RepAgg a = forUser(byUser, rep);
            if (a != null) a.revenue = a.revenue.add(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
        }

        List<RepRow> rows = new ArrayList<>();
        for (RepAgg a : byUser.values()) {
            rows.add(RepRow.builder()
                    .name(a.name)
                    .leads(a.leads)
                    .dealsWon(a.dealsWon)
                    .wonValue(a.wonValue)
                    .bookings(a.bookings)
                    .revenue(a.revenue)
                    .build());
        }
        rows.sort(Comparator.comparing(r -> r.getRevenue(), Comparator.nullsLast(Comparator.reverseOrder())));
        return rows.size() > MAX_REPS ? rows.subList(0, MAX_REPS) : rows;
    }

    private RepAgg forUser(Map<UUID, RepAgg> byUser, UserEntity user) {
        if (user == null || user.getUserId() == null) {
            return null; // unassigned records are counted in totals only
        }
        return byUser.computeIfAbsent(user.getUserId(), id -> {
            RepAgg a = new RepAgg();
            a.name = user.getFullName() != null ? user.getFullName() : id.toString();
            return a;
        });
    }

    private BigDecimal sumRevenue(List<DealEntity> deals) {
        return deals.stream()
                .map(d -> d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
    }



    private double rate(long part, long whole) {
        if (whole <= 0) return 0;
        return Math.round((part * 10000.0 / whole)) / 100.0; // 2 decimals
    }

    private static class RepAgg {
        String name;
        long leads;
        long dealsWon;
        BigDecimal wonValue = BigDecimal.ZERO;
        long bookings;
        BigDecimal revenue = BigDecimal.ZERO;
    }
}
