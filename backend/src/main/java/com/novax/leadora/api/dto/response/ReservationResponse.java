package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReservationResponse {

    private UUID id;
    private String guestName;
    private String reservationNo;
    private String roomType;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private String specialRequests;
    private String rejectionReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<BookingDetailResponse> details;

    public static ReservationResponse from(BookingEntity entity) {
        if (entity == null) return null;
        return ReservationResponse.builder()
                .id(entity.getBookingId())
                .guestName(entity.getCustomer() != null ? entity.getCustomer().getFullName() : null)
                .reservationNo(entity.getBookingCode())
                .roomType("N/A")
                .checkInDate(entity.getCheckInDate())
                .checkOutDate(entity.getCheckOutDate())
                .totalAmount(entity.getTotalAmount())
                .status(entity.getStatus())
                .specialRequests(entity.getSpecialRequests())
                .rejectionReason(entity.getRejectionReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static ReservationResponse from(BookingEntity entity, List<BookingDetailEntity> detailsList) {
        if (entity == null) return null;
        
        String resolvedRoomType = "N/A";
        if (detailsList != null && !detailsList.isEmpty()) {
            resolvedRoomType = detailsList.stream()
                    .filter(d -> d.getProductService() != null)
                    .map(d -> d.getProductService().getName())
                    .distinct()
                    .collect(Collectors.joining(", "));
        }

        List<BookingDetailResponse> detailResponses = null;
        if (detailsList != null) {
            detailResponses = detailsList.stream()
                    .map(BookingDetailResponse::from)
                    .collect(Collectors.toList());
        }

        return ReservationResponse.builder()
                .id(entity.getBookingId())
                .guestName(entity.getCustomer() != null ? entity.getCustomer().getFullName() : null)
                .reservationNo(entity.getBookingCode())
                .roomType(resolvedRoomType)
                .checkInDate(entity.getCheckInDate())
                .checkOutDate(entity.getCheckOutDate())
                .totalAmount(entity.getTotalAmount())
                .status(entity.getStatus())
                .specialRequests(entity.getSpecialRequests())
                .rejectionReason(entity.getRejectionReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .details(detailResponses)
                .build();
    }
}
