package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * The Reservation team's answer to a room-availability request, after checking the
 * hotel's real PMS.
 *
 * <p>{@link #heldUntil} is a commitment the CRM records verbatim — the room itself is
 * held in the PMS, not here. A {@code REJECTED} decision requires
 * {@link #reservationNote} so Sales can tell the customer why (and offer alternatives).
 */
@Getter
@Setter
public class RespondRoomRequestRequest {

    /** Only {@code CONFIRMED} or {@code REJECTED} are accepted. */
    @NotNull(message = "Decision is required")
    private RoomRequestStatus decision;

    private String reservationNote;

    /** Only meaningful for {@code CONFIRMED}; ignored otherwise. */
    private OffsetDateTime heldUntil;
}
