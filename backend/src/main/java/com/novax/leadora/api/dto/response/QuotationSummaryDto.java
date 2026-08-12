package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuotationSummaryDto(
    UUID quotationId,
    UUID dealId,
    String dealName,
    UUID customerId,
    String contactName,
    String email,
    String phone,
    String roomType,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    String paymentPolicy,
    BigDecimal subtotal,
    BigDecimal discountPercent,
    BigDecimal discountAmount,
    BigDecimal totalAmount,
    LocalDate validUntil,
    QuotationStatus status,
    String notes,
    Integer version,
    UUID parentQuotationId,
    String changeReason,
    OffsetDateTime createdAt
) {}
