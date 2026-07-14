package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.api.dto.request.ProcessBookingRequest;
import com.novax.leadora.api.dto.response.BookingDetailResponse;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessBookingUseCase {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public BookingResponse execute(UUID bookingId, ProcessBookingRequest request) {
        BookingEntity booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with ID: " + bookingId));

        BookingStatus newStatus;
        try {
            newStatus = BookingStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + request.getStatus());
        }

        booking.setStatus(newStatus);

        if (newStatus == BookingStatus.REJECTED && request.getRejectionReason() != null) {
            String currentReqs = booking.getSpecialRequests();
            String reasonTag = "[Rejection Reason: " + request.getRejectionReason() + "]";
            if (currentReqs == null || currentReqs.isEmpty()) {
                booking.setSpecialRequests(reasonTag);
            } else {
                booking.setSpecialRequests(currentReqs + "\n" + reasonTag);
            }
            booking.setRejectionReason(request.getRejectionReason());
        }

        BookingEntity saved = bookingRepository.save(booking);

        // UC-15.1: notify the assigned staff of the booking status decision
        if (saved.getAssignedUser() != null) {
            try {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(saved.getAssignedUser())
                        .title("Booking " + newStatus.name())
                        .message("Booking " + saved.getBookingCode() + " is now " + newStatus.name() + ".")
                        .type("BOOKING_UPDATE")
                        .relatedEntity("BOOKING")
                        .relatedId(bookingId)
                        .build();
                notificationRepository.save(notification);
            } catch (Exception e) {
                log.warn("Booking-update notification failed for booking {}: {}", bookingId, e.getMessage());
            }
        }

        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(bookingId);
        List<BookingDetailResponse> detailResponses = details.stream()
                .map(BookingDetailResponse::from)
                .collect(Collectors.toList());

        return BookingResponse.from(saved, detailResponses);
    }
}
