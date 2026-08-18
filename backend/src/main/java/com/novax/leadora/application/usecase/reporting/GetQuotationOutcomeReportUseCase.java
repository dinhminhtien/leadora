package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse;
import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse.StatusRow;
import com.novax.leadora.common.util.ReportRange;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.common.util.ReportingUtils;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UC-23.5 — View Quotation Outcome Report.
 *
 * <p>Two things about this data shape drive the implementation. First, {@code quotations.status} is
 * overwritten in place as a quotation advances, so a status snapshot cannot answer "how many were
 * approved" — that comes from {@code approved_at}, which is written once. Second, BR-22 turns an
 * edit into a new version and marks the old row SUPERSEDED, so those rows have to leave the
 * denominator or every rate falls as negotiation rounds pile up.
 */
@Service
@RequiredArgsConstructor
public class GetQuotationOutcomeReportUseCase {

    private final QuotationRepository quotationRepository;
    private final ReportRangeFactory reportRangeFactory;

    @Cacheable(value = "quotation-outcome-report", key = "#from + '_' + #to", unless = "#result == null")
    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public QuotationOutcomeReportResponse execute(LocalDate from, LocalDate to) {
        ReportRange range = reportRangeFactory.resolve(from, to);

        Map<QuotationStatus, Long> counts = ReportingUtils.countByKey(
                quotationRepository.aggregateByStatus(range.start(), range.endExclusive()));

        long superseded = ReportingUtils.countOf(counts, QuotationStatus.SUPERSEDED);
        long total = counts.values().stream().mapToLong(Long::longValue).sum() - superseded;

        long accepted = ReportingUtils.countOf(counts, QuotationStatus.ACCEPTED) 
                + ReportingUtils.countOf(counts, QuotationStatus.ACCEPTED_BY_CUSTOMER)
                + ReportingUtils.countOf(counts, QuotationStatus.RESERVATION_PENDING)
                + ReportingUtils.countOf(counts, QuotationStatus.RESERVATION_REJECTED);
        long converted = ReportingUtils.countOf(counts, QuotationStatus.CONVERTED) + ReportingUtils.countOf(counts, QuotationStatus.BOOKING_REQUEST);

        long approved = quotationRepository.countApproved(
                range.start(), range.endExclusive(), QuotationStatus.SUPERSEDED);
        long rejectedByApprover = quotationRepository.countRejectedByApprover(
                range.start(), range.endExclusive(), QuotationStatus.REJECTED);

        // Full breakdown in enum order, superseded revisions included so the numbers stay auditable
        // against the quotations screen even though they are out of the rate denominators.
        List<StatusRow> byStatus = new ArrayList<>();
        for (QuotationStatus status : QuotationStatus.values()) {
            long count = ReportingUtils.countOf(counts, status);
            if (count > 0) {
                byStatus.add(StatusRow.builder()
                        .status(status.name())
                        .label(label(status))
                        .count(count)
                        .build());
            }
        }

        return QuotationOutcomeReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .total(total)
                .superseded(superseded)
                .sent(ReportingUtils.countOf(counts, QuotationStatus.SENT) + ReportingUtils.countOf(counts, QuotationStatus.PENDING_CUSTOMER_RESPONSE))
                .rejected(ReportingUtils.countOf(counts, QuotationStatus.REJECTED))
                .expired(ReportingUtils.countOf(counts, QuotationStatus.EXPIRED))
                .accepted(accepted)
                .converted(converted)
                .approved(approved)
                .rejectedByApprover(rejectedByApprover)
                .approvalRate(ReportingUtils.calculateRate(approved, approved + rejectedByApprover))
                .acceptanceRate(ReportingUtils.calculateRate(accepted + converted, total))
                .conversionRate(ReportingUtils.calculateRate(converted, total))
                .byStatus(byStatus)
                .build();
    }

    private String label(QuotationStatus status) {
        return switch (status) {
            case DRAFT -> "Draft";
            case PENDING_APPROVAL -> "Pending approval";
            case SENT -> "Sent";
            case APPROVED -> "Approved (awaiting dispatch)";
            case REJECTED -> "Rejected";
            case EXPIRED -> "Expired";
            case CLOSED -> "Closed";
            case CONVERTED -> "Converted";
            case PENDING_REVISION -> "Pending revision";
            case ACCEPTED -> "Accepted";
            case INTERESTED -> "Interested";
            case SUPERSEDED -> "Superseded (older version)";
            case PENDING_CUSTOMER_RESPONSE -> "Pending customer response";
            case ACCEPTED_BY_CUSTOMER -> "Accepted by customer";
            case BOOKING_REQUEST -> "Booking request";
            case RESERVATION_PENDING -> "Pending reservation confirmation";
            case RESERVATION_REJECTED -> "Reservation rejected";
        };
    }
}
