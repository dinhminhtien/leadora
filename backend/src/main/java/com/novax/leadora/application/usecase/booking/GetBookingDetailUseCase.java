package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.api.dto.response.BookingDetailResponse;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetBookingDetailUseCase {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final BookingAccessPolicy bookingAccessPolicy;

    @Transactional(readOnly = true)
    public BookingResponse execute(UUID bookingId) {
        BookingEntity booking = bookingRepository.findByIdWithCustomerAndAssignedUser(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));

        // Assert access control policies
        bookingAccessPolicy.assertCanView(bookingAccessPolicy.currentUser(), booking);

        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(bookingId);
        List<BookingDetailResponse> detailResponses = details.stream()
                .map(BookingDetailResponse::from)
                .collect(Collectors.toList());

        return BookingResponse.from(booking, detailResponses);
    }
}
