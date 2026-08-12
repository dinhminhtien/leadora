package com.novax.leadora.application.usecase.inventory;

import com.novax.leadora.api.dto.request.ReconcileAllotmentRequest;
import com.novax.leadora.api.dto.response.AllotmentReconciliationResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoomAllotmentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoomAllotmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The daily reconciliation: bringing the CRM's allotment back in line with the hotel's system.
 *
 * <p><b>Why this exists at all.</b> Allotment is keyed in by hand from a system this CRM cannot
 * read, so it drifts — the hotel sells through other channels, releases extra rooms, or takes
 * some back, and nothing here changes. The obvious fix would be to have the Reservation team
 * update the CRM after every guest movement, but that recreates the very
 * person-waiting-on-person loop this feature was built to remove, just moved to the end of the
 * process. A once-a-day sweep costs one screen instead of one message per booking.
 *
 * <p><b>The subtraction is done here, not by the person.</b> A PMS shows how many rooms are
 * free; it does not show "the block you were given was twelve". Asking the desk to work the
 * total out in their head for every row is how a reconciliation screen ends up less accurate
 * than the drift it was meant to correct. So they enter what they can see and the server
 * reconstructs the total:
 *
 * <pre>allotted = observed free + already sold</pre>
 *
 * <p><b>Holds are deliberately not added back.</b> A hold is internal bookkeeping — the hotel is
 * never told about it, so a held room still shows as free in their system and is already inside
 * {@code observed free}. Adding it would inflate the block by rooms nobody granted. Confirmed
 * bookings are the opposite case: the Reservation team enters those into the PMS, so they are
 * already gone from what it reports as free and have to be added back to recover the total.
 *
 * <p>That last point is an assumption about how the desk works. If a site does <em>not</em> put
 * CRM bookings into the PMS, {@code observed free} would already be the whole block and this
 * would over-count — worth confirming before go-live rather than discovering from a bad number.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileAllotmentUseCase {

    private final RoomAllotmentRepository allotmentRepository;
    private final ProductServiceRepository productServiceRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public AllotmentReconciliationResponse execute(ReconcileAllotmentRequest request) {
        List<ReconcileAllotmentRequest.Entry> entries = request.getEntries();

        Map<UUID, ProductServiceEntity> products = resolveRooms(entries);
        LocalDate first = entries.stream().map(ReconcileAllotmentRequest.Entry::getStayDate)
                .min(LocalDate::compareTo).orElseThrow();
        LocalDate lastExclusive = entries.stream().map(ReconcileAllotmentRequest.Entry::getStayDate)
                .max(LocalDate::compareTo).orElseThrow().plusDays(1);

        // Lock the nights being corrected: a sale landing between reading "sold" and writing the
        // new total would be silently absorbed into it, and the room would go missing.
        allotmentRepository.lockNightsForUpdate(products.keySet(), first, lastExclusive);

        Map<UUID, Map<LocalDate, Integer>> soldPerNight = soldPerNight(products.keySet(), first, lastExclusive);
        Map<UUID, Map<LocalDate, RoomAllotmentEntity>> existing = existingRows(products.keySet(), first, lastExclusive);

        OffsetDateTime asOf = request.getAsOf() != null ? request.getAsOf() : OffsetDateTime.now();
        UserEntity actor = resolveActorQuietly();
        UUID actorId = actor != null ? actor.getUserId() : null;

        List<AllotmentReconciliationResponse.Change> changes = new ArrayList<>();
        int changed = 0;

        for (ReconcileAllotmentRequest.Entry entry : entries) {
            ProductServiceEntity product = products.get(entry.getProductId());
            int booked = soldPerNight
                    .getOrDefault(entry.getProductId(), Map.of())
                    .getOrDefault(entry.getStayDate(), 0);

            int newAllotted = entry.getActualAvailable() + booked;

            RoomAllotmentEntity current = existing
                    .getOrDefault(entry.getProductId(), Map.of())
                    .get(entry.getStayDate());
            Integer previousAllotted = current != null ? current.getAllottedQty() : null;

            // A reconciliation always refreshes as_of, even where the number is unchanged:
            // "someone checked this today and it was right" is exactly what the staleness
            // warning needs to know, and is a different fact from "nobody has looked".
            allotmentRepository.upsertNight(
                    entry.getProductId(),
                    entry.getStayDate(),
                    newAllotted,
                    current != null && Boolean.TRUE.equals(current.getClosed()),
                    current != null ? current.getNote() : null,
                    asOf,
                    actorId);

            if (!Objects.equals(previousAllotted, newAllotted)) {
                changed++;
            }

            changes.add(AllotmentReconciliationResponse.Change.builder()
                    .productId(entry.getProductId())
                    .roomType(product.getName())
                    .stayDate(entry.getStayDate())
                    .previousAllotted(previousAllotted)
                    .newAllotted(newAllotted)
                    .booked(booked)
                    .observedAvailable(entry.getActualAvailable())
                    .build());
        }

        // One audit row per room type, not one summary row for the batch: system_audit_logs
        // requires an entity_id, and a reconciliation covering four room types has no single
        // subject. Per-product rows also make the trail answer the question people actually ask
        // of it — "who last touched the Deluxe quota" — rather than only "a batch happened".
        Map<UUID, Long> reconciledPerProduct = changes.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        AllotmentReconciliationResponse.Change::getProductId,
                        java.util.stream.Collectors.counting()));

        reconciledPerProduct.forEach((productId, nightCount) ->
                systemAuditLogService.log("ROOM_ALLOTMENT", "PRODUCT", productId,
                        "ALLOTMENT_RECONCILED", actor, null, String.valueOf(nightCount),
                        "%s: %d night(s) reconciled for %s → %s"
                                .formatted(products.get(productId).getName(), nightCount,
                                        first, lastExclusive.minusDays(1))));

        log.info("Allotment reconciliation: {} night(s) checked, {} corrected", entries.size(), changed);

        return AllotmentReconciliationResponse.builder()
                .nightsReconciled(entries.size())
                .nightsChanged(changed)
                .changes(changes)
                .build();
    }

    private Map<UUID, ProductServiceEntity> resolveRooms(List<ReconcileAllotmentRequest.Entry> entries) {
        List<UUID> ids = entries.stream()
                .map(ReconcileAllotmentRequest.Entry::getProductId)
                .distinct()
                .toList();

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

    private Map<UUID, Map<LocalDate, Integer>> soldPerNight(
            java.util.Collection<UUID> productIds, LocalDate from, LocalDate toExclusive) {

        Map<UUID, Map<LocalDate, Integer>> sold = new HashMap<>();
        for (BookingDetailRepository.BookingNightSpan span : bookingDetailRepository.findCommittedSpans(
                BookingStatus.CONSUMING_INVENTORY, productIds, from, toExclusive)) {

            LocalDate cursor = span.getCheckInDate().isBefore(from) ? from : span.getCheckInDate();
            LocalDate end = span.getCheckOutDate().isAfter(toExclusive) ? toExclusive : span.getCheckOutDate();
            Map<LocalDate, Integer> byDate = sold.computeIfAbsent(span.getProductId(), k -> new HashMap<>());
            while (cursor.isBefore(end)) {
                byDate.merge(cursor, span.getQuantity(), Integer::sum);
                cursor = cursor.plusDays(1);
            }
        }
        return sold;
    }

    private Map<UUID, Map<LocalDate, RoomAllotmentEntity>> existingRows(
            java.util.Collection<UUID> productIds, LocalDate from, LocalDate toExclusive) {

        Map<UUID, Map<LocalDate, RoomAllotmentEntity>> rows = new HashMap<>();
        for (RoomAllotmentEntity row : allotmentRepository.findPublished(productIds, from, toExclusive)) {
            rows.computeIfAbsent(row.getProduct().getProductId(), k -> new HashMap<>())
                    .put(row.getStayDate(), row);
        }
        return rows;
    }

    private UserEntity resolveActorQuietly() {
        try {
            return currentUserProvider.resolve(null);
        } catch (Exception e) {
            log.warn("Could not resolve actor for allotment reconciliation: {}", e.getMessage());
            return null;
        }
    }
}
