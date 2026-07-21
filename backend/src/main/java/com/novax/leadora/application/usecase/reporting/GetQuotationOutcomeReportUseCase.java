package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse;
import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse.StatusRow;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.common.util.ReportingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** UC-23.5 — View Quotation Outcome Report. */
@Service
@RequiredArgsConstructor
public class GetQuotationOutcomeReportUseCase {

    private final QuotationRepository quotationRepository;

    @Cacheable(value = "quotation-outcome-report", key = "#from + '_' + #to", unless = "#result == null")
    @Transactional(readOnly = true)
    public QuotationOutcomeReportResponse execute(LocalDate from, LocalDate to) {
        OffsetDateTime start = ReportingUtils.getStartOfDayOrEpoch(from);
        OffsetDateTime end = ReportingUtils.getEndOfDayOrFuture(to);

        List<QuotationEntity> quotations = quotationRepository.findByCreatedAtRange(start, end);

        // Count per status.
        Map<QuotationStatus, Long> counts = new EnumMap<>(QuotationStatus.class);
        for (QuotationEntity q : quotations) {
            if (q.getStatus() != null) {
                counts.put(q.getStatus(), counts.getOrDefault(q.getStatus(), 0L) + 1L);
            }
        }

        long total = quotations.size();
        long sent = counts.getOrDefault(QuotationStatus.SENT, 0L);
        long approved = counts.getOrDefault(QuotationStatus.APPROVED, 0L);
        long rejected = counts.getOrDefault(QuotationStatus.REJECTED, 0L);
        long expired = counts.getOrDefault(QuotationStatus.EXPIRED, 0L);
        long accepted = counts.getOrDefault(QuotationStatus.ACCEPTED, 0L);
        long converted = counts.getOrDefault(QuotationStatus.CONVERTED, 0L);

        // Full breakdown (enum order, only non-empty statuses).
        List<StatusRow> byStatus = new ArrayList<>();
        for (QuotationStatus s : QuotationStatus.values()) {
            long c = counts.getOrDefault(s, 0L);
            if (c > 0) {
                byStatus.add(StatusRow.builder().status(s.name()).label(label(s)).count(c).build());
            }
        }

        return QuotationOutcomeReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .total(total)
                .sent(sent)
                .approved(approved)
                .rejected(rejected)
                .expired(expired)
                .accepted(accepted)
                .converted(converted)
                .approvalRate(ReportingUtils.calculateRate(approved, approved + rejected))
                .acceptanceRate(ReportingUtils.calculateRate(accepted, total))
                .conversionRate(ReportingUtils.calculateRate(converted, total))
                .byStatus(byStatus)
                .build();
    }

    private String label(QuotationStatus s) {
        return switch (s) {
            case DRAFT -> "Draft";
            case PENDING_APPROVAL -> "Pending approval";
            case SENT -> "Sent";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case EXPIRED -> "Expired";
            case CLOSED -> "Closed";
            case CONVERTED -> "Converted";
            case PENDING_REVISION -> "Pending revision";
            case ACCEPTED -> "Accepted";
            case INTERESTED -> "Interested";
            case SUPERSEDED -> "Superseded";
        };
    }
}
