package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse;
import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse.RepRow;
import com.novax.leadora.common.util.ReportRange;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.common.util.ReportingUtils;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * <p>One database round trip. The consolidated {@code salesPerformanceAggregates} statement returns
 * every population already grouped by status <em>and</em> by owner, so the headline KPIs are the
 * per-rep rows summed — the two can no longer drift apart, which is a property the previous
 * two-statement version could only promise.
 *
 * <p>Three time axes, deliberately, and the report is laid out to match: what was <b>opened</b> in
 * the period (created_at), what was <b>closed</b> in it (closed_at, qualified_at, confirmed_at) and
 * what was <b>collected</b> in it (paid_at). Mixing them is what made the old win rate wrong.
 *
 * <p>Event-dated KPIs — qualified leads, confirmed bookings — are counted on when the event
 * happened, reconstructed from activity_log, not on the record's current status. Counting current
 * status made {@code qualifiedLeads} <em>fall</em> when a rep qualified a lead and then converted
 * it, because the lead left the bucket. A metric that punishes progress is worse than no metric.
 */
@Service
@RequiredArgsConstructor
public class GetSalesPerformanceReportUseCase {

    /** A quotation the customer said yes to, whether or not it became a booking yet. */
    private static final Set<String> ACCEPTED_QUOTATIONS = Set.of(
            QuotationStatus.ACCEPTED.name(), QuotationStatus.CONVERTED.name(),
            QuotationStatus.ACCEPTED_BY_CUSTOMER.name(), QuotationStatus.BOOKING_REQUEST.name());

    private static final int MAX_REPS = 50;
    private static final String UNASSIGNED_LABEL = "(Unassigned)";

    // Discriminators emitted by the consolidated query — shared with the rep scorecard, which reads
    // the same statement.
    private static final String KIND_LEAD = SalesAggregateKinds.LEAD;
    private static final String KIND_LEAD_QUALIFIED = SalesAggregateKinds.LEAD_QUALIFIED;
    private static final String KIND_LEAD_CONVERTED = SalesAggregateKinds.LEAD_CONVERTED;
    private static final String KIND_LEAD_COHORT_CONVERTED = SalesAggregateKinds.LEAD_COHORT_CONVERTED;
    private static final String KIND_DEAL_OPENED = SalesAggregateKinds.DEAL_OPENED;
    private static final String KIND_DEAL_CLOSED = SalesAggregateKinds.DEAL_CLOSED;
    private static final String KIND_QUOTATION = SalesAggregateKinds.QUOTATION;
    private static final String KIND_BOOKING_CONFIRMED = SalesAggregateKinds.BOOKING_CONFIRMED;
    private static final String KIND_REVENUE = SalesAggregateKinds.REVENUE;

    private static final String BUCKET_QUALIFIED = SalesAggregateKinds.BUCKET_QUALIFIED;
    private static final String BUCKET_CONVERTED = SalesAggregateKinds.BUCKET_CONVERTED;
    private static final String BUCKET_CONFIRMED = SalesAggregateKinds.BUCKET_CONFIRMED;
    private static final String BUCKET_PAID = SalesAggregateKinds.BUCKET_PAID;

    private final DealRepository dealRepository;
    private final ReportRangeFactory reportRangeFactory;

    @Cacheable(value = "sales-performance-report", key = "#filter.cacheKey()", unless = "#result == null")
    @Transactional(readOnly = true)
    public SalesPerformanceReportResponse execute(SalesPerformanceFilter filter) {
        ReportRange range = reportRangeFactory.resolve(filter.dateFrom(), filter.dateTo());
        Aggregates aggregates = read(range, filter);
        Totals totals = aggregates.totals;

        long leadsCreated = totals.count(KIND_LEAD);
        long qualifiedLeads = totals.count(KIND_LEAD_QUALIFIED, BUCKET_QUALIFIED);
        long leadsConverted = totals.count(KIND_LEAD_CONVERTED, BUCKET_CONVERTED);
        long cohortConverted = totals.count(KIND_LEAD_COHORT_CONVERTED, BUCKET_CONVERTED);

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
        long quotationsConverted = totals.count(KIND_QUOTATION, QuotationStatus.CONVERTED.name())
                + totals.count(KIND_QUOTATION, QuotationStatus.BOOKING_REQUEST.name());

        long bookingsConfirmed = totals.count(KIND_BOOKING_CONFIRMED, BUCKET_CONFIRMED);
        BigDecimal revenue = totals.amount(KIND_REVENUE, BUCKET_PAID);

        return SalesPerformanceReportResponse.builder()
                .dateFrom(filter.dateFrom())
                .dateTo(filter.dateTo())
                .timezone(reportRangeFactory.zone().getId())
                .leadsCreated(leadsCreated)
                .qualifiedLeads(qualifiedLeads)
                .leadsConverted(leadsConverted)
                .cohortConverted(cohortConverted)
                // Cohort over cohort: of the leads raised in this period, how many ever converted.
                // The old form divided conversions that happened in the period by leads created in
                // it — two different populations, so the rate could exceed 100% in a month spent
                // closing out a backlog, and read as a collapse in a month full of fresh leads.
                .leadConversionRate(ReportingUtils.calculateRate(cohortConverted, leadsCreated))
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
                .reps(buildReps(aggregates.byUser))
                .build();
    }

    /**
     * The single round trip: {@code [kind, bucket, ownerId, ownerName, count, amount]}.
     *
     * <p>Each row is folded into both views at once — the headline totals and the per-rep table —
     * which is the whole point of carrying the owner in the query.
     */
    private Aggregates read(ReportRange range, SalesPerformanceFilter filter) {
        Aggregates aggregates = new Aggregates();
        List<Object[]> rows = dealRepository.salesPerformanceAggregates(
                range.start(), range.endExclusive(),
                filter.ownerIdParam(), filter.sourceParam(), filter.serviceParam(),
                filter.corporateParam(), filter.segmentOff());

        for (Object[] row : rows) {
            String kind = asString(row[0]);
            String bucket = asString(row[1]);
            long count = ReportingUtils.toLong(row[4]);
            BigDecimal amount = ReportingUtils.toBigDecimal(row[5]);

            aggregates.totals.put(kind, bucket, count, amount);

            RepAgg agg = aggregates.forUser(row[2], row[3]);
            switch (kind) {
                case KIND_LEAD -> agg.leads += count;
                case KIND_DEAL_CLOSED -> {
                    if (DealStatus.WON.name().equals(bucket)) {
                        agg.dealsWon += count;
                        agg.wonValue = agg.wonValue.add(amount);
                    }
                }
                case KIND_BOOKING_CONFIRMED -> agg.bookings += count;
                case KIND_REVENUE -> agg.revenue = agg.revenue.add(amount);
                default -> { /* the other kinds are headline-only; ignore them here */ }
            }
        }
        return aggregates;
    }

    /**
     * The per-rep breakdown.
     *
     * <p>Records with no assignee land in an explicit "(Unassigned)" row rather than being dropped,
     * so the table reconciles with the headline KPIs — a mismatch there reads to anyone checking as
     * the report being wrong.
     */
    private List<RepRow> buildReps(Map<UUID, RepAgg> byUser) {
        List<RepRow> named = new ArrayList<>();
        RepRow unassigned = null;
        for (Map.Entry<UUID, RepAgg> entry : byUser.entrySet()) {
            RepAgg agg = entry.getValue();
            boolean isUnassigned = entry.getKey() == null;
            if (!agg.hasActivity()) {
                continue; // an owner the query touched but who did nothing in this period
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

    /** The two views of the same rows: headline totals, and the per-owner breakdown. */
    private static final class Aggregates {
        private final Totals totals = new Totals();
        private final Map<UUID, RepAgg> byUser = new LinkedHashMap<>();

        /** {@code ownerId} may be null — that is the unassigned bucket, deliberately kept. */
        @SuppressWarnings("null")
        RepAgg forUser(Object ownerId, Object ownerName) {
            UUID key = toUuid(ownerId);
            return byUser.computeIfAbsent(key, id -> {
                RepAgg agg = new RepAgg();
                agg.name = (ownerName instanceof String name && !name.isBlank())
                        ? name
                        : (id == null ? UNASSIGNED_LABEL : id.toString());
                return agg;
            });
        }
    }

    /** Counts and amounts from the consolidated query, summed across owners, keyed by kind+bucket. */
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
