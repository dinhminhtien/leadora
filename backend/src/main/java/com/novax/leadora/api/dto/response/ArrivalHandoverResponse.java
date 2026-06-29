package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Arrival handover as seen by Front Office (UC-22.1 / UC-22.2): the operational handover
 * created by Sales/Reservation after a booking is confirmed, plus the guest/booking context
 * the front desk needs to prepare for arrival.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArrivalHandoverResponse {

    private UUID handoverId;

    // Booking / guest context
    private UUID bookingId;
    private String bookingCode;
    private String customerName;
    private String customerPhone;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;

    // Handover content (read-only for FO)
    private String specialRequests;
    private String roomPreferences;
    private String vipNotes;
    private String operationalNotes;

    // Lifecycle
    private String status;            // HandoverStatus: SUBMITTED | ACKNOWLEDGED | READY
    private String readinessStatus;   // ReadinessStatus: PENDING | IN_PROGRESS | READY
    private OffsetDateTime submittedAt;
    private OffsetDateTime acknowledgedAt;
    private String updatedByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ArrivalHandoverResponse from(OpHandoverEntity h) {
        BookingEntity booking = h.getBooking();
        CustomerEntity customer = booking != null ? booking.getCustomer() : null;
        UserEntity updatedBy = h.getUpdatedBy();

        return ArrivalHandoverResponse.builder()
                .handoverId(h.getHandoverId())
                .bookingId(booking != null ? booking.getBookingId() : null)
                .bookingCode(booking != null ? booking.getBookingCode() : null)
                .customerName(customer != null ? customer.getFullName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .checkInDate(booking != null ? booking.getCheckInDate() : null)
                .checkOutDate(booking != null ? booking.getCheckOutDate() : null)
                .specialRequests(h.getSpecialRequests())
                .roomPreferences(h.getRoomPreferences())
                .vipNotes(h.getVipNotes())
                .operationalNotes(h.getOperationalNotes())
                .status(h.getStatus() != null ? h.getStatus().name() : null)
                .readinessStatus(h.getReadinessStatus() != null ? h.getReadinessStatus().name() : null)
                .submittedAt(h.getSubmittedAt())
                .acknowledgedAt(h.getAcknowledgedAt())
                .updatedByName(updatedBy != null ? updatedBy.getFullName() : null)
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
