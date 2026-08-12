package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.application.usecase.inventory.NightAvailability;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The allotment grid: one row per room type, one cell per night.
 *
 * <p>Every cell carries {@code allotted}, {@code booked} and {@code held} alongside the answer,
 * not just the answer. "Five left" prompts a different action depending on whether it is five of
 * five or five of twenty — the first is worth asking the hotel to extend, the second is not — and
 * a lone availability figure cannot tell a rep which situation they are in.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomAvailabilityGridResponse {

    private LocalDate from;
    /** Inclusive last night shown. */
    private LocalDate to;
    private List<RoomRow> rooms;

    @Getter
    @Builder
    public static class RoomRow {
        private UUID productId;
        private String roomType;
        private BigDecimal unitPrice;
        private String unit;
        private List<Night> days;

        public static RoomRow of(ProductServiceEntity product, List<NightAvailability> nights) {
            return RoomRow.builder()
                    .productId(product.getProductId())
                    .roomType(product.getName())
                    .unitPrice(product.getUnitPrice())
                    .unit(product.getUnit())
                    .days(nights.stream().map(Night::of).toList())
                    .build();
        }
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Night {
        private LocalDate date;
        /** Null when the Reservation team has not published this night — never zero (BR-48). */
        private Integer allotted;
        private int booked;
        private int held;
        /** Null when unpublished; the client must render this as "—", not as "sold out". */
        private Integer available;
        private boolean closed;
        /** {@code PUBLISHED} | {@code NOT_PUBLISHED} | {@code CLOSED} — saves clients inferring it. */
        private String status;
        private OffsetDateTime asOf;
        private boolean stale;

        public static Night of(NightAvailability night) {
            return Night.builder()
                    .date(night.date())
                    .allotted(night.allotted())
                    .booked(night.booked())
                    .held(night.held())
                    .available(night.available())
                    .closed(night.closed())
                    .status(night.closed() ? "CLOSED" : night.published() ? "PUBLISHED" : "NOT_PUBLISHED")
                    .asOf(night.asOf())
                    .stale(night.stale())
                    .build();
        }
    }
}
