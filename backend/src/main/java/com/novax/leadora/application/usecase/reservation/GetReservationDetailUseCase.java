package com.novax.leadora.application.usecase.reservation;

import com.novax.leadora.api.dto.response.ReservationResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetReservationDetailUseCase {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final com.novax.leadora.application.usecase.booking.BookingAccessPolicy bookingAccessPolicy;

    @Transactional(readOnly = true)
    public ReservationResponse execute(UUID id) {
        BookingEntity booking = bookingRepository.findByIdWithCustomerAndAssignedUser(id)
                .orElseThrow(() -> new com.novax.leadora.common.exception.ResourceNotFoundException("Reservation", id));

        // Assert access control policies
        bookingAccessPolicy.assertCanView(bookingAccessPolicy.currentUser(), booking);

        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(id);
        return ReservationResponse.from(booking, details);
    }
}
