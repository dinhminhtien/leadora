package com.novax.leadora.application.usecase.roomrequest;

import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.inventory.RoomAvailabilityAssessment;
import com.novax.leadora.application.usecase.inventory.StayAvailability;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoomRequestEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import com.novax.leadora.infrastructure.persistence.repository.RoomRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Raises the room request for Sales when a quotation cannot be covered from allotment.
 *
 * <p>This is where the round trip the feature exists to remove actually disappears. Reading the
 * quota answers most enquiries outright; when it does not, the rep should not then have to notice
 * the warning, remember the procedure, and re-key the same room type, dates and numbers into a
 * second form. The system already knows all of it, so it asks on their behalf.
 *
 * <p>Deliberately <b>not</b> built on {@link CreateRoomRequestUseCase}. That use case is the
 * deliberate act of asking and is allowed to fail loudly — notably with {@code NO_RESERVATION_STAFF}
 * when no one could answer. Failing loudly is wrong here: this runs as a side effect of saving a
 * quotation, and a missing Reservation account must never be the reason a rep cannot save their
 * work. Every problem this class meets is logged and swallowed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRoomRequestService {

    private static final DateTimeFormatter AS_OF_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final RoomRequestRepository roomRequestRepository;
    private final RoomRequestNotifier roomRequestNotifier;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;
    private final SystemAuditLogService systemAuditLogService;

    /**
     * Asks the Reservation team about the lines that could not be covered.
     *
     * <p>Skipped when the quotation already has an unanswered request: a rep adjusting a price or
     * a note five times must not put five identical questions in the Reservation inbox. The check
     * is on {@code PENDING} only — once answered, a genuinely new question deserves to be asked
     * again.
     *
     * @param askAboutEveryLine ask about all lines rather than only the ones the assessment
     *                          faulted. Set when the rooms were lost <em>after</em> a clean
     *                          assessment — another rep took them between assessing and holding —
     *                          in which case the assessment has no faulted lines to report and
     *                          filtering by them would send no question at all.
     */
    @Transactional
    public void raiseIfNeeded(QuotationEntity quotation, RoomAvailabilityAssessment assessment,
                              UserEntity actor, boolean askAboutEveryLine) {
        if (quotation == null || assessment == null) {
            return;
        }
        if (quotation.getCheckInDate() == null || quotation.getCheckOutDate() == null) {
            return;
        }

        List<RoomAvailabilityAssessment.LineAssessment> unconfirmed =
                askAboutEveryLine ? assessment.lines() : assessment.unconfirmedLines();
        if (unconfirmed.isEmpty()) {
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

            // Nobody to ask is a configuration problem worth a log line, not a reason to fail the
            // rep's save. The quotation still shows as needing confirmation either way.
            List<UserEntity> reservationStaff = roomRequestNotifier.activeReservationStaff();
            if (reservationStaff.isEmpty()) {
                log.warn("Quotation {} needs room confirmation but no active RESERVATION user exists",
                        quotation.getQuotationId());
                return;
            }

            int totalRooms = unconfirmed.stream()
                    .mapToInt(RoomAvailabilityAssessment.LineAssessment::requested)
                    .sum();

            RoomRequestEntity saved = roomRequestRepository.save(RoomRequestEntity.builder()
                    .quotation(quotation)
                    .roomTypeRequested(roomTypeLabel(unconfirmed))
                    .checkInDate(quotation.getCheckInDate())
                    .checkOutDate(quotation.getCheckOutDate())
                    .quantity(totalRooms)
                    .status(RoomRequestStatus.PENDING)
                    .requesterNote(buildContext(unconfirmed))
                    .requestedBy(actor)
                    .build());

            try {
                startSlaTrackingUseCase.execute("ROOM_REQUEST", "QUOTATION", quotation.getQuotationId());
            } catch (Exception e) {
                log.warn("SLA tracking failed for auto room request {}: {}", saved.getRequestId(), e.getMessage());
            }

            systemAuditLogService.log("ROOM_REQUEST", "QUOTATION", quotation.getQuotationId(),
                    "ROOM_REQUESTED_AUTO", actor, null, RoomRequestStatus.PENDING.name(),
                    saved.getRequesterNote());

            roomRequestNotifier.requestRaised(saved, reservationStaff, actor);

            log.info("Auto-raised room request {} for quotation {} ({} room(s) unconfirmed)",
                    saved.getRequestId(), quotation.getQuotationId(), totalRooms);

        } catch (Exception e) {
            log.warn("Could not auto-raise room request for quotation {}: {}",
                    quotation.getQuotationId(), e.getMessage());
        }
    }

    private static String roomTypeLabel(List<RoomAvailabilityAssessment.LineAssessment> lines) {
        String label = lines.stream()
                .map(RoomAvailabilityAssessment.LineAssessment::roomTypeName)
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("the quoted room");
        return label.length() > 255 ? label.substring(0, 252) + "..." : label;
    }

    /**
     * Spells out what the CRM already believes, so the Reservation team can see at a glance
     * whether they are being asked to extend a quota, publish a missing one, or re-confirm a
     * stale one — three different jobs that a bare "is this available?" cannot distinguish.
     */
    private static String buildContext(List<RoomAvailabilityAssessment.LineAssessment> lines) {
        StringBuilder note = new StringBuilder("Raised automatically — allotment does not cover this quotation.\n");
        for (RoomAvailabilityAssessment.LineAssessment line : lines) {
            StayAvailability stay = line.availability();
            note.append("• ").append(line.roomTypeName()).append(": need ").append(line.requested());

            if (stay == null || stay.availableForStay() == null) {
                note.append(", allotment not published for these dates");
            } else {
                note.append(", allotment shows ").append(stay.availableForStay())
                        .append(" (short ").append(line.shortfall()).append(')');
                if (!stay.limitingDates().isEmpty()) {
                    note.append(", tightest on ").append(stay.limitingDates().get(0));
                }
            }
            if (stay != null && stay.stale() && stay.oldestAsOf() != null) {
                note.append(", last updated ").append(AS_OF_FORMAT.format(stay.oldestAsOf()));
            }
            note.append('\n');
        }
        return note.toString().trim();
    }
}
