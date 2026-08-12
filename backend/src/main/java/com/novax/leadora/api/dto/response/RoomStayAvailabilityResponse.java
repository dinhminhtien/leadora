package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.application.usecase.inventory.StayAvailability;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One room type's answer for a specific stay — the shape a sales rep actually asks in.
 *
 * <p>{@code limitingDates} is the part that keeps a conversation alive: "Deluxe is full" ends an
 * enquiry, while "full on the 11th, open either side" gives the rep an alternative to offer.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomStayAvailabilityResponse {

    private UUID productId;
    private String roomType;
    private BigDecimal unitPrice;

    /** Minimum across the stay's nights; null when any night is unpublished. */
    private Integer availableForStay;

    /** Whether the requested number of rooms can be taken without asking Reservation. */
    private boolean bookableNow;

    /** The nights that set the minimum — where the stay is actually constrained. */
    private List<LocalDate> limitingDates;

    private List<LocalDate> closedDates;
    private List<LocalDate> unpublishedDates;

    private boolean stale;
    private OffsetDateTime asOf;

    public static RoomStayAvailabilityResponse of(
            ProductServiceEntity product, StayAvailability stay, int requestedQuantity) {
        return RoomStayAvailabilityResponse.builder()
                .productId(product.getProductId())
                .roomType(product.getName())
                .unitPrice(product.getUnitPrice())
                .availableForStay(stay.availableForStay())
                .bookableNow(stay.canCover(requestedQuantity))
                .limitingDates(stay.limitingDates())
                .closedDates(stay.closedDates())
                .unpublishedDates(stay.unpublishedDates())
                .stale(stay.stale())
                .asOf(stay.oldestAsOf())
                .build();
    }
}
