package com.novax.leadora.application.usecase.roomrequest;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoomRequestEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import com.novax.leadora.infrastructure.persistence.repository.RoomRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads whether a quotation's rooms have been confirmed by the Reservation team.
 *
 * <p>This CRM owns no room inventory, so "is the room available" is never computed here — it
 * is answered by the Reservation team against the hotel's real PMS and recorded as a
 * {@link RoomRequestEntity}. This class only reads that answer back.
 *
 * <p><b>Advisory, not a gate.</b> Room confirmation is one condition attached to a quotation,
 * not a precondition for working it: sending, revising and converting a quotation all proceed
 * whether or not the rooms are confirmed. Callers use this to <em>inform</em> — surface the
 * state, notify the team — never to refuse an operation.
 */
@Component
@RequiredArgsConstructor
public class RoomConfirmationReader {

    private final RoomRequestRepository roomRequestRepository;

    /** The request that currently speaks for this quotation, if any. */
    public Optional<RoomRequestEntity> currentRequest(UUID quotationId) {
        return roomRequestRepository
                .findFirstByQuotation_QuotationIdAndStatusNotOrderByCreatedAtDesc(
                        quotationId, RoomRequestStatus.SUPERSEDED);
    }

    /**
     * True when the Reservation team has confirmed rooms that still describe this quotation:
     * same room type and dates, and any hold they committed to has not lapsed.
     */
    public boolean isRoomConfirmed(QuotationEntity quotation) {
        if (quotation == null) {
            return false;
        }
        return currentRequest(quotation.getQuotationId())
                .filter(r -> r.getStatus() == RoomRequestStatus.CONFIRMED)
                .filter(r -> matches(r, quotation.getRoomType(),
                        quotation.getCheckInDate(), quotation.getCheckOutDate()))
                .filter(r -> !holdExpired(r))
                .isPresent();
    }

    /**
     * True when the confirmed answer still describes the room type and dates being relied on.
     * Quantity is excluded on purpose: it is not stored on the quotation, so it cannot drift
     * independently of the request that captured it.
     */
    private static boolean matches(RoomRequestEntity request, String roomType,
                                   LocalDate checkInDate, LocalDate checkOutDate) {
        return Objects.equals(request.getCheckInDate(), checkInDate)
                && Objects.equals(request.getCheckOutDate(), checkOutDate)
                && sameRoomType(request.getRoomTypeRequested(), roomType);
    }

    private static boolean sameRoomType(String requested, String quoted) {
        if (requested == null || quoted == null) {
            return requested == null && quoted == null;
        }
        return requested.trim().equalsIgnoreCase(quoted.trim());
    }

    /** A null heldUntil means "confirmed with no deadline given" — never expires. */
    private static boolean holdExpired(RoomRequestEntity request) {
        return request.getHeldUntil() != null && request.getHeldUntil().isBefore(OffsetDateTime.now());
    }
}
