package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * The Reservation team publishing how many rooms the hotel has released to us.
 *
 * <p>Entered as a range and stored one row per night. Ranges are what a person works in — "ten
 * Deluxe for the first half of August" — while nights are what the system can edit and deduct
 * from without merging or splitting anything.
 */
@Data
public class UpsertRoomAllotmentRequest {

    @NotNull(message = "Room type is required")
    private UUID productId;

    @NotNull(message = "Start date is required")
    private LocalDate dateFrom;

    /** Inclusive — the last night covered, not a check-out date. */
    @NotNull(message = "End date is required")
    private LocalDate dateTo;

    @NotNull(message = "Allotted quantity is required")
    @Min(value = 0, message = "Allotted quantity cannot be negative")
    private Integer allottedQty;

    /**
     * Stop-sell rather than sold out. Zero quota is worth a room request — the hotel may release
     * more; a closed date is not.
     */
    private Boolean closed;

    /**
     * When the hotel's figures were true, if that is not now. Keying in a morning report at
     * midday should not make the numbers look freshly confirmed — the staleness warning is only
     * as honest as this field.
     */
    private OffsetDateTime asOf;

    private String note;

    /**
     * Restricts the range to certain days, e.g. weekdays only. Values are {@code MONDAY} …
     * {@code SUNDAY}; empty or omitted means every day.
     *
     * <p>Present because weekend quota is almost never the same as midweek quota. Without it the
     * team would publish the same range twice with different days picked out by hand — the sort
     * of re-keying this feature exists to remove.
     */
    private Set<DayOfWeek> weekdays;
}
