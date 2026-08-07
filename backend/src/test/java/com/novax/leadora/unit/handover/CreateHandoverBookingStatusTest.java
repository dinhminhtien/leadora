package com.novax.leadora.unit.handover;

import com.novax.leadora.api.dto.request.CreateHandoverRequest;
import com.novax.leadora.application.usecase.handover.CreateHandoverUseCase;
import com.novax.leadora.application.usecase.handover.HandoverEditPolicy;
import com.novax.leadora.application.usecase.timeline.CreateInteractionTimelineUseCase;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-20.1 / BR-44 — which bookings may have an operational handover authored for them.
 *
 * <p>The guard this pins used to be a blacklist of three status <em>names</em> compared as strings
 * ({@code CANCELLED}, {@code REJECTED}, {@code CHECKED_OUT}), so {@code NO_SHOW} and {@code PENDING}
 * walked straight through: a handover could be authored for a guest who never arrived, or for a
 * booking Reservation had not confirmed. It is now the same whitelist the update path enforces —
 * {@link BookingStatus#EDITABLE_BY_SALES}.
 *
 * <p>Note the asymmetry with the update path, which is deliberate rather than an oversight: an
 * update on a {@code CHECKED_IN} booking is allowed when it answers a Front Office clarification,
 * because that reply is the only way out of {@code NEED_CLARIFICATION}. A <em>create</em> has no
 * such loop to unblock — a handover that does not exist yet cannot be under clarification.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateHandoverBookingStatusTest {

    @Mock private OpHandoverRepository opHandoverRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingDetailRepository bookingDetailRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private CreateInteractionTimelineUseCase createInteractionTimelineUseCase;

    private CreateHandoverUseCase useCase;
    private UUID bookingId;
    private UserEntity actor;

    @BeforeEach
    void setUp() {
        useCase = new CreateHandoverUseCase(
                opHandoverRepository, bookingRepository, bookingDetailRepository,
                paymentRepository, notificationRepository, userRepository,
                createInteractionTimelineUseCase, new HandoverEditPolicy("Asia/Ho_Chi_Minh"));
        bookingId = UUID.randomUUID();
        actor = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Sales user")
                .role(RoleEntity.builder().roleName("SALES").build())
                .build();

        when(opHandoverRepository.findByBooking_BookingId(any())).thenReturn(List.of());
        when(opHandoverRepository.save(any(OpHandoverEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(bookingDetailRepository.findByBooking_BookingId(any())).thenReturn(List.of());
        when(paymentRepository.findByBooking_BookingId(any())).thenReturn(List.of());
    }

    private void booking(BookingStatus status) {
        // Future arrival, so only the status guard can decide the outcome.
        booking(status, LocalDate.now().plusDays(3));
    }

    private void booking(BookingStatus status, LocalDate checkInDate) {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(
                BookingEntity.builder()
                        .bookingId(bookingId)
                        .bookingCode("BK-TEST")
                        .status(status)
                        .checkInDate(checkInDate)
                        .build()));
    }

    /** A DRAFT create — the least demanding valid request. */
    private CreateHandoverRequest draftRequest() {
        CreateHandoverRequest r = new CreateHandoverRequest();
        r.setBookingId(bookingId);
        r.setStatus("DRAFT");
        r.setOperationalNotes("note");
        return r;
    }

    @Test
    @DisplayName("A confirmed booking is the one state a handover may be authored for")
    void confirmedBookingAcceptsCreation() {
        booking(BookingStatus.CONFIRMED);

        assertThatCode(() -> useCase.execute(draftRequest(), actor)).doesNotThrowAnyException();

        verify(opHandoverRepository).save(any(OpHandoverEntity.class));
    }

    @Test
    @DisplayName("On the arrival day itself a handover may still be authored")
    void arrivalDayAcceptsCreation() {
        booking(BookingStatus.CONFIRMED, LocalDate.now());

        assertThatCode(() -> useCase.execute(draftRequest(), actor)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BR-26: a past arrival refuses a new handover — there is nothing left to prepare")
    void pastArrivalBlocksCreation() {
        booking(BookingStatus.CONFIRMED, LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> useCase.execute(draftRequest(), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has passed");

        verify(opHandoverRepository, never()).save(any());
    }

    @ParameterizedTest(name = "BR-44: a {0} booking refuses a new handover")
    @CsvSource({"CANCELLED", "REJECTED", "CHECKED_OUT", "NO_SHOW", "PENDING", "CHECKED_IN"})
    @DisplayName("The string blacklist this replaced let NO_SHOW and PENDING through")
    void deadOrUnconfirmedBookingBlocksCreation(BookingStatus status) {
        booking(status);

        assertThatThrownBy(() -> useCase.execute(draftRequest(), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(status.name());

        verify(opHandoverRepository, never()).save(any());
    }
}
