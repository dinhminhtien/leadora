package com.novax.leadora.application.usecase.roomrequest;

import com.novax.leadora.api.dto.response.RoomRequestResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.quotation.QuotationAccessPolicy;
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

import java.util.UUID;

/**
 * UC-26.4 — Sales withdraws a room-availability request the Reservation team has not yet
 * answered.
 *
 * <p>The request row is never deleted: it moves to {@link RoomRequestStatus#CANCELLED},
 * which keeps the history for audit (POST-3) while taking it out of the Reservation inbox,
 * whose queue is the set of PENDING rows (POST-2).
 *
 * <p>Because a cancelled request does not speak for its quotation
 * ({@link RoomRequestStatus#notSpeakingForQuotation()}), whatever was current before it —
 * an earlier confirmation, or nothing — becomes current again, and Sales can raise a fresh
 * request (UC-26.5) without any special re-submission path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelRoomRequestUseCase {

    private final RoomRequestRepository roomRequestRepository;
    private final QuotationAccessPolicy quotationAccessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public RoomRequestResponse execute(UUID requestId, String reason) {
        // Locked read, for the same reason the answer path takes one: a Reservation
        // staffer may be answering this very request. Whichever transaction commits
        // first wins, and the other fails its status check rather than overwriting.
        RoomRequestEntity roomRequest = roomRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Room request", requestId));

        // Cancelling is a write on the quotation's workflow, so it takes the same
        // owner-scoping as raising the request in the first place.
        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), roomRequest.getQuotation());

        // E5.1 — only an unanswered request can be withdrawn. Naming the state it is
        // actually in saves the rep from re-opening the record to find out.
        if (roomRequest.getStatus() != RoomRequestStatus.PENDING) {
            throw new BusinessException("ROOM_REQUEST_ALREADY_PROCESSED",
                    "Request already processed and cannot be cancelled (it is "
                            + roomRequest.getStatus().name() + ").",
                    HttpStatus.CONFLICT);
        }

        UserEntity actor = currentUserProvider.resolve(null);
        String previousStatus = roomRequest.getStatus().name();

        roomRequest.setStatus(RoomRequestStatus.CANCELLED);
        RoomRequestEntity saved = roomRequestRepository.save(roomRequest);

        UUID quotationId = saved.getQuotation() != null ? saved.getQuotation().getQuotationId() : null;

        // The Reservation team is no longer expected to answer, so stop the UC-17.2 clock —
        // otherwise a withdrawn request keeps ticking towards a breach nobody can resolve.
        if (quotationId != null) {
            try {
                resolveSlaBreachUseCase.executeByEntity("QUOTATION", quotationId);
            } catch (Exception e) {
                log.warn("SLA auto-resolve failed for cancelled room request {}: {}", requestId, e.getMessage());
            }
        }

        systemAuditLogService.log("ROOM_REQUEST", "QUOTATION", quotationId,
                "ROOM_CANCELLED", actor, previousStatus, RoomRequestStatus.CANCELLED.name(), reason);

        log.info("[AUDIT] Action: ROOM_REQUEST_CANCELLED, RequestId: {}, QuotationId: {}, CancelledBy: {}",
                requestId, quotationId, actor != null ? actor.getUserId() : null);

        return RoomRequestResponse.from(saved);
    }
}
