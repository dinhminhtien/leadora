package com.novax.leadora.application.usecase.roomrequest;

import com.novax.leadora.api.dto.request.RespondRoomRequestRequest;
import com.novax.leadora.api.dto.response.RoomRequestResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.RoomRequestEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import com.novax.leadora.infrastructure.persistence.repository.RoomRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The Reservation team answers a room-availability request after checking the hotel's
 * real PMS. Their {@code heldUntil} commitment is recorded verbatim — the hold itself
 * lives in the PMS, not here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RespondRoomRequestUseCase {

    private final RoomRequestRepository roomRequestRepository;
    private final CurrentUserProvider currentUserProvider;
    private final RoomRequestNotifier roomRequestNotifier;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public RoomRequestResponse execute(UUID requestId, RespondRoomRequestRequest request) {
        RoomRequestStatus decision = request.getDecision();
        if (decision != RoomRequestStatus.CONFIRMED && decision != RoomRequestStatus.REJECTED) {
            throw new BusinessException("INVALID_ROOM_DECISION",
                    "A room request can only be answered with CONFIRMED or REJECTED.", HttpStatus.BAD_REQUEST);
        }

        // Locked read: two Reservation staff answering at once must not both pass the
        // "still PENDING" check below.
        RoomRequestEntity roomRequest = roomRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Room request", requestId));

        if (roomRequest.getStatus() != RoomRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "This room request was already answered (" + roomRequest.getStatus().name() + ").");
        }

        // Sales needs a reason to take back to the customer, and something to act on.
        if (decision == RoomRequestStatus.REJECTED && !StringUtils.hasText(request.getReservationNote())) {
            throw new BusinessException("RESERVATION_NOTE_REQUIRED",
                    "A note is required when rooms cannot be confirmed, so Sales can inform the customer.",
                    HttpStatus.BAD_REQUEST);
        }

        UserEntity actor = currentUserProvider.resolve(null);
        String previousStatus = roomRequest.getStatus().name();

        roomRequest.setStatus(decision);
        roomRequest.setReservationNote(request.getReservationNote());
        // A hold deadline only means anything on a confirmation.
        roomRequest.setHeldUntil(decision == RoomRequestStatus.CONFIRMED ? request.getHeldUntil() : null);
        roomRequest.setRespondedBy(actor);
        roomRequest.setRespondedAt(OffsetDateTime.now());

        RoomRequestEntity saved = roomRequestRepository.save(roomRequest);

        UUID quotationId = saved.getQuotation() != null ? saved.getQuotation().getQuotationId() : null;

        // UC-17.2: the Reservation team acted — stop the clock either way.
        if (quotationId != null) {
            try {
                resolveSlaBreachUseCase.executeByEntity("QUOTATION", quotationId);
            } catch (Exception e) {
                log.warn("SLA auto-resolve failed for room request {}: {}", requestId, e.getMessage());
            }
        }

        systemAuditLogService.log("ROOM_REQUEST", "QUOTATION", quotationId,
                "ROOM_" + decision.name(), actor, previousStatus, decision.name(),
                request.getReservationNote());

        roomRequestNotifier.requestAnswered(saved, actor);

        log.info("[AUDIT] Action: ROOM_REQUEST_{}, RequestId: {}, QuotationId: {}, RespondedBy: {}",
                decision.name(), requestId, quotationId, actor != null ? actor.getUserId() : null);

        return RoomRequestResponse.from(saved);
    }
}
