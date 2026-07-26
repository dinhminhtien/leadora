package com.novax.leadora.application.usecase.roomrequest;

import com.novax.leadora.api.dto.request.CreateRoomRequestRequest;
import com.novax.leadora.api.dto.response.RoomRequestResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.quotation.QuotationAccessPolicy;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoomRequestEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoomRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Sales asks the Reservation team whether a quotation's rooms are available.
 *
 * <p>Room type and dates come from the quotation, never from the request body, so the
 * question can never drift from what the customer is being quoted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateRoomRequestUseCase {

    /** Answers that still "speak" for a quotation and must be retired on a re-ask. */
    private static final List<RoomRequestStatus> SUPERSEDABLE = List.of(
            RoomRequestStatus.PENDING, RoomRequestStatus.CONFIRMED, RoomRequestStatus.REJECTED);

    private final RoomRequestRepository roomRequestRepository;
    private final QuotationRepository quotationRepository;
    private final QuotationAccessPolicy quotationAccessPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final RoomRequestNotifier roomRequestNotifier;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public RoomRequestResponse execute(CreateRoomRequestRequest request) {
        QuotationEntity quotation = quotationRepository.findById(request.getQuotationId())
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", request.getQuotationId()));

        // Same owner-scoping as viewing the quotation.
        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

        if (quotation.getCheckInDate() == null || quotation.getCheckOutDate() == null) {
            throw new BusinessException("QUOTATION_DATES_MISSING",
                    "The quotation has no check-in/check-out dates to ask about.", HttpStatus.BAD_REQUEST);
        }

        // Fail now if nobody can answer: Send and Convert are gated on a confirmed room,
        // so a request nobody receives would strand the quotation with no explanation.
        List<UserEntity> reservationStaff = roomRequestNotifier.requireReservationStaff();

        UserEntity actor = currentUserProvider.resolve(null);

        // Re-asking the exact same question is a no-op: return the open request instead of
        // creating a duplicate and pinging the Reservation team again.
        RoomRequestEntity existing = roomRequestRepository
                .findFirstByQuotation_QuotationIdAndStatusNotOrderByCreatedAtDesc(
                        quotation.getQuotationId(), RoomRequestStatus.SUPERSEDED)
                .orElse(null);
        if (existing != null && existing.getStatus() == RoomRequestStatus.PENDING
                && isSameQuestion(existing, quotation, request.getQuantity())) {
            return RoomRequestResponse.from(existing);
        }

        // Anything previously answered no longer applies once a new question is asked.
        List<RoomRequestEntity> previous = roomRequestRepository
                .findByQuotation_QuotationIdAndStatusIn(quotation.getQuotationId(), SUPERSEDABLE);
        for (RoomRequestEntity old : previous) {
            old.setStatus(RoomRequestStatus.SUPERSEDED);
        }
        if (!previous.isEmpty()) {
            roomRequestRepository.saveAll(previous);
        }

        RoomRequestEntity saved = roomRequestRepository.save(RoomRequestEntity.builder()
                .quotation(quotation)
                .roomTypeRequested(quotation.getRoomType())
                .checkInDate(quotation.getCheckInDate())
                .checkOutDate(quotation.getCheckOutDate())
                .quantity(request.getQuantity())
                .status(RoomRequestStatus.PENDING)
                .requestedBy(actor)
                .build());

        // UC-17.2: the Reservation team is now on the clock. Tracked against the
        // QUOTATION because SlaEntityResolver resolves that to the quotation's owner —
        // the Sales rep who needs to hear about a breach.
        try {
            startSlaTrackingUseCase.execute("ROOM_REQUEST", "QUOTATION", quotation.getQuotationId());
        } catch (Exception e) {
            log.warn("SLA tracking failed for room request {}: {}", saved.getRequestId(), e.getMessage());
        }

        systemAuditLogService.log("ROOM_REQUEST", "QUOTATION", quotation.getQuotationId(),
                "ROOM_REQUESTED", actor, null, RoomRequestStatus.PENDING.name(),
                "%d x %s for %s → %s".formatted(saved.getQuantity(), saved.getRoomTypeRequested(),
                        saved.getCheckInDate(), saved.getCheckOutDate()));

        roomRequestNotifier.requestRaised(saved, reservationStaff, actor);

        return RoomRequestResponse.from(saved);
    }

    private static boolean isSameQuestion(RoomRequestEntity request, QuotationEntity quotation, Integer quantity) {
        return Objects.equals(request.getCheckInDate(), quotation.getCheckInDate())
                && Objects.equals(request.getCheckOutDate(), quotation.getCheckOutDate())
                && Objects.equals(request.getQuantity(), quantity)
                && Objects.equals(
                        request.getRoomTypeRequested() == null ? null : request.getRoomTypeRequested().trim(),
                        quotation.getRoomType() == null ? null : quotation.getRoomType().trim());
    }
}
