package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One room type on a quotation.
 *
 * <p>{@code productId} is the identity; {@code roomType} is only a label. It used to be the other
 * way round — the line carried a free-text room type that was matched against
 * {@code product_services.name} with {@code equalsIgnoreCase}, and {@code product_id} was left
 * null on every quotation detail ever written. That made the link fragile in both directions: a
 * typed-in description could never match, and renaming a product silently detached every
 * quotation referencing it, with no foreign key to complain.
 *
 * <p>Room allotment is keyed on the product, so a name-based link cannot support it: the system
 * would deduct quota from whichever product happened to share a spelling.
 */
@Data
public class RoomLineRequest {

    @NotNull(message = "Room type is required")
    private UUID productId;

    /**
     * Display label only, and optional — the server overwrites it with the product's current
     * name so the stored text cannot disagree with the product it points at.
     */
    private String roomType;

    @NotNull(message = "Number of rooms is required")
    @Min(value = 1, message = "Number of rooms must be at least 1")
    private Integer numberOfRooms;

    @NotNull(message = "Price per night is required")
    @DecimalMin(value = "0.0", message = "Price per night must be non-negative")
    private BigDecimal pricePerNight;
}
