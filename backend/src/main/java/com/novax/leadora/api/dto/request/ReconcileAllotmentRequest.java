package com.novax.leadora.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The Reservation team's daily reconciliation against the hotel's real system.
 *
 * <p>Carries what they can actually <b>see</b> — how many rooms are still free — rather than the
 * allotment total, which the PMS never states. Asking them to work the total out by hand would
 * make every entry an opportunity for arithmetic to go wrong; the server does the subtraction.
 */
@Data
public class ReconcileAllotmentRequest {

    @NotEmpty(message = "At least one night must be reconciled")
    @Valid
    private List<Entry> entries;

    /**
     * When the hotel's figures were true. A morning report keyed in at midday must not make the
     * numbers look freshly confirmed — the staleness warning is only worth having if this is
     * honest.
     */
    private OffsetDateTime asOf;

    @Data
    public static class Entry {

        @NotNull(message = "Room type is required")
        private UUID productId;

        @NotNull(message = "Night is required")
        private LocalDate stayDate;

        /** Rooms the hotel's system shows as still free to us on this night. */
        @NotNull(message = "Observed availability is required")
        @Min(value = 0, message = "Observed availability cannot be negative")
        private Integer actualAvailable;
    }
}
