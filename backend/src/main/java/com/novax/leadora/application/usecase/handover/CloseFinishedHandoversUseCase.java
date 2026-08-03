package com.novax.leadora.application.usecase.handover;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Closes handovers whose arrival is over (bug G — the lifecycle had no end).
 *
 * <p>Nothing marked a handover finished once the guest had come and gone, so every record stayed
 * nominally open forever: the arrival desk relied on a booking-status filter to stop the list
 * growing without bound, and "how many arrivals are still outstanding" had no answer at all.
 *
 * <p>Runs as a scheduled sweep rather than reacting to the booking change, because this codebase has
 * no event bus and no cross-module orchestrator — a booking status change has no hook to attach to.
 * A sweep is also self-healing: a handover missed for any reason is closed on the next run instead of
 * staying open forever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloseFinishedHandoversUseCase {

    private final OpHandoverRepository opHandoverRepository;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

    /** @return how many handovers were closed */
    @Transactional
    public int execute() {
        List<OpHandoverEntity> finished =
                opHandoverRepository.findFinishedButNotClosed(BookingStatus.LIVE_FOR_ARRIVAL);
        if (finished.isEmpty()) {
            return 0;
        }

        for (OpHandoverEntity handover : finished) {
            HandoverStatus previous = handover.getStatus();
            handover.setStatus(HandoverStatus.CLOSED);
            opHandoverRepository.save(handover);

            try {
                ObjectNode payload = objectMapper.createObjectNode()
                        .put("previousStatus", previous != null ? previous.name() : null)
                        .put("newStatus", HandoverStatus.CLOSED.name())
                        .put("bookingStatus", handover.getBooking().getStatus() != null
                                ? handover.getBooking().getStatus().name() : null)
                        .put("closedBy", "system");
                activityLogPublisher.publish(
                        ActivityLogType.HANDOVER_SUBMITTED,
                        EntityType.HANDOVER,
                        handover.getHandoverId(),
                        "Handover closed - arrival finished (" + previous + " -> CLOSED)",
                        payload);
            } catch (Exception e) {
                log.warn("Failed to publish handover close activity for {}: {}",
                        handover.getHandoverId(), e.getMessage());
            }
        }

        log.info("Closed {} handover(s) whose arrival has finished", finished.size());
        return finished.size();
    }
}
