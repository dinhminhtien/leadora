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
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingStatusTransitionServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingDetailRepository bookingDetailRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ActivityLogPublisher activityLogPublisher;

    private ObjectMapper objectMapper = new ObjectMapper();

    private BookingStatusTransitionService service;

    private UUID bookingId;
    private BookingEntity booking;
    private List<BookingDetailEntity> details;

    @BeforeEach
    void setUp() {
        service = new BookingStatusTransitionService(bookingRepository, bookingDetailRepository, paymentRepository, activityLogPublisher, objectMapper);
        bookingId = UUID.randomUUID();
        booking = BookingEntity.builder()
                .bookingId(bookingId)
                .status(BookingStatus.PENDING)
                .build();
        details = new ArrayList<>();
        details.add(BookingDetailEntity.builder()
                .inventoryStatus(InventoryStatus.ALLOCATED)
                .build());
    }

    @Test
    @DisplayName("Should successfully transition status and update reason")
    void testTransitionSuccess() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingDetailRepository.findByBooking_BookingId(bookingId)).thenReturn(details);
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingEntity result = service.transition(bookingId, BookingStatus.CANCELLED,
                TransitionActor.SALES, "Customer changed mind");

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.getStatusReason()).isEqualTo("Customer changed mind");
        assertThat(details.get(0).getInventoryStatus()).isEqualTo(InventoryStatus.RELEASED);

        verify(bookingRepository).findByIdForUpdate(bookingId);
        verify(bookingDetailRepository).saveAll(details);
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("Should transition PENDING to CONFIRMED for the Reservation actor")
    void testTransitionPendingToConfirmedSuccess() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingDetailRepository.findByBooking_BookingId(bookingId)).thenReturn(details);
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Only the Reservation team may confirm — they are the ones who know the rooms exist.
        BookingEntity result = service.transition(bookingId, BookingStatus.CONFIRMED,
                TransitionActor.RESERVATION, "Approved booking");

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.getStatusReason()).isEqualTo("Approved booking");

        verify(bookingRepository).findByIdForUpdate(bookingId);
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when booking is not found")
    void testTransitionBookingNotFound() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(bookingId, BookingStatus.CANCELLED,
                TransitionActor.SALES, "Test reason"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(bookingRepository).findByIdForUpdate(bookingId);
        verifyNoInteractions(bookingDetailRepository, paymentRepository);
    }

    @Test
    @DisplayName("Should throw BusinessException when transition is not allowed for Sales actor")
    void testTransitionInvalidCRM() {
        // PENDING -> CHECKED_IN is not in SALES_TRANSITIONS
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.transition(bookingId, BookingStatus.CHECKED_IN,
                TransitionActor.SALES, "Invalid transition"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Transition from PENDING to CHECKED_IN is not allowed for this actor")
                .extracting(e -> ((BusinessException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(bookingRepository).findByIdForUpdate(bookingId);
        verifyNoInteractions(bookingDetailRepository, paymentRepository);
    }

    @Test
    @DisplayName("Should throw BusinessException when transition is not allowed for FO actor")
    void testTransitionInvalidFO() {
        // PENDING -> REJECTED is not in FRONT_OFFICE_TRANSITIONS
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.transition(bookingId, BookingStatus.REJECTED,
                TransitionActor.FRONT_OFFICE, "Invalid transition"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Transition from PENDING to REJECTED is not allowed for this actor")
                .extracting(e -> ((BusinessException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(bookingRepository).findByIdForUpdate(bookingId);
        verifyNoInteractions(bookingDetailRepository, paymentRepository);
    }

    @Test
    @DisplayName("Should throw BusinessException when trying to cancel/reject a PAID booking")
    void testCancelPaidBookingThrows() {
        booking.setStatus(BookingStatus.CONFIRMED); // CONFIRMED -> CANCELLED is allowed by rules
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.existsByBooking_BookingIdAndStatus(bookingId, PaymentStatus.PAID)).thenReturn(true);

        assertThatThrownBy(() -> service.transition(bookingId, BookingStatus.CANCELLED,
                TransitionActor.SALES, "Cancel paid booking"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể hủy booking đã thanh toán")
                .extracting(e -> ((BusinessException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(bookingRepository).findByIdForUpdate(bookingId);
        verify(paymentRepository).existsByBooking_BookingIdAndStatus(bookingId, PaymentStatus.PAID);
        verifyNoInteractions(bookingDetailRepository);
    }

    @Test
    @DisplayName("Should allow NO_SHOW transition even if booking is PAID")
    void testNoShowAllowedEvenIfPaid() {
        booking.setStatus(BookingStatus.CONFIRMED); // CONFIRMED -> NO_SHOW is allowed
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingDetailRepository.findByBooking_BookingId(bookingId)).thenReturn(details);
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // NO_SHOW belongs to the Reservation lane.
        BookingEntity result = service.transition(bookingId, BookingStatus.NO_SHOW,
                TransitionActor.RESERVATION, "Guest did not show up");

        assertThat(result.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
        assertThat(details.get(0).getInventoryStatus()).isEqualTo(InventoryStatus.RELEASED);

        verify(bookingRepository).findByIdForUpdate(bookingId);
        verify(bookingDetailRepository).saveAll(details);
        verify(bookingRepository).save(booking);
        // paymentRepository should not be queried since NO_SHOW is not CANCELLED/REJECTED
        verifyNoInteractions(paymentRepository);
    }
}
