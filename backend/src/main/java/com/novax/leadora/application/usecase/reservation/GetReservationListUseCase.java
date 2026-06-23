package com.novax.leadora.application.usecase.reservation;

import com.novax.leadora.api.dto.response.ReservationResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetReservationListUseCase {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;

    @Transactional(readOnly = true)
    public Page<ReservationResponse> execute(String search, String status, String sortBy, String sortDir, int page, int size) {
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        BookingStatus statusParam = null;
        if (StringUtils.hasText(status)) {
            try {
                statusParam = BookingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        Page<BookingEntity> bookings = bookingRepository.searchBookings(search, statusParam, pageable);

        return bookings.map(booking -> {
            List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(booking.getBookingId());
            return ReservationResponse.from(booking, details);
        });
    }
}
