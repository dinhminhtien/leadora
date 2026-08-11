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
    /**
     * BookingStatus (UC-22.1 step 3, BR-44).
     *
     * <p>This used to be justified by "a detail view can still be reached by a deep link from an
     * older notification". That is not true: a HANDOVER notification routes to
     * {@code /front-office-handover?highlight=<id>}, and the highlight only rings a row that is
     * already in the list — the drawer opens on a row click alone. A handover the list filters out
     * is therefore unreachable from the UI, and the arrival endpoints refuse it anyway.
     *
     * <p>What the field is actually for, on each side:
     * <ul>
     *   <li><b>Operational screens</b> — {@code forOperations} does not filter on booking status, so
     *       Sales and Reservation legitimately see handovers whose booking has been cancelled or
     *       checked out, and need this to tell them apart.</li>
     *   <li><b>Arrival screens</b> — always CONFIRMED or CHECKED_IN on a fresh read, because
     *       {@code GetArrivalHandoverDetailUseCase} 404s anything else. It still matters for the
     *       cached copy: React Query keeps the last successful payload when a refetch fails, so the
     *       drawer can be showing a booking that has since died, and {@code isBookingActive} reads
     *       this field to keep the readiness form disabled rather than let the user submit into a
     *       422.</li>
     * </ul>
     */
    private String bookingStatus;

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

    // Assignment (UC-22.1 step 4 — the list must show the responsible Front Office Staff)
    private UUID assignedFoUserId;
    /** Resolved separately: {@code assigned_fo_user_id} is a scalar column, not a JPA relation. */
    private String assignedFoName;

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
        return fromList(h, details, null);
    }

    /** List row for the Front Office desk, which also names the responsible FO staff. */
    public static ArrivalHandoverResponse fromList(OpHandoverEntity h, List<BookingDetailEntity> details,
                                                   String assignedFoName) {
        return baseBuilder(h)
                .roomSummary(buildRoomSummary(details))
                .assignedFoName(assignedFoName)
                .build();
    }

    /** Detail: base + full room breakdown + payment/deposit reference. */
    public static ArrivalHandoverResponse fromDetail(OpHandoverEntity h,
                                                     List<BookingDetailEntity> details,
                                                     List<PaymentEntity> payments) {
        return fromDetail(h, details, payments, null);
    }

    /** Detail for the Front Office desk, which also names the responsible FO staff. */
    public static ArrivalHandoverResponse fromDetail(OpHandoverEntity h,
                                                     List<BookingDetailEntity> details,
                                                     List<PaymentEntity> payments,
                                                     String assignedFoName) {
        return baseBuilder(h)
                .roomSummary(buildRoomSummary(details))
                .rooms(buildRooms(details))
                .paymentReference(buildPaymentReference(payments))
                .assignedFoName(assignedFoName)
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
                .bookingStatus(booking != null && booking.getStatus() != null
                        ? booking.getStatus().name() : null)
                .assignedFoUserId(h.getAssignedFoUserId())
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
