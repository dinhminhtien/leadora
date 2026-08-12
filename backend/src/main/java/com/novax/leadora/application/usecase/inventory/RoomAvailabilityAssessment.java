package com.novax.leadora.application.usecase.inventory;

import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The verdict on a whole quotation's worth of room lines, plus the detail behind it.
 *
 * <p>Callers need more than a yes/no. The quotation use cases need the resolved
 * {@link ProductServiceEntity} per line so they can write {@code quotation_details.product_id}
 * without looking it up a second time; the auto-raised room request needs the shortfall figures
 * to tell the Reservation team what is actually being asked for.
 */
public record RoomAvailabilityAssessment(

        /** Worst verdict across the lines — a quotation is only as sellable as its weakest line. */
        RoomAvailabilityVerdict verdict,

        /** Resolved products, keyed by id, so callers need not re-query. */
        Map<UUID, ProductServiceEntity> products,

        List<LineAssessment> lines) {

    public record LineAssessment(
            UUID productId,
            String roomTypeName,
            int requested,
            RoomAvailabilityVerdict verdict,
            StayAvailability availability) {

        /** How many rooms short this line is, or zero when it is covered or unknowable. */
        public int shortfall() {
            Integer available = availability.availableForStay();
            return available == null ? 0 : Math.max(0, requested - available);
        }
    }

    public boolean needsConfirmation() {
        return verdict == RoomAvailabilityVerdict.NEEDS_CONFIRMATION;
    }

    /** Only the lines that could not be covered — what a room request should ask about. */
    public List<LineAssessment> unconfirmedLines() {
        return lines.stream()
                .filter(line -> line.verdict() != RoomAvailabilityVerdict.OK)
                .toList();
    }
}
