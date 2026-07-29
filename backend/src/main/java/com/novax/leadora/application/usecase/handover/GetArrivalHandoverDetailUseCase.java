package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
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

        // Deliberately NOT scoped by assignee, unlike the operational detail endpoint.
        //
        // There, one Sales rep must not read another's records (BR-01 / BR-02). Here the assignee
        // is a work-queue default, not a confidentiality boundary: the same Front Office user can
        // list the whole desk on request (see ArrivalDeskScope), so refusing the detail would block
        // nothing while breaking the obvious case — opening a row from the desk-wide list.
        // The real boundaries are already enforced: the FO role plus HANDOVER_VIEW on the
        // controller, and the DRAFT check above.
        UUID bookingId = handover.getBooking() != null ? handover.getBooking().getBookingId() : null;
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
