package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.UUID;

/**
 * UC-20.3 — View Operational Handover Detail (Sales/Reservation).
 */
@Service
@RequiredArgsConstructor
public class GetHandoverDetailUseCase {

    private final OpHandoverRepository opHandoverRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public ArrivalHandoverResponse execute(UUID handoverId) {
        OpHandoverEntity handover = opHandoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Handover not found."));

        UUID bookingId = handover.getBooking() != null ? handover.getBooking().getBookingId() : null;
        var details = bookingId != null
                ? bookingDetailRepository.findByBooking_BookingId(bookingId)
                : Collections.<com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity>emptyList();
        var payments = bookingId != null
                ? paymentRepository.findByBooking_BookingId(bookingId)
                : Collections.<com.novax.leadora.infrastructure.persistence.entity.PaymentEntity>emptyList();

        return ArrivalHandoverResponse.fromDetail(handover, details, payments);
    }
}
