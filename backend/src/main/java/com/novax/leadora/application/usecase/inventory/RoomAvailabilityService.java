package com.novax.leadora.application.usecase.inventory;

import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.RoomAllotmentHoldRepository;
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
 * Works out how many rooms of each type are still sellable, per night and per stay.
 *
 * <p>The whole calculation is one line:
 * <pre>available = allotted − booked − held</pre>
 * with each term coming from a different place — quota from {@code room_allotments}, sold rooms
 * from bookings, provisional rooms from live holds. Availability is never stored, only derived,
 * so it cannot fall out of step with the bookings the way a running balance would.
 *
 * <p><b>This is quota, not the hotel's inventory.</b> The hotel does not disclose how full it is;
 * it grants a block to sell and this counts down within it. "None left" therefore means "our
 * allocation is spent", and the Reservation team can often get more — which is why running out
 * routes to {@link RoomAvailabilityVerdict#NEEDS_CONFIRMATION} rather than a refusal.
 *
 * <p>Numbers only. Whether a given number is good enough to quote on is policy, and lives in
 * {@code QuotationAvailabilityChecker}.
 */
@Service
@RequiredArgsConstructor
public class RoomAvailabilityService {

    private final RoomAllotmentHoldRepository holdRepository;
    private final RoomAvailabilityDbFetcher dbFetcher;

    /** How old published quota may get before the UI has to warn about it (BR-50). */
    @Value("${leadora.room-allotment.stale-hours:24}")
    private long staleHours;

    /**
     * Per-night picture for a set of room types over {@code [from, toExclusive)}.
     *
     * <p>{@code excludeQuotationId} discounts one quotation's own live hold. A quotation being
     * revised is already holding its rooms; counting them would have it compete with itself and
     * report the rooms it is sitting on as unavailable.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<NightAvailability>> nights(
            Collection<ProductServiceEntity> products,
            LocalDate from,
            LocalDate toExclusive,
            UUID excludeQuotationId) {

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

        Map<UUID, Map<LocalDate, Integer>> held = new HashMap<>();
        for (RoomAllotmentHoldRepository.HoldSpan span : holdRepository.findActiveSpans(
                productIds, from, toExclusive)) {
            if (excludeQuotationId != null && excludeQuotationId.equals(span.getQuotationId())) {
                continue;
            }
            spread(held, span.getProductId(), span.getCheckInDate(), span.getCheckOutDate(),
                    from, toExclusive, span.getQuantity());
        }

        OffsetDateTime staleBefore = OffsetDateTime.now().minusHours(staleHours);

        for (ProductServiceEntity product : products) {
            UUID productId = product.getProductId();
            Map<LocalDate, AllotmentNightDto> productQuota = quota.getOrDefault(productId, Map.of());
            Map<LocalDate, Integer> productBooked = booked.getOrDefault(productId, Map.of());
            Map<LocalDate, Integer> productHeld = held.getOrDefault(productId, Map.of());

            List<NightAvailability> perNight = new ArrayList<>();
            for (LocalDate date = from; date.isBefore(toExclusive); date = date.plusDays(1)) {
                int nightBooked = productBooked.getOrDefault(date, 0);
                int nightHeld = productHeld.getOrDefault(date, 0);
                AllotmentNightDto row = productQuota.get(date);

                if (row == null) {
                    perNight.add(NightAvailability.unpublished(date, nightBooked, nightHeld));
                    continue;
                }

                int allotted = row.allottedQty();
                // Floored at zero: quota can legitimately be cut below what is already sold
                // (BR-49), and a negative figure on screen would read as a system fault rather
                // than as the overbooking it actually is. The overbooking is surfaced by the
                // publish path, which notifies the affected reps.
                int available = Math.max(0, allotted - nightBooked - nightHeld);
                OffsetDateTime asOf = row.asOf();

                perNight.add(new NightAvailability(
                        date,
                        allotted,
                        nightBooked,
                        nightHeld,
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
            LocalDate checkOut,
            UUID excludeQuotationId) {

        Map<UUID, List<NightAvailability>> perNight =
                nights(products, checkIn, checkOut, excludeQuotationId);

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
