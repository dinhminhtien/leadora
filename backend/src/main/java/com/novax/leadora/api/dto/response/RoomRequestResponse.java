package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.RoomRequestEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomRequestResponse {

    private UUID requestId;
    private UUID quotationId;
    /** Same display convention as QuotationResponse.quoteNo. */
    private String quoteNo;
    private String customerName;
    private String roomTypeRequested;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer quantity;
    private RoomRequestStatus status;
    /** Allotment context captured when the request was raised — see RoomRequestEntity. */
    private String requesterNote;
    private String reservationNote;
    private OffsetDateTime heldUntil;
    private String requestedByName;
    private String respondedByName;
    private OffsetDateTime respondedAt;
    private OffsetDateTime createdAt;

    public static RoomRequestResponse from(RoomRequestEntity entity) {
        UUID quotationId = entity.getQuotation() != null ? entity.getQuotation().getQuotationId() : null;
        return RoomRequestResponse.builder()
                .requestId(entity.getRequestId())
                .quotationId(quotationId)
                .quoteNo(quotationId != null
                        ? "QT-" + quotationId.toString().substring(0, 8).toUpperCase() : null)
                .customerName(entity.getQuotation() != null && entity.getQuotation().getCustomer() != null
                        ? entity.getQuotation().getCustomer().getFullName() : null)
                .roomTypeRequested(entity.getRoomTypeRequested())
                .checkInDate(entity.getCheckInDate())
                .checkOutDate(entity.getCheckOutDate())
                .quantity(entity.getQuantity())
                .status(entity.getStatus())
                .requesterNote(entity.getRequesterNote())
                .reservationNote(entity.getReservationNote())
                .heldUntil(entity.getHeldUntil())
                .requestedByName(entity.getRequestedBy() != null ? entity.getRequestedBy().getFullName() : null)
                .respondedByName(entity.getRespondedBy() != null ? entity.getRespondedBy().getFullName() : null)
                .respondedAt(entity.getRespondedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
