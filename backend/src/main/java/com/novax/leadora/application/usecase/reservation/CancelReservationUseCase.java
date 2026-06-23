package com.novax.leadora.application.usecase.reservation;

import com.novax.leadora.api.dto.request.CancelReservationRequest;
import com.novax.leadora.api.dto.response.ReservationResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.InventoryStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelReservationUseCase {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;

    @Transactional
    public ReservationResponse execute(UUID id, CancelReservationRequest request) {
        BookingEntity booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));

        BookingStatus oldStatus = booking.getStatus();
        
        // 1. Update Booking status to CANCELLED
        booking.setStatus(BookingStatus.CANCELLED);
        
        // Save the cancellation reason in special requests or rejection reason if applicable
        String cancellationNote = "[Cancellation Reason: " + request.getReason() + "]";
        if (booking.getSpecialRequests() == null || booking.getSpecialRequests().isEmpty()) {
            booking.setSpecialRequests(cancellationNote);
        } else {
            booking.setSpecialRequests(booking.getSpecialRequests() + "\n" + cancellationNote);
        }
        
        BookingEntity saved = bookingRepository.save(booking);

        // 2. Release held inventory by setting details status to RELEASED
        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(id);
        for (BookingDetailEntity detail : details) {
            detail.setInventoryStatus(InventoryStatus.RELEASED);
            bookingDetailRepository.save(detail);
        }

        // BR-37: Write Slf4j Audit Log
        log.info("[AUDIT] Action: CANCEL_RESERVATION, TargetRecord: {}, OldValue: {}, NewValue: CANCELLED, Reason: {}, Timestamp: {}",
                id, oldStatus, request.getReason(), OffsetDateTime.now());

        return ReservationResponse.from(saved, details);
    }
}
