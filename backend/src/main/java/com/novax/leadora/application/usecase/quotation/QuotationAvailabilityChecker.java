package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.application.usecase.inventory.RoomAvailabilityAssessment;
import com.novax.leadora.application.usecase.inventory.RoomAvailabilityService;
import com.novax.leadora.application.usecase.inventory.RoomAvailabilityVerdict;
import com.novax.leadora.application.usecase.inventory.RoomLineDemand;
import com.novax.leadora.application.usecase.inventory.StayAvailability;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decides whether a quotation's rooms may be promised — the policy layer over
 * {@link RoomAvailabilityService}'s arithmetic. Used by Create, Revise and Convert
 * (BR-24, UC-14.1/14.5/14.7).
 *
 * <p><b>This class used to be a gate in name only.</b> It looked the room type up by name and
 * threw if no active product matched — then let anything through, never once reading the
 * requested quantity. Its own javadoc called it an availability gate, which is the dangerous
 * part: later readers took it on trust that overselling was already prevented.
 *
 * <p>It now returns a {@link RoomAvailabilityVerdict} instead of merely throwing, because the
 * honest answer is often "we cannot tell":
 *
 * <ul>
 *   <li>{@code BLOCKED} still throws. The room type does not exist, is not an active room, or the
 *       hotel has closed the date — no confirmation would change any of those.</li>
 *   <li>{@code NEEDS_CONFIRMATION} does <b>not</b> throw. Quota is short, unpublished, or stale.
 *       A quotation is an offer, and the hotel may hold rooms outside our allocation, so blocking
 *       here would lose business the hotel could service. Callers let the quotation through and
 *       route it to the Reservation team; the hard stop is at conversion, where the CRM stops
 *       offering and starts committing.</li>
 *   <li>{@code OK} proceeds untouched.</li>
 * </ul>
 *
 * <p>Refusing to quote beyond the published horizon would be the worst failure mode of all: the
 * Reservation team publishes quota some weeks out, so a strict gate would make every enquiry for
 * a later date unquotable.
 */
@Component
@RequiredArgsConstructor
public class QuotationAvailabilityChecker {

    private final ProductServiceRepository productServiceRepository;
    private final RoomAvailabilityService roomAvailabilityService;

    /**
     * Assesses every room line of a stay.
     *
     * @param excludeQuotationId a quotation whose own live hold should not count against it —
     *                           pass the quotation being revised or converted, otherwise it
     *                           competes with the rooms it is already holding
     * @throws BusinessException when any line is {@link RoomAvailabilityVerdict#BLOCKED}
     */
    public RoomAvailabilityAssessment assess(
            LocalDate checkInDate,
            LocalDate checkOutDate,
            List<RoomLineDemand> demands,
            UUID excludeQuotationId) {

        if (demands == null || demands.isEmpty()) {
            throw new BusinessException("ROOM_LINES_REQUIRED",
                    "A quotation must contain at least one room line.", HttpStatus.BAD_REQUEST);
        }
        if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new BusinessException("INVALID_DATES",
                    "Check-out date must be after check-in date.", HttpStatus.BAD_REQUEST);
        }

        Map<UUID, ProductServiceEntity> products = resolveSellableRooms(demands);

        Map<UUID, StayAvailability> stays = roomAvailabilityService.stays(
                products.values(), checkInDate, checkOutDate, excludeQuotationId);

        List<RoomAvailabilityAssessment.LineAssessment> lines = new ArrayList<>();
        RoomAvailabilityVerdict overall = RoomAvailabilityVerdict.OK;

        for (RoomLineDemand demand : demands) {
            ProductServiceEntity product = products.get(demand.productId());
            StayAvailability stay = stays.get(demand.productId());
            RoomAvailabilityVerdict verdict = verdictFor(stay, demand.quantity());

            lines.add(new RoomAvailabilityAssessment.LineAssessment(
                    demand.productId(), product.getName(), demand.quantity(), verdict, stay));
            overall = overall.andThen(verdict);
        }

        if (overall == RoomAvailabilityVerdict.BLOCKED) {
            throw new BusinessException("ROOM_DATE_CLOSED",
                    "The hotel has closed these dates for " + blockedNames(lines)
                            + ". Please choose different dates or another room type.",
                    HttpStatus.CONFLICT);
        }

        return new RoomAvailabilityAssessment(overall, products, List.copyOf(lines));
    }

    /**
     * The hard stop before the CRM commits a room to a guest (UC-14.7).
     *
     * <p>Separate from {@link #assess} so the difference between offering and committing stays
     * explicit at the call sites rather than hidden behind a flag.
     */
    public RoomAvailabilityAssessment assertCanCommit(
            LocalDate checkInDate,
            LocalDate checkOutDate,
            List<RoomLineDemand> demands,
            UUID excludeQuotationId,
            boolean roomsConfirmedByReservation) {

        RoomAvailabilityAssessment assessment =
                assess(checkInDate, checkOutDate, demands, excludeQuotationId);

        if (assessment.needsConfirmation() && !roomsConfirmedByReservation) {
            throw new BusinessException("ROOM_NOT_CONFIRMED",
                    "There is not enough confirmed allotment for " + unconfirmedNames(assessment)
                            + ". The Reservation team must confirm these rooms before the booking"
                            + " can be created.",
                    HttpStatus.CONFLICT);
        }
        return assessment;
    }

    /**
     * Loads the products behind the demands, rejecting anything that is not a room this CRM may
     * sell. Preserves the previous behaviour exactly — the old name-matching path only ever
     * searched active {@code ROOM} products, so anything else already failed, just with a message
     * that blamed the room type's spelling.
     */
    private Map<UUID, ProductServiceEntity> resolveSellableRooms(List<RoomLineDemand> demands) {
        if (demands.stream().anyMatch(demand -> demand.productId() == null)) {
            throw new BusinessException("INVALID_ROOM_TYPE",
                    "Every room line must name a room type.", HttpStatus.BAD_REQUEST);
        }
        List<UUID> ids = demands.stream().map(RoomLineDemand::productId).distinct().toList();

        Map<UUID, ProductServiceEntity> byId = new LinkedHashMap<>();
        for (ProductServiceEntity product : productServiceRepository.findAllById(ids)) {
            byId.put(product.getProductId(), product);
        }

        for (UUID id : ids) {
            ProductServiceEntity product = byId.get(id);
            if (product == null) {
                throw new BusinessException("INVALID_ROOM_TYPE",
                        "Room type " + id + " no longer exists.", HttpStatus.BAD_REQUEST);
            }
            if (product.getCategory() != ProductCategory.ROOM || product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException("INVALID_ROOM_TYPE",
                        "\"" + product.getName() + "\" is not a room type currently on sale.",
                        HttpStatus.BAD_REQUEST);
            }
        }
        return byId;
    }

    /** Closed dates are final; everything else short of covered is a question for Reservation. */
    private static RoomAvailabilityVerdict verdictFor(StayAvailability stay, int requested) {
        if (stay == null) {
            return RoomAvailabilityVerdict.NEEDS_CONFIRMATION;
        }
        if (!stay.closedDates().isEmpty()) {
            return RoomAvailabilityVerdict.BLOCKED;
        }
        return stay.canCover(requested)
                ? RoomAvailabilityVerdict.OK
                : RoomAvailabilityVerdict.NEEDS_CONFIRMATION;
    }

    private static String blockedNames(List<RoomAvailabilityAssessment.LineAssessment> lines) {
        return lines.stream()
                .filter(line -> line.verdict() == RoomAvailabilityVerdict.BLOCKED)
                .map(RoomAvailabilityAssessment.LineAssessment::roomTypeName)
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("the selected room type");
    }

    private static String unconfirmedNames(RoomAvailabilityAssessment assessment) {
        return assessment.unconfirmedLines().stream()
                .map(RoomAvailabilityAssessment.LineAssessment::roomTypeName)
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("the selected room type");
    }
}
