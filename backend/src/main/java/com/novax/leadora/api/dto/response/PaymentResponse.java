package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentType;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private UUID paymentId;
    private UUID bookingId;
    private String bookingCode;
    private UUID customerId;
    private String customerName;
    private UUID createdById;
    private String createdByName;
    private String paymentMethod;
    private String gatewayProvider;
    private String gatewayTransactionId;
    private BigDecimal amount;
    private PaymentType paymentType;
    private PaymentStatus status;
    private LocalDate dueDate;
    private OffsetDateTime paidAt;
    private String qrCodeUrl;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static PaymentResponse from(PaymentEntity entity) {
        if (entity == null) return null;
        return PaymentResponse.builder()
                .paymentId(entity.getPaymentId())
                .bookingId(entity.getBooking() != null ? entity.getBooking().getBookingId() : null)
                .bookingCode(entity.getBooking() != null ? entity.getBooking().getBookingCode() : null)
                .customerId((entity.getBooking() != null && entity.getBooking().getCustomer() != null) 
                        ? entity.getBooking().getCustomer().getCustomerId() : null)
                .customerName((entity.getBooking() != null && entity.getBooking().getCustomer() != null) 
                        ? entity.getBooking().getCustomer().getFullName() : null)
                .createdById(entity.getCreatedBy() != null ? entity.getCreatedBy().getUserId() : null)
                .createdByName(entity.getCreatedBy() != null ? entity.getCreatedBy().getFullName() : null)
                .paymentMethod(entity.getPaymentMethod())
                .gatewayProvider(entity.getGatewayProvider())
                .gatewayTransactionId(entity.getGatewayTransactionId())
                .amount(entity.getAmount())
                .paymentType(entity.getPaymentType())
                .status(entity.getStatus())
                .dueDate(entity.getDueDate())
                .paidAt(entity.getPaidAt())
                .qrCodeUrl(entity.getQrCodeUrl())
                .notes(entity.getVerificationNote())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
