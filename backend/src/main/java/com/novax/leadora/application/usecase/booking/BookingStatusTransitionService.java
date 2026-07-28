package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.InventoryStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus.*;

@Service
@RequiredArgsConstructor
public class BookingStatusTransitionService {
    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final PaymentRepository paymentRepository;

    // Sales owns the deal, not the room: they may withdraw a booking, never confirm one.
    private static final Map<BookingStatus, Set<BookingStatus>> SALES_TRANSITIONS = Map.of(
        PENDING,    Set.of(CANCELLED),
        CONFIRMED,  Set.of(CANCELLED)
    );

    // The Reservation team decides whether the rooms exist, so only they may CONFIRM.
    private static final Map<BookingStatus, Set<BookingStatus>> RESERVATION_TRANSITIONS = Map.of(
        PENDING,    Set.of(CONFIRMED, REJECTED, CANCELLED),
        CONFIRMED,  Set.of(NO_SHOW, CANCELLED)
    );

    // The arrival desk moves a confirmed booking through the stay.
    private static final Map<BookingStatus, Set<BookingStatus>> FRONT_OFFICE_TRANSITIONS = Map.of(
        PENDING,    Set.of(CANCELLED),
        CONFIRMED,  Set.of(CHECKED_IN, CANCELLED),
        CHECKED_IN, Set.of(CHECKED_OUT)
    );

    private static Map<BookingStatus, Set<BookingStatus>> laneFor(TransitionActor actor) {
        return switch (actor) {
            case RESERVATION -> RESERVATION_TRANSITIONS;
            case FRONT_OFFICE -> FRONT_OFFICE_TRANSITIONS;
            case SALES -> SALES_TRANSITIONS;
        };
    }

    @Transactional
    public BookingEntity transition(UUID bookingId, BookingStatus newStatus, TransitionActor actor, String reason) {
        // 1. SELECT FOR UPDATE to prevent race conditions (C5)
        BookingEntity booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        BookingStatus oldStatus = booking.getStatus();

        // 2. Validate the transition against this actor's lane
        Set<BookingStatus> allowed = laneFor(actor).get(oldStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new BusinessException("BOOKING_TRANSITION_INVALID",
                String.format("Transition from %s to %s is not allowed for this actor.", oldStatus, newStatus),
                HttpStatus.CONFLICT);
        }

        // 3. Guard C5: Block CANCEL/REJECT if there is a PAID payment (NO_SHOW is intentionally allowed)
        if (newStatus == BookingStatus.CANCELLED || newStatus == BookingStatus.REJECTED) {
            boolean hasPaidPayment = paymentRepository.existsByBooking_BookingIdAndStatus(bookingId, PaymentStatus.PAID);
            if (hasPaidPayment) {
                throw new BusinessException("PAYMENT_CONFLICT",
                    "Không thể hủy booking đã thanh toán. Vui lòng liên hệ kế toán để xử lý hoàn tiền ngoài hệ thống trước khi yêu cầu hỗ trợ kỹ thuật cập nhật trạng thái.",
                    HttpStatus.CONFLICT);
            }
        }

        // 4. Update status & status change reason
        booking.setStatus(newStatus);
        if (reason != null && !reason.isBlank()) {
            booking.setStatusReason(reason);
        }
        
        // 5. Sync inventory status in booking details
        List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(bookingId);
        if (newStatus == BookingStatus.CHECKED_IN) {
            details.forEach(d -> d.setInventoryStatus(InventoryStatus.ALLOCATED));
        } else if (newStatus == BookingStatus.CHECKED_OUT || newStatus == BookingStatus.CANCELLED || newStatus == BookingStatus.REJECTED || newStatus == BookingStatus.NO_SHOW) {
            details.forEach(d -> d.setInventoryStatus(InventoryStatus.RELEASED));
        }
        bookingDetailRepository.saveAll(details);

        return bookingRepository.save(booking);
    }
}
