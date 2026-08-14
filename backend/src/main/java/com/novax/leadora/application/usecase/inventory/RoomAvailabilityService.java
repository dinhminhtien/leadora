package com.novax.leadora.application.usecase.inventory;

import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Presents the room figures the Reservation team has synchronized into this CRM (FE-19).
 *
 * <p>The whole calculation is one line:
 * <pre>available = allotted − booked</pre>
 * quota from {@code room_allotments}, which only the Reservation team writes, less the rooms this
 * CRM has already committed through bookings. Availability is never stored, only derived, so it
 * cannot fall out of step with the bookings the way a running balance would.
 *
 * <p><b>This is reference information, not an availability decision.</b> Report 1 (FE-19, LI-02)
 * puts determining, allocating, holding and locking room inventory on the Reservation side of the
 * boundary; Leadora displays what Reservation published so a rep can prepare a quotation and
 * decide whether to raise a request. Nothing in the sales workflow is refused on the strength of
 * these numbers — the only authority on whether rooms exist is the Reservation team's recorded
 * answer to a room request.
 *
 * <p>A third term used to be subtracted here: soft holds this CRM placed on the quota when a
 * quotation was saved. That was Leadora holding inventory, which the scope excludes, so the holds
 * and the machinery behind them are gone.
 */
@Service
@RequiredArgsConstructor
public class RoomAvailabilityService {

    private final RoomAvailabilityDbFetcher dbFetcher;

    /** How old published quota may get before the UI has to warn about it (BR-50). */
    @Value("${leadora.room-allotment.stale-hours:24}")
    private long staleHours;

    /** Per-night picture for a set of room types over {@code [from, toExclusive)}. */
    @Transactional(readOnly = true)
    public Map<UUID, List<NightAvailability>> nights(
            Collection<ProductServiceEntity> products,
            LocalDate from,
            LocalDate toExclusive) {

        Map<UUID, List<NightAvailability>> result = new LinkedHashMap<>();
        if (products == null || products.isEmpty() || !from.isBefore(toExclusive)) {
            return result;
        }

        List<UUID> productIds = products.stream().map(p -> p.getProductId()).toList();

        Map<UUID, Map<LocalDate, AllotmentNightDto>> quota = new HashMap<>();
        for (AllotmentNightDto row : dbFetcher.getPublishedAllotments(productIds, from, toExclusive)) {
            quota.computeIfAbsent(row.productId(), k -> new HashMap<>())
                    .put(row.stayDate(), row);
        }

        Map<UUID, Map<LocalDate, Integer>> booked = new HashMap<>();
        for (CommittedSpanDto span : dbFetcher.getCommittedSpans(
                BookingStatus.CONSUMING_INVENTORY, productIds, from, toExclusive)) {
            spread(booked, span.productId(), span.checkInDate(), span.checkOutDate(),
                    from, toExclusive, span.quantity());
        }

        OffsetDateTime staleBefore = OffsetDateTime.now().minusHours(staleHours);

        for (ProductServiceEntity product : products) {
            UUID productId = product.getProductId();
            Map<LocalDate, AllotmentNightDto> productQuota = quota.getOrDefault(productId, Map.of());
            Map<LocalDate, Integer> productBooked = booked.getOrDefault(productId, Map.of());

            List<NightAvailability> perNight = new ArrayList<>();
            for (LocalDate date = from; date.isBefore(toExclusive); date = date.plusDays(1)) {
                int nightBooked = productBooked.getOrDefault(date, 0);
                AllotmentNightDto row = productQuota.get(date);

                if (row == null) {
                    perNight.add(NightAvailability.unpublished(date, nightBooked));
                    continue;
                }

                int allotted = row.allottedQty();
                // Floored at zero: quota can legitimately be cut below what is already sold
                // (BR-49), and a negative figure on screen would read as a system fault rather
                // than as the overbooking it actually is. The overbooking is surfaced by the
                // publish path, which notifies the affected reps.
                int available = Math.max(0, allotted - nightBooked);
                OffsetDateTime asOf = row.asOf();

                perNight.add(new NightAvailability(
                        date,
                        allotted,
                        nightBooked,
                        available,
                        Boolean.TRUE.equals(row.closed()),
                        asOf,
                        asOf != null && asOf.isBefore(staleBefore)));
            }
            result.put(productId, perNight);
        }
        return result;
    }

    /**
     * Stay-level picture, which is what a sales rep actually asks for: one answer per room type
     * for {@code [checkIn, checkOut)}. The check-out morning is not a night and is not counted.
     */
    @Transactional(readOnly = true)
    public Map<UUID, StayAvailability> stays(
            Collection<ProductServiceEntity> products,
            LocalDate checkIn,
            LocalDate checkOut) {

        Map<UUID, List<NightAvailability>> perNight = nights(products, checkIn, checkOut);

        Map<UUID, StayAvailability> result = new LinkedHashMap<>();
        for (ProductServiceEntity product : products) {
            result.put(product.getProductId(),
                    fold(product, perNight.getOrDefault(product.getProductId(), List.of())));
        }
        return result;
    }

    /** Collapses a stay's nights into its weakest one. */
    private static StayAvailability fold(ProductServiceEntity product, List<NightAvailability> perNight) {
        List<LocalDate> closed = new ArrayList<>();
        List<LocalDate> unpublished = new ArrayList<>();
        boolean stale = false;
        OffsetDateTime oldestAsOf = null;
        Integer minimum = null;

        for (NightAvailability night : perNight) {
            if (night.closed()) {
                closed.add(night.date());
            }
            if (!night.published()) {
                unpublished.add(night.date());
                continue;
            }
            stale |= night.stale();
            if (night.asOf() != null && (oldestAsOf == null || night.asOf().isBefore(oldestAsOf))) {
                oldestAsOf = night.asOf();
            }
            minimum = (minimum == null) ? night.available() : Math.min(minimum, night.available());
        }

        // Unknown nights make the stay unknown: reporting the minimum of the nights we happen to
        // know about would quietly answer a narrower question than the one that was asked.
        Integer availableForStay = unpublished.isEmpty() ? minimum : null;

        List<LocalDate> limiting = new ArrayList<>();
        if (availableForStay != null) {
            for (NightAvailability night : perNight) {
                if (Objects.equals(night.available(), availableForStay)) {
                    limiting.add(night.date());
                }
            }
        }

        return new StayAvailability(
                product.getProductId(),
                product.getName(),
                availableForStay,
                List.copyOf(limiting),
                List.copyOf(closed),
                List.copyOf(unpublished),
                stale,
                oldestAsOf);
    }

    /** Adds a span's quantity to every night it covers inside the window. */
    private static void spread(
            Map<UUID, Map<LocalDate, Integer>> accumulator,
            UUID productId,
            LocalDate spanStart,
            LocalDate spanEndExclusive,
            LocalDate windowStart,
            LocalDate windowEndExclusive,
            Integer quantity) {

        if (productId == null || quantity == null || spanStart == null || spanEndExclusive == null) {
            return;
        }
        LocalDate cursor = spanStart.isBefore(windowStart) ? windowStart : spanStart;
        LocalDate end = spanEndExclusive.isAfter(windowEndExclusive) ? windowEndExclusive : spanEndExclusive;

        Map<LocalDate, Integer> byDate = accumulator.computeIfAbsent(productId, k -> new HashMap<>());
        while (cursor.isBefore(end)) {
            byDate.merge(cursor, quantity, (a, b) -> a + b);
            cursor = cursor.plusDays(1);
        }
    }
}
