package com.novax.leadora.application.usecase.roomrequest;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoomRequestEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.NotificationPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Room-request notifications between Sales and the Reservation team.
 *
 * <p>Writes to the shared {@code notifications} table that
 * {@code GET /api/v1/notifications} already serves; clients deep-link on
 * {@code relatedEntity=ROOM_REQUEST} + {@code relatedId}.
 *
 * <p>Emission is best-effort by design — a notification must never roll back the
 * business transaction that triggered it (same contract as {@code TaskNotifier}).
 * The one exception is {@link #activeReservationStaff()}: with the room gate in front
 * of Send/Convert, having nobody to ask is a hard configuration error, so it fails
 * loudly at request time rather than silently stranding the quotation later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomRequestNotifier {

    /** Role name in the {@code roles} table — see db/reservation_role.sql. */
    public static final String RESERVATION_ROLE = "RESERVATION";

    private static final DateTimeFormatter HOLD_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Every Reservation user who can actually answer. {@code findByRoleName} does not
     * filter on status, so INACTIVE/LOCKED accounts are excluded here — otherwise the
     * "somebody can answer" guard below would pass on disabled staff.
     */
    public List<UserEntity> activeReservationStaff() {
        return userRepository.findByRoleName(RESERVATION_ROLE).stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .toList();
    }

    /**
     * Fails the request when no Reservation user can answer it. Raised at request
     * time on purpose: Send and Convert are gated on a confirmed room, so a silent
     * no-recipient here would leave Sales permanently unable to send the quotation
     * with no explanation. Mirrors {@code SubmitQuotationUseCase}'s
     * {@code NO_MANAGER_AVAILABLE} guard.
     */
    public List<UserEntity> requireReservationStaff() {
        List<UserEntity> staff = activeReservationStaff();
        if (staff.isEmpty()) {
            throw new BusinessException("NO_RESERVATION_STAFF",
                    "No active Reservation staff account exists, so room availability cannot be confirmed. "
                            + "Ask an administrator to create a user with the RESERVATION role.",
                    HttpStatus.CONFLICT);
        }
        return staff;
    }

    /** A new question is waiting in the Reservation inbox. */
    public void requestRaised(RoomRequestEntity request, List<UserEntity> recipients, UserEntity actor) {
        String message = "%s is asking whether %d x \"%s\" is available for %s → %s."
                .formatted(displayName(actor), request.getQuantity(),
                        request.getRoomTypeRequested() != null ? request.getRoomTypeRequested() : "the quoted room",
                        request.getCheckInDate(), request.getCheckOutDate());
        for (UserEntity recipient : recipients) {
            send(recipient, "ROOM_REQUEST_RAISED", "Room Availability Request",
                    message, NotificationPriority.HIGH, request.getRequestId());
        }
    }

    /** Reservation answered — tell the Sales rep who asked. */
    public void requestAnswered(RoomRequestEntity request, UserEntity actor) {
        UserEntity recipient = request.getRequestedBy();
        if (recipient == null) {
            return;
        }
        boolean confirmed = request.getStatus() == com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus.CONFIRMED;
        String title = confirmed ? "Rooms Confirmed" : "Rooms Not Available";
        StringBuilder message = new StringBuilder(confirmed
                ? "%s confirmed %d x \"%s\" for %s → %s."
                        .formatted(displayName(actor), request.getQuantity(),
                                request.getRoomTypeRequested(), request.getCheckInDate(), request.getCheckOutDate())
                : "%s could not confirm %d x \"%s\" for %s → %s."
                        .formatted(displayName(actor), request.getQuantity(),
                                request.getRoomTypeRequested(), request.getCheckInDate(), request.getCheckOutDate()));
        if (confirmed && request.getHeldUntil() != null) {
            message.append(" Held until ").append(HOLD_FORMAT.format(request.getHeldUntil())).append('.');
        }
        if (request.getReservationNote() != null && !request.getReservationNote().isBlank()) {
            message.append(' ').append(request.getReservationNote());
        }
        send(recipient, "ROOM_REQUEST_ANSWERED", title, message.toString(),
                confirmed ? NotificationPriority.NORMAL : NotificationPriority.HIGH, request.getRequestId());
    }

    /**
     * Money landed on a booking whose rooms nobody has confirmed yet — the one case
     * where Reservation must act immediately, since the customer has already paid.
     */
    public void paymentReceivedWithoutRoomConfirmation(BookingEntity booking) {
        List<UserEntity> recipients = activeReservationStaff();
        String message = ("Payment received for booking %s but its rooms are not confirmed yet. "
                + "Confirm availability now — the customer has already paid.")
                .formatted(booking.getBookingCode());
        for (UserEntity recipient : recipients) {
            send(recipient, "ROOM_CONFIRMATION_URGENT", "Paid Booking Awaiting Room Confirmation",
                    message, NotificationPriority.URGENT, booking.getBookingId());
        }
    }

    private void send(UserEntity recipient, String type, String title, String message,
                      NotificationPriority priority, java.util.UUID relatedId) {
        if (recipient == null) {
            return;
        }
        try {
            notificationRepository.save(NotificationEntity.builder()
                    .user(recipient)
                    .title(title)
                    .message(message)
                    .type(type)
                    .priority(priority)
                    .relatedEntity("ROOM_REQUEST")
                    .relatedId(relatedId)
                    .build());
        } catch (Exception e) {
            log.warn("{} notification failed for room request {}: {}", type, relatedId, e.getMessage());
        }
    }

    private static String displayName(UserEntity user) {
        return user != null && user.getFullName() != null ? user.getFullName() : "A teammate";
    }
}
