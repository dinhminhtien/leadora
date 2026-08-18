package com.novax.leadora.unit.handover;
import com.novax.leadora.application.usecase.handover.*;

import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-22.2 — what the arrival desk is allowed to open.
 *
 * <p>The point of these tests is that this endpoint answers the same question as the other two
 * arrival endpoints. The list filters on {@code LIVE_FOR_ARRIVAL} and the readiness update
 * re-checks it, but the detail used to check only DRAFT — so it would render a handover whose
 * booking had been cancelled or checked out, a row the list deliberately hides.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetArrivalHandoverDetailUseCaseTest {

    @Mock private OpHandoverRepository opHandoverRepository;
    @Mock private BookingDetailRepository bookingDetailRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;

    private GetArrivalHandoverDetailUseCase useCase;
    private UUID handoverId;

    @BeforeEach
    void setUp() {
        useCase = new GetArrivalHandoverDetailUseCase(
                opHandoverRepository, bookingDetailRepository, paymentRepository, userRepository);
        handoverId = UUID.randomUUID();
        when(bookingDetailRepository.findByBooking_BookingId(any())).thenReturn(List.of());
        when(paymentRepository.findByBooking_BookingId(any())).thenReturn(List.of());
    }

    /** A submitted handover sitting on a booking in {@code bookingStatus}. */
    private void handoverOn(HandoverStatus status, BookingStatus bookingStatus) {
        BookingEntity booking = BookingEntity.builder()
                .bookingId(UUID.randomUUID())
                .bookingCode("BK-001")
                .status(bookingStatus)
                .build();
        OpHandoverEntity handover = OpHandoverEntity.builder()
                .handoverId(handoverId)
                .booking(booking)
                .status(status)
                .readinessStatus(ReadinessStatus.PENDING_REVIEW)
                .build();
        when(opHandoverRepository.findById(handoverId)).thenReturn(Optional.of(handover));
    }

    // ---------------------------------------------------------------- what may be opened

    @ParameterizedTest(name = "a {0} booking is still the front desk''s business")
    @CsvSource({"CONFIRMED", "CHECKED_IN"})
    void returnsTheDetailWhileTheArrivalIsLive(BookingStatus bookingStatus) {
        handoverOn(HandoverStatus.SUBMITTED, bookingStatus);

        ArrivalHandoverResponse response = useCase.execute(handoverId);

        assertThat(response.getBookingCode()).isEqualTo("BK-001");
        assertThat(response.getBookingStatus()).isEqualTo(bookingStatus.name());
    }

    // ---------------------------------------------------------------- what must not be opened

    @ParameterizedTest(name = "BR-44: a {0} booking hides its handover from the desk")
    @CsvSource({"CANCELLED", "REJECTED", "NO_SHOW", "CHECKED_OUT", "PENDING"})
    @DisplayName("A booking outside LIVE_FOR_ARRIVAL is reported as not found, like the list hides it")
    void hidesAHandoverWhoseBookingIsNoLongerLive(BookingStatus bookingStatus) {
        handoverOn(HandoverStatus.SUBMITTED, bookingStatus);

        assertThatThrownBy(() -> useCase.execute(handoverId))
                .isInstanceOf(ResourceNotFoundException.class);

        // Refused before the joins: a row the desk may not see should not cost three queries.
        verify(bookingDetailRepository, never()).findByBooking_BookingId(any());
        verify(paymentRepository, never()).findByBooking_BookingId(any());
    }

    @Test
    @DisplayName("PENDING is refused too — the operational create path can still reach that state")
    void hidesAHandoverOnABookingThatWasNeverConfirmed() {
        // CreateHandoverUseCase screens booking status with an older blacklist that misses PENDING
        // and NO_SHOW, so such a handover can exist. The arrival desk must not be where it surfaces.
        handoverOn(HandoverStatus.SUBMITTED, BookingStatus.PENDING);

        assertThatThrownBy(() -> useCase.execute(handoverId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("A DRAFT is hidden as well — Sales has not sent it yet")
    void hidesADraft() {
        handoverOn(HandoverStatus.DRAFT, BookingStatus.CONFIRMED);

        assertThatThrownBy(() -> useCase.execute(handoverId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(bookingDetailRepository, never()).findByBooking_BookingId(any());
    }

    @Test
    void throwsWhenNoHandoverHasThatId() {
        when(opHandoverRepository.findById(handoverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(handoverId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- the defensive edge

    @Test
    @DisplayName("A handover with no booking still renders — booking_id is NOT NULL, so the guard is defensive")
    void stillRendersAHandoverThatHasNoBooking() {
        OpHandoverEntity handover = OpHandoverEntity.builder()
                .handoverId(handoverId)
                .status(HandoverStatus.SUBMITTED)
                .readinessStatus(ReadinessStatus.PENDING_REVIEW)
                .build();
        when(opHandoverRepository.findById(handoverId)).thenReturn(Optional.of(handover));

        ArrivalHandoverResponse response = useCase.execute(handoverId);

        // The booking-status guard must not turn a detached row into a 404: the rest of the method
        // already renders one, and failing here would hide a record nobody could then repair.
        assertThat(response.getHandoverId()).isEqualTo(handoverId);
        assertThat(response.getBookingStatus()).isNull();
    }
}
