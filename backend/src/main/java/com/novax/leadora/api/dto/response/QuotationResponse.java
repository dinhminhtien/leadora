package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
                .build();
    }

    public static QuotationResponse fromWithDetail(QuotationEntity entity, int nights,
                                                    Integer numberOfRooms, BigDecimal pricePerNight) {
        return QuotationResponse.builder()
                .id(entity.getQuotationId().toString())
                .quoteNo("QT-" + entity.getQuotationId().toString().substring(0, 8).toUpperCase())
                .dealId(entity.getDeal() != null ? entity.getDeal().getDealId() : null)
                .dealName(entity.getDeal() != null ? entity.getDeal().getDealName() : "")
                .customerId(entity.getCustomer() != null ? entity.getCustomer().getCustomerId() : null)
                .contactName(entity.getCustomer() != null ? entity.getCustomer().getFullName() : "")
                .email(entity.getCustomer() != null ? entity.getCustomer().getEmail() : "")
                .phone(entity.getCustomer() != null ? entity.getCustomer().getPhone() : "")
                .roomType(entity.getRoomType())
                .checkInDate(entity.getCheckInDate())
                .checkOutDate(entity.getCheckOutDate())
                .numberOfRooms(numberOfRooms)
                .nights(nights)
                .pricePerNight(pricePerNight)
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
                .build();
    }
}