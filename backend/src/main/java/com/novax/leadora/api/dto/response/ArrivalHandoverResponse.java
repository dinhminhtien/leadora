package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private LocalDate checkInDate;   // arrival date
    private LocalDate checkOutDate;

    // Room / service information
    private String roomSummary;              // compact line for the list view
    private List<RoomLine> rooms;            // full breakdown for the detail view

    // Handover content (read-only for FO)
    private String specialRequests;
    private String roomPreferences;
    private String vipNotes;
    private String operationalNotes;

    // Payment / deposit status reference (UC-22.2)
    private String paymentReference;

    // Lifecycle
    private String status;            // HandoverStatus: SUBMITTED | ACKNOWLEDGED | READY
    private String readinessStatus;   // ReadinessStatus: PENDING_REVIEW | REVIEWED | READY_FOR_ARRIVAL | NEED_CLARIFICATION
    private String clarificationNote;
    private OffsetDateTime submittedAt;
    private OffsetDateTime acknowledgedAt;
    private String updatedByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** One allocated room/service line of the booking. */
    @Getter
    @Builder
    public static class RoomLine {
        private String productName;
        private String roomNumber;
        private Integer quantity;
        private Integer nights;
        private String inventoryStatus;
    }

    /** Base fields only (no room/payment context). */
    public static ArrivalHandoverResponse from(OpHandoverEntity h) {
        return baseBuilder(h).build();
    }

    /** List row: base + a compact room/service summary. */
    public static ArrivalHandoverResponse fromList(OpHandoverEntity h, List<BookingDetailEntity> details) {
        return baseBuilder(h)
                .roomSummary(buildRoomSummary(details))
                .build();
    }

    /** Detail: base + full room breakdown + payment/deposit reference. */
    public static ArrivalHandoverResponse fromDetail(OpHandoverEntity h,
                                                     List<BookingDetailEntity> details,
                                                     List<PaymentEntity> payments) {
        return baseBuilder(h)
                .roomSummary(buildRoomSummary(details))
                .rooms(buildRooms(details))
                .paymentReference(buildPaymentReference(payments))
                .build();
    }

    private static ArrivalHandoverResponseBuilder baseBuilder(OpHandoverEntity h) {
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
                .clarificationNote(h.getClarificationNote())
                .submittedAt(h.getSubmittedAt())
                .acknowledgedAt(h.getAcknowledgedAt())
                .updatedByName(updatedBy != null ? updatedBy.getFullName() : null)
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt());
    }

    private static List<RoomLine> buildRooms(List<BookingDetailEntity> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        return details.stream()
                .map(d -> RoomLine.builder()
                        .productName(d.getProductService() != null ? d.getProductService().getName() : null)
                        .roomNumber(d.getRoomNumber())
                        .quantity(d.getQuantity())
                        .nights(d.getNights())
                        .inventoryStatus(d.getInventoryStatus() != null ? d.getInventoryStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private static String buildRoomSummary(List<BookingDetailEntity> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        return details.stream()
                .map(d -> {
                    String name = d.getProductService() != null ? d.getProductService().getName() : "Service";
                    return d.getQuantity() != null && d.getQuantity() > 1 ? name + " x" + d.getQuantity() : name;
                })
                .collect(Collectors.joining(", "));
    }

    private static String buildPaymentReference(List<PaymentEntity> payments) {
        if (payments == null || payments.isEmpty()) {
            return "No payment yet";
        }
        PaymentEntity deposit = payments.stream()
                .filter(p -> p.getPaymentType() == PaymentType.DEPOSIT)
                .findFirst()
                .orElse(null);
        if (deposit != null && deposit.getStatus() != null) {
            return "Deposit: " + deposit.getStatus().name();
        }
        PaymentEntity latest = payments.get(payments.size() - 1);
        return latest.getStatus() != null ? "Payment: " + latest.getStatus().name() : "No payment yet";
    }
}
