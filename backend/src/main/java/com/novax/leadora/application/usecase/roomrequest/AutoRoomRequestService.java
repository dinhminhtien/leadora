package com.novax.leadora.application.usecase.roomrequest;

import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoomRequestEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoomRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Puts the availability question to the Reservation team the moment the customer accepts.
 *
 * <p>This is the canonical trigger. The customer's acceptance is the point at which the sales
 * side needs a real answer about rooms — before that a quotation is an offer, and asking about
 * every offer would fill the Reservation inbox with questions most of which never mattered. A rep
 * who wants an earlier answer can still raise one by hand from the quotation.
 *
 * <p>Deliberately <b>not</b> built on {@link CreateRoomRequestUseCase}. That use case is the
 * deliberate act of asking and is allowed to fail loudly — notably with
 * {@code NO_RESERVATION_STAFF} when no one could answer. Failing loudly is wrong here: this runs
 * as a side effect of the customer accepting through the portal, and a missing Reservation account
 * must never be the reason a customer's acceptance is rejected. Every problem this class meets is
 * logged and swallowed; the quotation still shows as awaiting confirmation either way.
 *
 * <p>This class used to run on every quotation save, asking whenever Leadora's own allotment
 * arithmetic came up short. Both halves of that were wrong: the timing, and treating this system's
 * figures as a reason to escalate. Report 1 (FE-19, LI-02) makes Reservation the authority, so the
 * question is now asked because the sale reached the point of needing one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRoomRequestService {

    private final RoomRequestRepository roomRequestRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final RoomRequestNotifier roomRequestNotifier;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;
    private final SystemAuditLogService systemAuditLogService;

    /**
     * Raises the availability request for a quotation the customer has just accepted.
     *
     * <p>Skipped when one is already open: the rep may have asked ahead of the acceptance, and a
     * second identical question would only duplicate it in the Reservation inbox.
     */
    @Transactional
    public void raiseOnCustomerAcceptance(QuotationEntity quotation, UserEntity actor) {
        if (quotation == null
                || quotation.getCheckInDate() == null
                || quotation.getCheckOutDate() == null) {
            return;
        }

        try {
            boolean alreadyAsked = roomRequestRepository
                    .findFirstByQuotation_QuotationIdAndStatusNotInOrderByCreatedAtDesc(
                            quotation.getQuotationId(), RoomRequestStatus.notSpeakingForQuotation())
                    .filter(existing -> existing.getStatus() == RoomRequestStatus.PENDING)
                    .isPresent();
            if (alreadyAsked) {
                return;
            }

            // Nobody to ask is a configuration problem worth a log line, not a reason to refuse
            // the customer's acceptance.
            List<UserEntity> reservationStaff = roomRequestNotifier.activeReservationStaff();
            if (reservationStaff.isEmpty()) {
                log.warn("Quotation {} was accepted but no active RESERVATION user exists to ask",
                        quotation.getQuotationId());
                return;
            }

            List<QuotationDetailEntity> lines =
                    quotationDetailRepository.findByQuotation_QuotationId(quotation.getQuotationId());
            int totalRooms = lines.stream()
                    .mapToInt(line -> line.getQuantity() == null ? 0 : line.getQuantity())
                    .sum();
            if (totalRooms < 1) {
                log.warn("Quotation {} was accepted but carries no room lines to ask about",
                        quotation.getQuotationId());
                return;
            }

            RoomRequestEntity saved = roomRequestRepository.save(RoomRequestEntity.builder()
                    .quotation(quotation)
                    .roomTypeRequested(quotation.getRoomType())
                    .checkInDate(quotation.getCheckInDate())
                    .checkOutDate(quotation.getCheckOutDate())
                    .quantity(totalRooms)
                    .status(RoomRequestStatus.PENDING)
                    .requesterNote(buildContext(lines))
                    .requestedBy(actor)
                    .build());

            try {
                startSlaTrackingUseCase.execute("ROOM_REQUEST", "QUOTATION", quotation.getQuotationId());
            } catch (Exception e) {
                log.warn("SLA tracking failed for room request {}: {}", saved.getRequestId(), e.getMessage());
            }

            systemAuditLogService.log("ROOM_REQUEST", "QUOTATION", quotation.getQuotationId(),
                    "ROOM_REQUESTED_ON_ACCEPTANCE", actor, null, RoomRequestStatus.PENDING.name(),
                    saved.getRequesterNote());

            roomRequestNotifier.requestRaised(saved, reservationStaff, actor);

            log.info("Raised availability request {} for quotation {} on customer acceptance ({} room(s))",
                    saved.getRequestId(), quotation.getQuotationId(), totalRooms);

        } catch (Exception e) {
            log.warn("Could not raise the availability request for quotation {}: {}",
                    quotation.getQuotationId(), e.getMessage());
        }
    }

    /**
     * Spells out exactly what was sold, so the Reservation team can check it against the hotel's
     * system without opening the quotation. A bare "is this available?" costs a round trip to
     * establish what was actually asked for.
     */
    private static String buildContext(List<QuotationDetailEntity> lines) {
        StringBuilder note = new StringBuilder("Raised automatically — the customer accepted this quotation.\n");
        for (QuotationDetailEntity line : lines) {
            String room = line.getProductService() != null
                    ? line.getProductService().getName()
                    : line.getDescription();
            note.append("• ").append(room)
                    .append(" × ").append(line.getQuantity() == null ? 0 : line.getQuantity())
                    .append('\n');
        }
        return note.toString().trim();
    }
}
