package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoomAllotmentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.NotificationPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoomAllotmentRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The morning nudge to reconcile allotment against the hotel's system.
 *
 * <p>Without this, staleness is only ever a badge on a screen: it warns whoever happens to be
 * looking, and nobody owns fixing it. The figures are keyed in by hand from a system this CRM
 * cannot read, so they decay on their own — the hotel sells through other channels and nothing
 * here changes. Someone has to be told, by name, that the numbers reps are quoting from have
 * gone unverified.
 *
 * <p>Only counts nights that are <b>still sellable</b>. Quota that went stale last week for dates
 * that have already passed is not worth anyone's morning; nothing can be sold into it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleAllotmentScheduler {

    /** Role name in the {@code roles} table — the desk that owns the hotel's figures. */
    private static final String RESERVATION_ROLE = "RESERVATION";

    /** How far ahead to care. Quota nobody has published yet is not stale, just absent. */
    private static final int LOOKAHEAD_DAYS = 60;

    private final RoomAllotmentRepository allotmentRepository;
    private final ProductServiceRepository productServiceRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Value("${leadora.room-allotment.stale-hours:24}")
    private long staleHours;

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void warnAboutStaleAllotment() {
        try {
            List<UUID> roomIds = productServiceRepository.findByCategory(ProductCategory.ROOM).stream()
                    .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                    .map(product -> product.getProductId())
                    .toList();
            if (roomIds.isEmpty()) {
                return;
            }

            LocalDate today = LocalDate.now();
            OffsetDateTime staleBefore = OffsetDateTime.now().minusHours(staleHours);

            List<RoomAllotmentEntity> stale = allotmentRepository
                    .findPublished(roomIds, today, today.plusDays(LOOKAHEAD_DAYS)).stream()
                    .filter(row -> !Boolean.TRUE.equals(row.getClosed()))
                    .filter(row -> row.getAsOf() != null && row.getAsOf().isBefore(staleBefore))
                    .toList();

            if (stale.isEmpty()) {
                return;
            }

            long roomTypes = stale.stream()
                    .map(row -> row.getProduct().getProductId())
                    .distinct()
                    .count();
            OffsetDateTime oldest = stale.stream()
                    .map(row -> row.getAsOf())
                    .min((a, b) -> a.compareTo(b))
                    .orElse(null);

            List<UserEntity> reservationStaff = userRepository.findByRoleName(RESERVATION_ROLE).stream()
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .toList();

            if (reservationStaff.isEmpty()) {
                log.warn("{} allotment night(s) are stale but no active RESERVATION user exists to tell",
                        stale.size());
                return;
            }

            String message = ("%d upcoming night(s) across %d room type(s) have not been reconciled"
                    + " with the hotel%s. Sales are quoting from these figures — please check them.")
                    .formatted(stale.size(), roomTypes,
                            oldest != null ? " (oldest: " + oldest.toLocalDate() + ")" : "");

            for (UserEntity recipient : reservationStaff) {
                notificationRepository.save(NotificationEntity.builder()
                        .user(recipient)
                        .title("Room Allotment Needs Reconciling")
                        .message(message)
                        .type("ALLOTMENT_STALE")
                        .priority(NotificationPriority.NORMAL)
                        .relatedEntity("ROOM_ALLOTMENT")
                        .build());
            }

            log.info("Stale allotment sweep: {} night(s) across {} room type(s), {} staff notified",
                    stale.size(), roomTypes, reservationStaff.size());

        } catch (Exception e) {
            log.error("Stale allotment scheduler error: {}", e.getMessage(), e);
        }
    }
}
