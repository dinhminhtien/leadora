package com.novax.leadora.application.usecase.inventory;

import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoomAllotmentHoldEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HoldStatus;
import com.novax.leadora.infrastructure.persistence.repository.RoomAllotmentHoldRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoomAllotmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Places and releases the soft holds that stop two sales reps selling the same last room.
 *
 * <p>Reading availability and then writing a hold is a check-then-act, and an {@code @Version}
 * column cannot make it safe: the two transactions write to <em>different</em> rows, so nothing
 * collides and both commit happily having each read "1 left". The nights being sold are therefore
 * locked with {@code SELECT … FOR UPDATE} before availability is re-read inside the lock — see
 * {@link RoomAllotmentRepository#lockNightsForUpdate}, whose ordering also keeps overlapping
 * stays from deadlocking each other.
 *
 * <p>The first availability check, the one that produced the verdict, is deliberately not
 * trusted here: time passes between assessing a quotation and saving it. If the rooms are gone by
 * the time the lock is taken, this reports failure and the caller degrades the quotation to
 * "needs confirmation" rather than holding stock it no longer has.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomAllotmentHoldService {

    private final RoomAllotmentRepository allotmentRepository;
    private final RoomAllotmentHoldRepository holdRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final CurrentUserProvider currentUserProvider;

    /** Fallback hold length when a quotation carries no validity date. */
    @Value("${leadora.room-allotment.hold-days:7}")
    private long defaultHoldDays;

    /**
     * Holds every line of a quotation, all or nothing.
     *
     * <p>All or nothing because a partial hold is the worst outcome available: the quotation
     * would show as covered while silently holding stock for only some of its rooms, and the
     * shortfall would surface at conversion, after the customer had been quoted.
     *
     * @return {@code true} when every line was held
     */
    @Transactional
    public boolean holdForQuotation(
            QuotationEntity quotation,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            List<RoomLineDemand> demands,
            Map<UUID, ProductServiceEntity> products) {

        if (demands == null || demands.isEmpty()) {
            return false;
        }
        List<UUID> productIds = demands.stream().map(RoomLineDemand::productId).distinct().toList();

        // Lock first, then look — the whole point is that nothing may change between the two.
        allotmentRepository.lockNightsForUpdate(productIds, checkInDate, checkOutDate);

        Map<UUID, StayAvailability> stays = roomAvailabilityService.stays(
                products.values(), checkInDate, checkOutDate, quotation.getQuotationId());

        for (RoomLineDemand demand : demands) {
            StayAvailability stay = stays.get(demand.productId());
            if (stay == null || !stay.canCover(demand.quantity())) {
                log.info("Allotment hold declined for quotation {}: {} no longer covers {} room(s)",
                        quotation.getQuotationId(), demand.productId(), demand.quantity());
                return false;
            }
        }

        // A revision supersedes its own earlier hold; leaving it would double-count the rooms
        // against the very quotation that is holding them.
        releaseForQuotation(quotation.getQuotationId());

        OffsetDateTime expiresAt = expiryFor(quotation);
        UserEntity actor = resolveActorQuietly();

        List<RoomAllotmentHoldEntity> holds = new ArrayList<>();
        for (RoomLineDemand demand : demands) {
            holds.add(RoomAllotmentHoldEntity.builder()
                    .product(products.get(demand.productId()))
                    .checkInDate(checkInDate)
                    .checkOutDate(checkOutDate)
                    .quantity(demand.quantity())
                    .quotation(quotation)
                    .status(HoldStatus.ACTIVE)
                    .expiresAt(expiresAt)
                    .createdBy(actor)
                    .build());
        }
        holdRepository.saveAll(holds);
        return true;
    }

    /** Gives the rooms back — the quotation was closed, rejected, expired or revised away. */
    @Transactional
    public void releaseForQuotation(UUID quotationId) {
        stamp(quotationId, HoldStatus.RELEASED, null);
    }

    /**
     * Hands the deduction over to the booking, in the caller's transaction.
     *
     * <p>Must run inside the transaction that creates the booking. For the moment between the
     * booking existing and the hold being stamped, the rooms would be counted twice — briefly
     * understating availability, which is survivable — but if the booking were to roll back
     * after the hold had been released, they would be counted zero times, and the room would be
     * quietly resold. Keeping both in one transaction removes the window entirely (BR-47).
     */
    @Transactional
    public void convertForQuotation(UUID quotationId, BookingEntity booking) {
        stamp(quotationId, HoldStatus.CONVERTED, booking);
    }

    private void stamp(UUID quotationId, HoldStatus status, BookingEntity booking) {
        if (quotationId == null) {
            return;
        }
        List<RoomAllotmentHoldEntity> active =
                holdRepository.findByQuotation_QuotationIdAndStatus(quotationId, HoldStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (RoomAllotmentHoldEntity hold : active) {
            hold.setStatus(status);
            hold.setReleasedAt(now);
            if (booking != null) {
                hold.setBooking(booking);
            }
        }
        holdRepository.saveAll(active);
    }

    /**
     * Holds last as long as the offer does. A hold outliving its quotation would sit on stock
     * nobody is still selling; one expiring sooner would let the rooms go while the customer is
     * still being asked to decide.
     */
    private OffsetDateTime expiryFor(QuotationEntity quotation) {
        LocalDate validUntil = quotation.getValidUntil();
        if (validUntil != null) {
            // End of that day where the hotel is, not in UTC. Pinning it to UTC made the hold
            // outlive its quotation by the zone offset — seven hours in Vietnam — so rooms stayed
            // locked into a morning on which the offer had already lapsed.
            return validUntil.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        return OffsetDateTime.now().plusDays(defaultHoldDays);
    }

    private UserEntity resolveActorQuietly() {
        try {
            return currentUserProvider.resolve(null);
        } catch (Exception e) {
            log.warn("Could not resolve actor for allotment hold: {}", e.getMessage());
            return null;
        }
    }
}
