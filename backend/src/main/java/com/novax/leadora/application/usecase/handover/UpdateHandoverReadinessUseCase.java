package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.request.UpdateReadinessStatusRequest;
import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * UC-22.3 — Update Handover Readiness Status (Front Office).
 *
 * <p>Per BR-27, Front Office may update arrival readiness ONLY — it must not touch the booking
 * confirmation, quotation approval or deal value. So this use case changes nothing but
 * {@code readiness_status} (+ clarification note and the Sales→FO lifecycle markers that follow):
 * the first FO action acknowledges the handover, READY_FOR_ARRIVAL marks the room prepared, and
 * NEED_CLARIFICATION (with a required note) notifies the originating Sales/Reservation user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateHandoverReadinessUseCase {

    /** The readiness values a Front Office Staff is allowed to set (UC-22.3, step 4 / E7.3). */
    private static final Set<ReadinessStatus> FO_SETTABLE = EnumSet.of(
            ReadinessStatus.REVIEWED, ReadinessStatus.READY_FOR_ARRIVAL, ReadinessStatus.NEED_CLARIFICATION);

    private final OpHandoverRepository opHandoverRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public ArrivalHandoverResponse execute(UUID handoverId, UpdateReadinessStatusRequest request, UserEntity actor) {
        OpHandoverEntity handover = opHandoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Arrival handover", handoverId));

        // PRE-3: only handovers already sent to Front Office can be updated.
        if (handover.getStatus() == HandoverStatus.DRAFT) {
            throw new IllegalStateException("Handover chưa được gửi tới Lễ tân nên không thể cập nhật.");
        }

        ReadinessStatus newReadiness = parseReadiness(request.getReadinessStatus());

        // E7.2 — clarification note is required when asking for clarification.
        String note = request.getClarificationNote();
        if (newReadiness == ReadinessStatus.NEED_CLARIFICATION && !StringUtils.hasText(note)) {
            throw new IllegalStateException("Vui lòng nhập nội dung cần làm rõ.");
        }

        handover.setReadinessStatus(newReadiness);
        handover.setClarificationNote(
                newReadiness == ReadinessStatus.NEED_CLARIFICATION ? note.trim() : null);
        handover.setUpdatedBy(actor);

        // Stamp the acknowledgement once, on the first FO action.
        if (handover.getAcknowledgedAt() == null) {
            handover.setAcknowledgedAt(OffsetDateTime.now());
        }
        // Keep the Sales→FO status in sync with the FO readiness:
        //  READY_FOR_ARRIVAL → READY (room prepared); otherwise the FO has it in hand → ACKNOWLEDGED
        //  (so a handover sent back to NEED_CLARIFICATION is no longer shown as READY).
        handover.setStatus(newReadiness == ReadinessStatus.READY_FOR_ARRIVAL
                ? HandoverStatus.READY
                : HandoverStatus.ACKNOWLEDGED);

        OpHandoverEntity saved = opHandoverRepository.save(handover);

        // Step 9 / POST-3 — notify the originating Sales/Reservation user on NEED_CLARIFICATION.
        if (newReadiness == ReadinessStatus.NEED_CLARIFICATION) {
            notifyClarificationNeeded(saved, actor);
        }

        log.info("FO readiness update: handover={} readiness={} by={}",
                handoverId, newReadiness, actor != null ? actor.getUserId() : null);

        return ArrivalHandoverResponse.from(saved);
    }

    private void notifyClarificationNeeded(OpHandoverEntity handover, UserEntity actor) {
        UserEntity recipient = handover.getCreatedBy();
        if (recipient == null) {
            return; // no originating user recorded — nothing to notify
        }
        BookingEntity booking = handover.getBooking();
        String bookingCode = booking != null ? booking.getBookingCode() : "";
        String by = actor != null && actor.getFullName() != null ? actor.getFullName() : "Front Office";

        NotificationEntity notification = NotificationEntity.builder()
                .user(recipient)
                .title("Handover Clarification Requested")
                .message(by + " requested clarification for handover " + bookingCode + ": " + handover.getClarificationNote())
                .type("HANDOVER")
                .relatedEntity("HANDOVER")
                .relatedId(handover.getHandoverId())
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
        notificationRepository.save(notification);
    }

    private ReadinessStatus parseReadiness(String value) {
        ReadinessStatus parsed;
        try {
            parsed = ReadinessStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("Trạng thái sẵn sàng không hợp lệ: " + value);
        }
        // E7.3 — FO cannot set the initial PENDING_REVIEW (or any non-FO value).
        if (!FO_SETTABLE.contains(parsed)) {
            throw new IllegalStateException("Trạng thái sẵn sàng không hợp lệ: " + value);
        }
        return parsed;
    }
}
