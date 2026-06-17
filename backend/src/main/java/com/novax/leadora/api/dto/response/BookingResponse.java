package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingResponse {

    private UUID bookingId;
    private UUID quotationId;
    private UUID customerId;
    private String customerName;
    private UUID assignedUserId;
    private String assignedUserName;
    private String bookingCode;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private BookingStatus status;
    private String specialRequests;
    private String rejectionReason;
    private BigDecimal totalAmount;
    private List<BookingDetailResponse> details;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static BookingResponse from(BookingEntity entity) {
        return BookingResponse.builder()
                .bookingId(entity.getBookingId())
                .quotationId(entity.getQuotation() != null ? entity.getQuotation().getQuotationId() : null)
                .customerId(entity.getCustomer() != null ? entity.getCustomer().getCustomerId() : null)
                .customerName(entity.getCustomer() != null ? entity.getCustomer().getFullName() : null)
                .assignedUserId(entity.getAssignedUser() != null ? entity.getAssignedUser().getUserId() : null)
                .assignedUserName(entity.getAssignedUser() != null ? entity.getAssignedUser().getFullName() : null)
                .bookingCode(entity.getBookingCode())
                .checkInDate(entity.getCheckInDate())
                .checkOutDate(entity.getCheckOutDate())
                .status(entity.getStatus())
                .specialRequests(entity.getSpecialRequests())
                .rejectionReason(entity.getRejectionReason())
                .totalAmount(entity.getTotalAmount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static BookingResponse from(BookingEntity entity, List<BookingDetailResponse> details) {
        return BookingResponse.builder()
                .bookingId(entity.getBookingId())
                .quotationId(entity.getQuotation() != null ? entity.getQuotation().getQuotationId() : null)
                .customerId(entity.getCustomer() != null ? entity.getCustomer().getCustomerId() : null)
                .customerName(entity.getCustomer() != null ? entity.getCustomer().getFullName() : null)
                .assignedUserId(entity.getAssignedUser() != null ? entity.getAssignedUser().getUserId() : null)
                .assignedUserName(entity.getAssignedUser() != null ? entity.getAssignedUser().getFullName() : null)
                .bookingCode(entity.getBookingCode())
                .checkInDate(entity.getCheckInDate())
                .checkOutDate(entity.getCheckOutDate())
                .status(entity.getStatus())
                .specialRequests(entity.getSpecialRequests())
                .rejectionReason(entity.getRejectionReason())
                .totalAmount(entity.getTotalAmount())
                .details(details)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
