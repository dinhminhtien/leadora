package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.api.dto.response.QuotationSummaryDto;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetQuotationListUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final QuotationAccessPolicy quotationAccessPolicy;

    @Transactional(readOnly = true)
    public Page<QuotationResponse> execute(
            QuotationStatus status,
            List<QuotationStatus> statuses,
            String search,
            Pageable pageable) {
        return execute(status, statuses, search, pageable, false);
    }

    /**
     * @param byPriority order by what the rep has to do next instead of by a column. The
     *                   {@code pageable} must then be unsorted — the ordering is in the query.
     */
    @Transactional(readOnly = true)
    public Page<QuotationResponse> execute(
            QuotationStatus status,
            List<QuotationStatus> statuses,
            String search,
            Pageable pageable,
            boolean byPriority) {

        // Owner-scoping: SALES is restricted to quotations they created; MANAGER/ADMIN see all.
        UserEntity currentUser = quotationAccessPolicy.currentUser();
        UUID ownerId = quotationAccessPolicy.listScopeOwnerId(currentUser);

        // Fetch DTO Projections with database-side paging and filtering
        Page<QuotationSummaryDto> summaryPage = byPriority
                ? quotationRepository.findAllSummariesByPriority(ownerId, status, statuses, search, pageable)
                : quotationRepository.findAllSummaries(ownerId, status, statuses, search, pageable);

        // Batch fetch detail lines for the retrieved page of parent quotations (2-query pattern)
        List<UUID> quotationIds = summaryPage.getContent().stream()
                .map(q -> q.quotationId())
                .toList();

        Map<UUID, List<QuotationDetailEntity>> detailsByQuotation = Map.of();
        if (!quotationIds.isEmpty()) {
            detailsByQuotation = quotationDetailRepository.findByQuotation_QuotationIdIn(quotationIds).stream()
                    .collect(Collectors.groupingBy(d -> d.getQuotation().getQuotationId()));
        }

        final Map<UUID, List<QuotationDetailEntity>> finalDetails = detailsByQuotation;

        // Map DTO Projections and detail lines to final response objects
        return summaryPage.map(dto -> mapToResponse(dto, finalDetails.getOrDefault(dto.quotationId(), List.of())));
    }

    private QuotationResponse mapToResponse(QuotationSummaryDto dto, List<QuotationDetailEntity> details) {
        List<QuotationResponse.RoomLineResponse> roomLines = details.stream()
                .map(d -> QuotationResponse.RoomLineResponse.builder()
                        .roomType(d.getDescription())
                        .numberOfRooms(d.getQuantity())
                        .pricePerNight(d.getUnitPrice())
                        .nights(d.getNights())
                        .lineTotal(d.getLineTotal())
                        .build())
                .toList();

        Integer totalRooms = details.isEmpty() ? null
                : details.stream().mapToInt(d -> d.getQuantity()).sum();
        Integer nights = details.isEmpty() ? null : details.get(0).getNights();
        BigDecimal pricePerNight = details.size() == 1 ? details.get(0).getUnitPrice() : null;

        String statusStr = dto.status() != null ? dto.status().name().toLowerCase() : "draft";
        String expiryDate = dto.validUntil() != null ? dto.validUntil().toString() : null;

        return QuotationResponse.builder()
                .id(dto.quotationId().toString())
                .quoteNo("QT-" + dto.quotationId().toString().substring(0, 8).toUpperCase())
                .dealId(dto.dealId())
                .dealName(dto.dealName() != null ? dto.dealName() : "")
                .customerId(dto.customerId())
                .contactName(dto.contactName() != null ? dto.contactName() : "")
                .email(dto.email() != null ? dto.email() : "")
                .phone(dto.phone() != null ? dto.phone() : "")
                .roomType(dto.roomType())
                .checkInDate(dto.checkInDate())
                .checkOutDate(dto.checkOutDate())
                .numberOfRooms(totalRooms)
                .nights(nights)
                .pricePerNight(pricePerNight)
                .roomLines(roomLines)
                .paymentPolicy(dto.paymentPolicy())
                .subtotal(dto.subtotal())
                .discountPercent(dto.discountPercent())
                .discountAmount(dto.discountAmount())
                .totalAmount(dto.totalAmount())
                .amount(dto.totalAmount())
                .expiryDate(expiryDate)
                .validUntil(dto.validUntil())
                .status(statusStr)
                .notes(dto.notes())
                .version(dto.version())
                .parentQuotationId(dto.parentQuotationId())
                .changeReason(dto.changeReason())
                .createdAt(dto.createdAt())
                .reservationDecision(determineReservationDecision(dto.status()))
                .build();
    }

    private String determineReservationDecision(QuotationStatus status) {
        if (status == null) return null;
        switch (status) {
            case RESERVATION_PENDING:
                return "PENDING";
            case BOOKING_REQUEST:
            case CONVERTED:
                return "APPROVED";
            case RESERVATION_REJECTED:
                return "REJECTED";
            default:
                return null;
        }
    }
}

