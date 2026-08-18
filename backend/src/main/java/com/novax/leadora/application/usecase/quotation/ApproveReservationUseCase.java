package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.application.event.ReservationApprovedEvent;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.ReservationDecisionLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.ReservationDecisionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * BR-15 — Approve Reservation Request.
 * Invoked by Reservation staff to confirm availability and create the booking.
 * Guards that the quotation status is in RESERVATION_PENDING.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApproveReservationUseCase {

    private final QuotationRepository quotationRepository;
    private final ConvertToBookingUseCase convertToBookingUseCase;
    private final ReservationDecisionLogRepository decisionLogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public BookingResponse execute(UUID quotationId) {
        log.info("Reservation staff approving quotation: {}", quotationId);

        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new BusinessException("QUOTATION_NOT_FOUND", "Quotation not found", HttpStatus.NOT_FOUND));

        // Guard: must be in RESERVATION_PENDING status
        if (quotation.getStatus() != QuotationStatus.RESERVATION_PENDING) {
            throw new BusinessException("INVALID_QUOTATION_STATUS",
                    "Only quotations in RESERVATION_PENDING status can be approved. Current: " + quotation.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        // 1. Resolve current Reservation staff user
        UserEntity reservationStaff = currentUserProvider.resolve(null);
        UUID staffId = reservationStaff != null ? reservationStaff.getUserId() : null;

        // 2. Delegate to ConvertToBookingUseCase to check availability, create Booking, copy detail records, and set status
        BookingResponse bookingResponse = convertToBookingUseCase.execute(quotationId, new ConvertToBookingRequest());

        // 3. Log reservation decision (referencing the created booking_id)
        ReservationDecisionLogEntity decisionLog = ReservationDecisionLogEntity.builder()
                .quotationId(quotationId)
                .decision("APPROVED")
                .decidedBy(staffId)
                .bookingId(bookingResponse.getBookingId())
                .build();
        decisionLogRepository.save(decisionLog);

        // 4. Publish Event
        eventPublisher.publishEvent(new ReservationApprovedEvent(quotation, bookingResponse.getBookingId()));

        log.info("Quotation {} successfully approved. Booking {} created.", quotationId, bookingResponse.getBookingId());
        return bookingResponse;
    }
}
