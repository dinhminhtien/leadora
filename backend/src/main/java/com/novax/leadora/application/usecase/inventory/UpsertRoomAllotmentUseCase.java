package com.novax.leadora.application.usecase.inventory;

import com.novax.leadora.api.dto.request.UpsertRoomAllotmentRequest;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.NotificationPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoomAllotmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Reservation team publishing, extending or withdrawing the hotel's allocation.
 *
 * <p>Takes a date range and writes one row per night (see {@code RoomAllotmentEntity}), so the
 * team works in the units they are given quota in while the system keeps the units it can deduct
 * from.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpsertRoomAllotmentUseCase {

    /** A year at a time. Beyond that the request is far more likely a typo than an intention. */
    private static final long MAX_RANGE_DAYS = 365;

    private final RoomAllotmentRepository allotmentRepository;
    private final ProductServiceRepository productServiceRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SystemAuditLogService systemAuditLogService;

    /**
     * @return the nights where the new quota sits below what has already been sold — published
     *         anyway, and reported so the caller can surface it
     */
    @Transactional
    public List<LocalDate> execute(UpsertRoomAllotmentRequest request) {
        ProductServiceEntity product = productServiceRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("INVALID_ROOM_TYPE",
                        "That room type does not exist.", HttpStatus.BAD_REQUEST));

        if (product.getCategory() != ProductCategory.ROOM || product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException("INVALID_ROOM_TYPE",
                    "\"" + product.getName() + "\" is not a room type currently on sale.",
                    HttpStatus.BAD_REQUEST);
        }
        if (request.getDateTo().isBefore(request.getDateFrom())) {
            throw new BusinessException("INVALID_DATE_RANGE",
                    "The end date must not be before the start date.", HttpStatus.BAD_REQUEST);
        }
        if (ChronoUnit.DAYS.between(request.getDateFrom(), request.getDateTo()) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException("DATE_RANGE_TOO_WIDE",
                    "Please publish at most " + MAX_RANGE_DAYS + " days at a time.", HttpStatus.BAD_REQUEST);
        }

        Set<java.time.DayOfWeek> weekdays = request.getWeekdays();
        boolean everyDay = weekdays == null || weekdays.isEmpty();
        boolean closed = Boolean.TRUE.equals(request.getClosed());
        OffsetDateTime asOf = request.getAsOf() != null ? request.getAsOf() : OffsetDateTime.now();
        UserEntity actor = resolveActorQuietly();
        UUID actorId = actor != null ? actor.getUserId() : null;

        LocalDate toExclusive = request.getDateTo().plusDays(1);

        List<LocalDate> written = new ArrayList<>();
        for (LocalDate date = request.getDateFrom(); date.isBefore(toExclusive); date = date.plusDays(1)) {
            if (!everyDay && !weekdays.contains(date.getDayOfWeek())) {
                continue;
            }
            allotmentRepository.upsertNight(product.getProductId(), date, request.getAllottedQty(),
                    closed, request.getNote(), asOf, actorId);
            written.add(date);
        }

        List<LocalDate> oversold = findOversoldNights(product, request.getAllottedQty(), written);

        systemAuditLogService.log("ROOM_ALLOTMENT", "PRODUCT", product.getProductId(),
                "ALLOTMENT_PUBLISHED", actor, null, String.valueOf(request.getAllottedQty()),
                "%s → %s, %d night(s)%s".formatted(request.getDateFrom(), request.getDateTo(),
                        written.size(), closed ? ", closed" : ""));

        if (!oversold.isEmpty()) {
            warnAffectedReps(product, oversold);
        }
        return oversold;
    }

    /**
     * Finds nights where the new quota is below what is already sold.
     *
     * <p>Deliberately does <b>not</b> refuse the publish (BR-49). The hotel is entitled to take
     * an allocation back, and it sometimes does so after rooms have been sold from it. Rejecting
     * the entry would leave the CRM knowingly holding a number the Reservation team has just told
     * it is wrong; the honest response is to record the truth and escalate the conflict to the
     * people who can renegotiate it.
     */
    private List<LocalDate> findOversoldNights(ProductServiceEntity product, int allotted, List<LocalDate> nights) {
        if (nights.isEmpty()) {
            return List.of();
        }
        LocalDate first = nights.get(0);
        LocalDate lastExclusive = nights.get(nights.size() - 1).plusDays(1);

        Map<LocalDate, Integer> soldPerNight = new java.util.HashMap<>();
        for (BookingDetailRepository.BookingNightSpan span : bookingDetailRepository.findCommittedSpans(
                BookingStatus.CONSUMING_INVENTORY, List.of(product.getProductId()), first, lastExclusive)) {
            LocalDate cursor = span.getCheckInDate().isBefore(first) ? first : span.getCheckInDate();
            LocalDate end = span.getCheckOutDate().isAfter(lastExclusive) ? lastExclusive : span.getCheckOutDate();
            while (cursor.isBefore(end)) {
                soldPerNight.merge(cursor, span.getQuantity(), Integer::sum);
                cursor = cursor.plusDays(1);
            }
        }

        return nights.stream()
                .filter(night -> soldPerNight.getOrDefault(night, 0) > allotted)
                .toList();
    }

    /** MSG-32 — the reps holding the affected bookings are the ones who must renegotiate. */
    private void warnAffectedReps(ProductServiceEntity product, List<LocalDate> oversold) {
        try {
            LocalDate first = oversold.get(0);
            LocalDate lastExclusive = oversold.get(oversold.size() - 1).plusDays(1);

            List<BookingEntity> affected = bookingDetailRepository.findAffectedBookings(
                    BookingStatus.CONSUMING_INVENTORY, product.getProductId(), first, lastExclusive);

            Set<UserEntity> recipients = new LinkedHashSet<>();
            for (BookingEntity booking : affected) {
                if (booking.getAssignedUser() != null) {
                    recipients.add(booking.getAssignedUser());
                }
            }

            String message = ("Room allotment for %s on %s was reduced below the number of rooms already"
                    + " sold. Please review the affected bookings.")
                    .formatted(product.getName(), first.equals(lastExclusive.minusDays(1))
                            ? first.toString() : first + " → " + lastExclusive.minusDays(1));

            for (UserEntity recipient : recipients) {
                notificationRepository.save(NotificationEntity.builder()
                        .user(recipient)
                        .title("Room Allotment Reduced Below Bookings")
                        .message(message)
                        .type("ALLOTMENT_OVERSOLD")
                        .priority(NotificationPriority.HIGH)
                        .relatedEntity("ROOM_ALLOTMENT")
                        .relatedId(product.getProductId())
                        .build());
            }
            log.warn("Allotment for {} now below committed bookings on {} night(s); {} rep(s) notified",
                    product.getName(), oversold.size(), recipients.size());
        } catch (Exception e) {
            // The quota itself is already published and correct; failing the whole operation
            // because a warning could not be delivered would discard the Reservation team's work.
            log.warn("Could not notify reps about reduced allotment for {}: {}",
                    product.getName(), e.getMessage());
        }
    }

    private UserEntity resolveActorQuietly() {
        try {
            return currentUserProvider.resolve(null);
        } catch (Exception e) {
            log.warn("Could not resolve actor for allotment publish: {}", e.getMessage());
            return null;
        }
    }
}
