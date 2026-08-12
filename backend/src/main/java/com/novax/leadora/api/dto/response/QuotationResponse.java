package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class QuotationResponse {

    private String id;
    private String quoteNo;
    private UUID dealId;
    private String dealName;
    private UUID customerId;
    private String contactName;
    private String email;
    private String phone;
    private String roomType;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer numberOfRooms;
    private Integer nights;
    private BigDecimal pricePerNight;
    private List<RoomLineResponse> roomLines;
    private BigDecimal subtotal;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal amount;
    private String expiryDate;
    private String paymentPolicy;
    private LocalDate validUntil;
    private String status;
    private String notes;
    private Integer version;
    private java.util.UUID parentQuotationId;
    private String changeReason;
    private OffsetDateTime createdAt;
    private String reservationDecision;

    /** One room type + quantity + rate line within the quotation (UC-14.1/14.5). */
    @Getter
    @Builder
    public static class RoomLineResponse {
        private String roomType;
        private Integer numberOfRooms;
        private BigDecimal pricePerNight;
        private Integer nights;
        private BigDecimal lineTotal;
    }

    public static QuotationResponse from(QuotationEntity entity) {
        String customerId = entity.getCustomer() != null
                ? entity.getCustomer().getCustomerId().toString() : null;
        String contactName = entity.getCustomer() != null ? entity.getCustomer().getFullName() : "";
        String email      = entity.getCustomer() != null ? entity.getCustomer().getEmail() : "";
        String phone      = entity.getCustomer() != null ? entity.getCustomer().getPhone() : "";
        String dealName   = entity.getDeal() != null ? entity.getDeal().getDealName() : "";
        UUID   dealId     = entity.getDeal() != null ? entity.getDeal().getDealId() : null;
        String quoteNo    = "QT-" + entity.getQuotationId().toString().substring(0, 8).toUpperCase();
        String statusStr  = entity.getStatus() != null ? entity.getStatus().name().toLowerCase() : "draft";
        String expiryDate = entity.getValidUntil() != null ? entity.getValidUntil().toString() : null;

        return QuotationResponse.builder()
                .id(entity.getQuotationId().toString())
                .quoteNo(quoteNo)
                .dealId(dealId)
                .dealName(dealName)
                .customerId(customerId != null ? UUID.fromString(customerId) : null)
                .contactName(contactName)
                .email(email)
                .phone(phone)
                .roomType(entity.getRoomType())
                .checkInDate(entity.getCheckInDate())
                .checkOutDate(entity.getCheckOutDate())
                .paymentPolicy(entity.getPaymentPolicy())
                .subtotal(entity.getSubtotal())
                .discountPercent(entity.getDiscountPercent())
                .discountAmount(entity.getDiscountAmount())
                .totalAmount(entity.getTotalAmount())
                .amount(entity.getTotalAmount())
                .expiryDate(expiryDate)
                .validUntil(entity.getValidUntil())
                .status(statusStr)
                .notes(entity.getNotes())
                .version(entity.getVersion())
                .parentQuotationId(entity.getParentQuotationId())
                .changeReason(entity.getChangeReason())
                .createdAt(entity.getCreatedAt())
                .reservationDecision(determineReservationDecision(entity.getStatus()))
                .build();
    }

    /**
     * Same as {@link #from(QuotationEntity)}, plus the per-room-type breakdown
     * (BR-23: quantity/room type). {@code roomType}/{@code numberOfRooms}/{@code pricePerNight}
     * stay populated as an aggregate/summary for clients that only render the single-line
     * legacy shape (list, send/convert modals); {@code roomLines} carries the full detail.
     */
    public static QuotationResponse fromWithDetails(QuotationEntity entity, List<QuotationDetailEntity> details) {
        String customerId = entity.getCustomer() != null
                ? entity.getCustomer().getCustomerId().toString() : null;

        List<RoomLineResponse> roomLines = details.stream()
                .map(d -> RoomLineResponse.builder()
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

        return QuotationResponse.builder()
                .id(entity.getQuotationId().toString())
                .quoteNo("QT-" + entity.getQuotationId().toString().substring(0, 8).toUpperCase())
                .dealId(entity.getDeal() != null ? entity.getDeal().getDealId() : null)
                .dealName(entity.getDeal() != null ? entity.getDeal().getDealName() : "")
                .customerId(customerId != null ? UUID.fromString(customerId) : null)
                .contactName(entity.getCustomer() != null ? entity.getCustomer().getFullName() : "")
                .email(entity.getCustomer() != null ? entity.getCustomer().getEmail() : "")
                .phone(entity.getCustomer() != null ? entity.getCustomer().getPhone() : "")
                .roomType(entity.getRoomType())
                .checkInDate(entity.getCheckInDate())
                .checkOutDate(entity.getCheckOutDate())
                .numberOfRooms(totalRooms)
                .nights(nights)
                .pricePerNight(pricePerNight)
                .roomLines(roomLines)
                .paymentPolicy(entity.getPaymentPolicy())
                .subtotal(entity.getSubtotal())
                .discountPercent(entity.getDiscountPercent())
                .discountAmount(entity.getDiscountAmount())
                .totalAmount(entity.getTotalAmount())
                .amount(entity.getTotalAmount())
                .expiryDate(entity.getValidUntil() != null ? entity.getValidUntil().toString() : null)
                .validUntil(entity.getValidUntil())
                .status(entity.getStatus() != null ? entity.getStatus().name().toLowerCase() : "draft")
                .notes(entity.getNotes())
                .version(entity.getVersion())
                .parentQuotationId(entity.getParentQuotationId())
                .changeReason(entity.getChangeReason())
                .createdAt(entity.getCreatedAt())
                .reservationDecision(determineReservationDecision(entity.getStatus()))
                .build();
    }

    private static String determineReservationDecision(com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus status) {
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
