package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.infrastructure.persistence.repository.RoomAllotmentHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Returns rooms held for quotations that were never converted.
 *
 * <p>Without this the quota leaks: every abandoned quotation keeps sitting on its rooms, and
 * availability drifts steadily below the truth until someone notices rooms are unsellable for no
 * visible reason.
 *
 * <p>Runs every minute rather than nightly because the rooms are unsellable for as long as the
 * sweep is late, and an expired hold serves nobody. The work is a single bulk {@code UPDATE}
 * against an index on {@code (status, expires_at)}, so a minute's cadence costs effectively
 * nothing when there is nothing to expire — which is most of the time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllotmentHoldExpiryScheduler {

    private final RoomAllotmentHoldRepository holdRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseExpiredHolds() {
        try {
            int released = holdRepository.expireLapsedHolds(OffsetDateTime.now());
            if (released > 0) {
                log.info("Allotment hold sweep: {} expired hold(s) released back to availability", released);
            }
        } catch (Exception e) {
            log.error("Allotment hold expiry scheduler error: {}", e.getMessage(), e);
        }
    }
}
