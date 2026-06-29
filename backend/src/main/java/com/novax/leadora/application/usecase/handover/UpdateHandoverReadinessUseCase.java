package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.request.UpdateReadinessStatusRequest;
import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * UC-22.3 — Update Handover Readiness Status (Front Office).
 *
 * <p>Per BR-27, Front Office may update arrival readiness ONLY — it must not touch the booking
 * confirmation, quotation approval or deal value. So this use case changes nothing but
 * {@code readiness_status} (and the Sales→FO lifecycle markers that follow from FO acting on it):
 * the first FO action on a SUBMITTED handover acknowledges it, and marking it READY flags the
 * room/arrival as fully prepared.
 */
@Service
@RequiredArgsConstructor
public class UpdateHandoverReadinessUseCase {

    private final OpHandoverRepository opHandoverRepository;

    @Transactional
    public ArrivalHandoverResponse execute(UUID handoverId, UpdateReadinessStatusRequest request, UserEntity actor) {
        OpHandoverEntity handover = opHandoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Arrival handover", handoverId));

        if (handover.getStatus() == HandoverStatus.DRAFT) {
            throw new IllegalStateException("Handover chưa được gửi tới Lễ tân nên không thể cập nhật.");
        }

        ReadinessStatus newReadiness = parseReadiness(request.getReadinessStatus());
        handover.setReadinessStatus(newReadiness);
        handover.setUpdatedBy(actor);

        // First FO touch on a freshly submitted handover = acknowledgement.
        if (handover.getStatus() == HandoverStatus.SUBMITTED) {
            handover.setStatus(HandoverStatus.ACKNOWLEDGED);
            handover.setAcknowledgedAt(OffsetDateTime.now());
        }
        // Room/arrival fully prepared.
        if (newReadiness == ReadinessStatus.READY) {
            handover.setStatus(HandoverStatus.READY);
        }

        return ArrivalHandoverResponse.from(opHandoverRepository.save(handover));
    }

    private ReadinessStatus parseReadiness(String value) {
        try {
            return ReadinessStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("Trạng thái sẵn sàng không hợp lệ: " + value);
        }
    }
}
