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
 * Resolves a quotation's room lines and reports how they look against the figures the Reservation
 * team has published. <b>Advisory only.</b>
 *
 * <p>Report 1 draws the boundary in FE-19 and LI-02: Leadora "does not calculate, allocate, hold,
 * lock, or modify room inventory", and Sales "cannot independently determine" availability. So
 * this class answers "what do the synchronized figures say?" — never "may this proceed?". The
 * only authority on whether rooms exist is the Reservation team's recorded answer to a room
 * request, read by {@code RoomConfirmationReader}.
 *
 * <p>It used to own an {@code assertCanCommit} that refused a booking conversion whenever
 * Leadora's own arithmetic came up short. That was this system determining availability and
 * blocking the sales workflow on the result, so it is gone; the conversion gate is Reservation's
 * confirmation, in {@code QuotationActionPolicy}.
 *
 * <p>What remains that <em>can</em> refuse is deliberately not an availability judgement:
 *
 * <ul>
 *   <li>A room type that does not exist, or is not an active room this CRM may sell, cannot be
 *       quoted — there is nothing to quote.</li>
 *   <li>A date the Reservation team has explicitly <b>closed</b> is their decision, already made
 *       and published. Honouring it is not the same as making one.</li>
 * </ul>
 *
 * <p>Everything else — quota short, unpublished, stale — is {@code NEEDS_CONFIRMATION}, which
 * never blocks. A quotation is an offer, the hotel may hold rooms outside our allocation, and
 * quota is published only some weeks out, so refusing on these would make most forward enquiries
 * unquotable.
 */
@Component
@RequiredArgsConstructor
public class QuotationAvailabilityChecker {

    private final ProductServiceRepository productServiceRepository;
    private final RoomAvailabilityService roomAvailabilityService;

    /**
     * Resolves the room lines and assesses every one of them against published quota.
     *
     * @throws BusinessException only when a line names something that is not a sellable room, or
     *                           when the Reservation team has closed one of the dates
     */
    public RoomAvailabilityAssessment assess(
            LocalDate checkInDate,
            LocalDate checkOutDate,
            List<RoomLineDemand> demands) {

        if (demands == null || demands.isEmpty()) {
            throw new BusinessException("ROOM_LINES_REQUIRED",
                    "A quotation must contain at least one room line.", HttpStatus.BAD_REQUEST);
        }
        if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new BusinessException("INVALID_DATES",
                    "Check-out date must be after check-in date.", HttpStatus.BAD_REQUEST);
        }

        Map<UUID, ProductServiceEntity> products = resolveSellableRooms(demands);

        Map<UUID, StayAvailability> stays =
                roomAvailabilityService.stays(products.values(), checkInDate, checkOutDate);

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
                    "The Reservation team has closed these dates for " + blockedNames(lines)
                            + ". Please choose different dates or another room type.",
                    HttpStatus.CONFLICT);
        }

        return new RoomAvailabilityAssessment(overall, products, List.copyOf(lines));
    }

    /** Loads the products behind the demands, rejecting anything that is not a room we may sell. */
    private Map<UUID, ProductServiceEntity> resolveSellableRooms(List<RoomLineDemand> demands) {
        if (demands.stream().anyMatch(demand -> demand.productId() == null)) {
            throw new BusinessException("INVALID_ROOM_TYPE",
                    "Every room line must name a room type.", HttpStatus.BAD_REQUEST);
        }
        List<UUID> ids = demands.stream().map(d -> d.productId()).distinct().toList();

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

    /** Closed dates are Reservation's own decision; everything else is a question for them. */
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
                .map(l -> l.roomTypeName())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("the selected room type");
    }
}
