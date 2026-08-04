package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse;
import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse.RepRow;
import com.novax.leadora.common.util.ReportRange;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.common.util.ReportingUtils;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UC-23.1 — View Sales Performance Statistics Report.
 *
 * <p>Two database round trips: one for the headline aggregates, one for the per-rep breakdown.
 *
 * <p>The original implementation loaded whole entities from five tables and counted them with Java
 * streams — with the filters left empty, which is how the screen opens, that is a full scan of
 * leads, deals, quotations, bookings and payments into heap. Replacing that with ten small grouped
 * queries fixed the memory profile but not the latency: the database is remote, so the cost is
 * round trips rather than rows, and ten of them is slower than five fat ones at these table sizes.
 * The two UNION ALL statements behind {@code salesPerformanceAggregates} /
 * {@code salesPerformanceByOwner} give both — aggregation in SQL, and two trips.
 */
@Service
@RequiredArgsConstructor
public class GetSalesPerformanceReportUseCase {

    /** Booking states that represent business actually won. */
    private static final Set<String> CONFIRMED_BOOKINGS = Set.of(
            BookingStatus.CONFIRMED.name(),
            BookingStatus.CHECKED_IN.name(),
            BookingStatus.CHECKED_OUT.name());
    /** A quotation the customer said yes to, whether or not it became a booking yet. */
    private static final Set<String> ACCEPTED_QUOTATIONS = Set.of(
            QuotationStatus.ACCEPTED.name(), QuotationStatus.CONVERTED.name());

    private static final int MAX_REPS = 50;
    private static final String UNASSIGNED_LABEL = "(Unassigned)";

    // Discriminators emitted by the two consolidated queries.
    private static final String KIND_LEAD = "LEAD";
    private static final String KIND_DEAL_OPENED = "DEAL_OPENED";
    private static final String KIND_DEAL_CLOSED = "DEAL_CLOSED";
    private static final String KIND_QUOTATION = "QUOTATION";
    private static final String KIND_BOOKING = "BOOKING";
    private static final String KIND_REVENUE = "REVENUE";
    private static final String OWNER_LEADS = "LEADS";
    private static final String OWNER_DEALS_WON = "DEALS_WON";
    private static final String OWNER_BOOKINGS = "BOOKINGS";
    private static final String OWNER_REVENUE = "REVENUE";

    private final DealRepository dealRepository;
    private final ReportRangeFactory reportRangeFactory;

    @Cacheable(value = "sales-performance-report", key = "#from + '_' + #to", unless = "#result == null")
    @Transactional(readOnly = true)
    public SalesPerformanceReportResponse execute(LocalDate from, LocalDate to) {
        ReportRange range = reportRangeFactory.resolve(from, to);
        Totals totals = readTotals(range);

        long leadsCreated = totals.count(KIND_LEAD);
        long qualifiedLeads = totals.count(KIND_LEAD, LeadStatus.QUALIFIED.name());
        long leadsConverted = totals.count(KIND_LEAD, LeadStatus.CONVERTED.name());

        long dealsTotal = totals.count(KIND_DEAL_OPENED);
        long dealsOpen = totals.count(KIND_DEAL_OPENED, DealStatus.OPEN.name());
        BigDecimal pipelineValue = totals.amount(KIND_DEAL_OPENED, DealStatus.OPEN.name());

        long dealsWon = totals.count(KIND_DEAL_CLOSED, DealStatus.WON.name());
        long dealsLost = totals.count(KIND_DEAL_CLOSED, DealStatus.LOST.name());
        BigDecimal wonValue = totals.amount(KIND_DEAL_CLOSED, DealStatus.WON.name());

        // Superseded revisions leave the denominator so this matches UC-23.5 (BR-22).
        long quotationsCreated = totals.count(KIND_QUOTATION)
                - totals.count(KIND_QUOTATION, QuotationStatus.SUPERSEDED.name());
        long quotationsAccepted = totals.countIn(KIND_QUOTATION, ACCEPTED_QUOTATIONS);
        long quotationsConverted = totals.count(KIND_QUOTATION, QuotationStatus.CONVERTED.name());

        long bookingsConfirmed = totals.countIn(KIND_BOOKING, CONFIRMED_BOOKINGS);
        BigDecimal revenue = totals.amount(KIND_REVENUE, "PAID");

        return SalesPerformanceReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .leadsCreated(leadsCreated)
                .qualifiedLeads(qualifiedLeads)
                .leadsConverted(leadsConverted)
                .leadConversionRate(ReportingUtils.calculateRate(leadsConverted, leadsCreated))
                .dealsTotal(dealsTotal)
                .dealsOpen(dealsOpen)
                .dealsWon(dealsWon)
                .dealsLost(dealsLost)
                .winRate(ReportingUtils.calculateRate(dealsWon, dealsWon + dealsLost))
                .wonValue(wonValue)
                .pipelineValue(pipelineValue)
                .quotationsCreated(quotationsCreated)
                .quotationsAccepted(quotationsAccepted)
                .quotationAcceptanceRate(ReportingUtils.calculateRate(quotationsAccepted, quotationsCreated))
                .bookingsConfirmed(bookingsConfirmed)
                .quotationToBookingRate(ReportingUtils.calculateRate(quotationsConverted, quotationsCreated))
                .revenue(revenue)
                .reps(buildReps(range))
                .build();
    }

    /** Round trip 1: {@code [kind, bucket, count, amount]}. */
    private Totals readTotals(ReportRange range) {
        Totals totals = new Totals();
        for (Object[] row : dealRepository.salesPerformanceAggregates(range.start(), range.endExclusive())) {
            totals.put(asString(row[0]), asString(row[1]),
                    ReportingUtils.toLong(row[2]), ReportingUtils.toBigDecimal(row[3]));
        }
        return totals;
    }

    /**
     * Round trip 2: the per-rep breakdown.
     *
     * <p>Records with no assignee land in an explicit "(Unassigned)" row rather than being dropped,
     * so the table reconciles with the headline KPIs — a mismatch there reads to anyone checking as
     * the report being wrong.
     */
    @SuppressWarnings("null")
    private List<RepRow> buildReps(ReportRange range) {
        Map<UUID, RepAgg> byUser = new LinkedHashMap<>();

        for (Object[] row : dealRepository.salesPerformanceByOwner(range.start(), range.endExclusive())) {
            String kind = asString(row[0]);
            RepAgg agg = forUser(byUser, row[1], row[2]);
            long count = ReportingUtils.toLong(row[3]);
            BigDecimal amount = ReportingUtils.toBigDecimal(row[4]);
            switch (kind) {
                case OWNER_LEADS -> agg.leads += count;
                case OWNER_DEALS_WON -> {
                    agg.dealsWon += count;
                    agg.wonValue = agg.wonValue.add(amount);
                }
                case OWNER_BOOKINGS -> agg.bookings += count;
                case OWNER_REVENUE -> agg.revenue = agg.revenue.add(amount);
                default -> { /* an unknown discriminator must not corrupt the other buckets */ }
            }
        }

        List<RepRow> named = new ArrayList<>();
        RepRow unassigned = null;
        for (Map.Entry<UUID, RepAgg> entry : byUser.entrySet()) {
            RepAgg agg = entry.getValue();
            boolean isUnassigned = entry.getKey() == null;
            if (isUnassigned && !agg.hasActivity()) {
                continue; // nothing unassigned in this period — no need for the extra row
            }
            RepRow row = RepRow.builder()
                    .name(isUnassigned ? UNASSIGNED_LABEL : agg.name)
                    .leads(agg.leads)
                    .dealsWon(agg.dealsWon)
                    .wonValue(agg.wonValue)
                    .bookings(agg.bookings)
                    .revenue(agg.revenue)
                    .unassigned(isUnassigned)
                    .build();
            if (isUnassigned) {
                unassigned = row;
            } else {
                named.add(row);
            }
        }

        named.sort(Comparator.comparing(RepRow::getRevenue, Comparator.nullsLast(Comparator.reverseOrder())));
        List<RepRow> rows = new ArrayList<>(
                named.size() > MAX_REPS ? named.subList(0, MAX_REPS) : named);
        // Appended after the cap so the reconciling row can never be truncated away.
        if (unassigned != null) {
            rows.add(unassigned);
        }
        return rows;
    }

    /** {@code ownerId} may be null — that is the unassigned bucket, deliberately kept. */
    private RepAgg forUser(Map<UUID, RepAgg> byUser, Object ownerId, Object ownerName) {
        UUID key = toUuid(ownerId);
        return byUser.computeIfAbsent(key, id -> {
            RepAgg agg = new RepAgg();
            agg.name = (ownerName instanceof String name && !name.isBlank())
                    ? name
                    : (id == null ? UNASSIGNED_LABEL : id.toString());
            return agg;
        });
    }

    /** A native query hands back whatever the driver mapped uuid to; accept both forms. */
    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    /** Counts and amounts from the consolidated aggregate query, keyed by kind and bucket. */
    private static final class Totals {
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final Map<String, BigDecimal> amounts = new LinkedHashMap<>();

        @SuppressWarnings("null")
        void put(String kind, String bucket, long count, BigDecimal amount) {
            counts.merge(key(kind, bucket), count, Long::sum);
            amounts.merge(key(kind, bucket), amount, BigDecimal::add);
        }

        /** Total across every bucket of a kind. */
        @SuppressWarnings("null")
        long count(String kind) {
            String prefix = kind + "|";
            return counts.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
        }

        long count(String kind, String bucket) {
            return counts.getOrDefault(key(kind, bucket), 0L);
        }

        long countIn(String kind, Set<String> buckets) {
            return buckets.stream().mapToLong(bucket -> count(kind, bucket)).sum();
        }

        BigDecimal amount(String kind, String bucket) {
            return amounts.getOrDefault(key(kind, bucket), BigDecimal.ZERO);
        }

        private static String key(String kind, String bucket) {
            return kind + "|" + bucket;
        }
    }

    private static class RepAgg {
        String name;
        long leads;
        long dealsWon;
        BigDecimal wonValue = BigDecimal.ZERO;
        long bookings;
        BigDecimal revenue = BigDecimal.ZERO;

        boolean hasActivity() {
            return leads > 0 || dealsWon > 0 || bookings > 0
                    || revenue.compareTo(BigDecimal.ZERO) != 0;
        }
    }
}
