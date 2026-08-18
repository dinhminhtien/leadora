package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

/** UC-22.2 — View Arrival Handover Detail (Front Office). */
@Service
@RequiredArgsConstructor
public class GetArrivalHandoverDetailUseCase {

    private final OpHandoverRepository opHandoverRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ArrivalHandoverResponse execute(UUID handoverId) {
        OpHandoverEntity handover = opHandoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Arrival handover", handoverId));

        // A DRAFT handover hasn't been submitted to Front Office yet — hide its existence.
        if (handover.getStatus() == HandoverStatus.DRAFT) {
            throw new ResourceNotFoundException("Arrival handover", handoverId);
        }

        // BR-44 — the same "is this arrival still real?" rule the list and the readiness update
        // already apply, so all three arrival endpoints answer alike.
        //
        // Without it this endpoint was the loose one of the three: OpHandoverSpecification filters
        // the list on LIVE_FOR_ARRIVAL and UpdateHandoverReadinessUseCase re-checks it before
        // writing, but the detail would still render a handover whose booking had been cancelled or
        // checked out — a row the list deliberately hides. A drawer left open across a cancellation,
        // or a bookmarked link, was enough to reach it.
        //
        // 404 rather than 422, matching the DRAFT branch above: a handover that is not the front
        // desk's business does not get to confirm it exists.
        //
        // The cost, accepted deliberately: the front desk loses the explanation it would have got
        // from a 200 carrying bookingStatus ("this booking is cancelled"), and reads a plain
        // "not found" instead. The reason still reaches them through the 422 on the write, and the
        // drawer keeps its cached copy — see the note on isBookingActive in FrontOfficeHandoverScreen.
        BookingEntity booking = handover.getBooking();
        if (booking != null && !BookingStatus.LIVE_FOR_ARRIVAL.contains(booking.getStatus())) {
            throw new ResourceNotFoundException("Arrival handover", handoverId);
        }

        // Deliberately NOT scoped by assignee, unlike the operational detail endpoint.
        //
        // There, one Sales rep must not read another's records (BR-01 / BR-02). Here the assignee
        // is a work-queue default, not a confidentiality boundary: the same Front Office user can
        // list the whole desk on request (see ArrivalDeskScope), so refusing the detail would block
        // nothing while breaking the obvious case — opening a row from the desk-wide list.
        // The real boundaries are already enforced: the FO role plus HANDOVER_VIEW on the
        // controller, and the DRAFT check above.
        UUID bookingId = booking != null ? booking.getBookingId() : null;
        var details = bookingId != null
                ? bookingDetailRepository.findByBooking_BookingId(bookingId)
                : Collections.<com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity>emptyList();
        var payments = bookingId != null
                ? paymentRepository.findByBooking_BookingId(bookingId)
                : Collections.<com.novax.leadora.infrastructure.persistence.entity.PaymentEntity>emptyList();

        String assignedFoName = handover.getAssignedFoUserId() != null
                ? userRepository.findById(handover.getAssignedFoUserId())
                        .map(u -> u.getFullName())
                        .orElse(null)
                : null;

        return ArrivalHandoverResponse.fromDetail(handover, details, payments, assignedFoName);
    }
}
