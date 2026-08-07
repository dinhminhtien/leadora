package com.novax.leadora.unit.handover;
import com.novax.leadora.application.usecase.handover.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novax.leadora.api.dto.request.UpdateHandoverRequest;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.timeline.CreateInteractionTimelineUseCase;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
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
import org.springframework.security.access.AccessDeniedException;

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
 * UC-20.4 authorization (PRE-4 / E6-4.1) and BR-44.
 *
 * <p>The check this replaces was wrapped in {@code if (actor != null && actor.getRole() != null)},
 * so a caller carrying no role skipped authorization entirely — it failed <em>open</em>. It also
 * allowed only ADMIN or the creator, refusing a Manager a record they are allowed to read.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateHandoverAuthorizationTest {

    @Mock private OpHandoverRepository opHandoverRepository;
    @Mock private BookingDetailRepository bookingDetailRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private CreateInteractionTimelineUseCase createInteractionTimelineUseCase;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ActivityLogPublisher activityLogPublisher;

    private UpdateHandoverUseCase useCase;
    private UUID handoverId;
    private UserEntity creator;

    @BeforeEach
    void setUp() {
        useCase = new UpdateHandoverUseCase(
                opHandoverRepository, bookingDetailRepository, paymentRepository,
                notificationRepository, userRepository, createInteractionTimelineUseCase,
                new HandoverAccessPolicy(currentUserProvider),
                new HandoverEditPolicy("Asia/Ho_Chi_Minh"),
                activityLogPublisher, new ObjectMapper());
        handoverId = UUID.randomUUID();
        creator = user("SALES");
        when(bookingDetailRepository.findByBooking_BookingId(any())).thenReturn(List.of());
        when(paymentRepository.findByBooking_BookingId(any())).thenReturn(List.of());
    }

    private UserEntity user(String role) {
        return UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName(role + " user")
                .role(RoleEntity.builder().roleName(role).build())
                .build();
    }

    private OpHandoverEntity handover(BookingStatus bookingStatus, UserEntity bookingAssignee) {
        return handover(bookingStatus, bookingAssignee, null);
    }

    private OpHandoverEntity handover(BookingStatus bookingStatus, UserEntity bookingAssignee,
                                      ReadinessStatus readiness) {
        return handover(bookingStatus, bookingAssignee, readiness, LocalDate.now().plusDays(3));
    }

    private OpHandoverEntity handover(BookingStatus bookingStatus, UserEntity bookingAssignee,
                                      ReadinessStatus readiness, LocalDate checkInDate) {
        OpHandoverEntity h = OpHandoverEntity.builder()
                .handoverId(handoverId)
                .createdBy(creator)
                .status(HandoverStatus.DRAFT)
                .readinessStatus(readiness)
                .booking(BookingEntity.builder()
                        .bookingId(UUID.randomUUID())
                        .bookingCode("BK-TEST")
                        .status(bookingStatus)
                        .checkInDate(checkInDate)
                        .assignedUser(bookingAssignee)
                        .build())
                .build();
        when(opHandoverRepository.findById(handoverId)).thenReturn(Optional.of(h));
        when(opHandoverRepository.save(any(OpHandoverEntity.class))).thenAnswer(i -> i.getArgument(0));
        return h;
    }

    /** A DRAFT save — the least demanding valid request, so only authorization decides. */
    private UpdateHandoverRequest draftRequest() {
        UpdateHandoverRequest r = new UpdateHandoverRequest();
        r.setStatus("DRAFT");
        r.setOperationalNotes("note");
        return r;
    }

    // ------------------------------------------------------------------ fail closed

    @Test
    @DisplayName("A null actor is refused, not waved through")
    void nullActorIsRefused() {
        handover(BookingStatus.CONFIRMED, null);

        assertThatThrownBy(() -> useCase.execute(handoverId, draftRequest(), null))
                .isInstanceOf(AccessDeniedException.class);

        verify(opHandoverRepository, never()).save(any());
    }

    @Test
    @DisplayName("An actor with no role is refused — this used to skip the check entirely")
    void rolelessActorIsRefused() {
        handover(BookingStatus.CONFIRMED, null);
        UserEntity roleless = UserEntity.builder().userId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> useCase.execute(handoverId, draftRequest(), roleless))
                .isInstanceOf(AccessDeniedException.class);

        verify(opHandoverRepository, never()).save(any());
    }

    @Test
    void anotherRepIsRefused() {
        handover(BookingStatus.CONFIRMED, null);

        assertThatThrownBy(() -> useCase.execute(handoverId, draftRequest(), user("SALES")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Front Office cannot write through the operational endpoint")
    void frontOfficeIsRefused() {
        handover(BookingStatus.CONFIRMED, null);

        assertThatThrownBy(() -> useCase.execute(handoverId, draftRequest(), user("FO")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ allowed

    @Test
    void creatorMayUpdate() {
        handover(BookingStatus.CONFIRMED, null);

        assertThatCode(() -> useCase.execute(handoverId, draftRequest(), creator))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A Manager may update — the old check refused a record they can read")
    void managerMayUpdate() {
        handover(BookingStatus.CONFIRMED, null);

        assertThatCode(() -> useCase.execute(handoverId, draftRequest(), user("MANAGER")))
                .doesNotThrowAnyException();
    }

    @Test
    void adminMayUpdate() {
        handover(BookingStatus.CONFIRMED, null);

        assertThatCode(() -> useCase.execute(handoverId, draftRequest(), user("ADMIN")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("The rep the booking is assigned to may update a colleague's handover")
    void bookingAssigneeMayUpdate() {
        UserEntity assignee = user("RESERVATION");
        handover(BookingStatus.CONFIRMED, assignee);

        assertThatCode(() -> useCase.execute(handoverId, draftRequest(), assignee))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ BR-44

    @ParameterizedTest(name = "BR-44: a {0} booking blocks the update")
    @CsvSource({"CANCELLED", "REJECTED", "CHECKED_OUT", "NO_SHOW", "PENDING"})
    @DisplayName("The old blacklist missed NO_SHOW and PENDING")
    void deadBookingBlocksUpdate(BookingStatus bookingStatus) {
        handover(bookingStatus, null);

        assertThatThrownBy(() -> useCase.execute(handoverId, draftRequest(), creator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(bookingStatus.name());

        verify(opHandoverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Once the guest has checked in, Sales can no longer rewrite the arrival prep")
    void checkedInBookingBlocksUpdate() {
        handover(BookingStatus.CHECKED_IN, null);

        assertThatThrownBy(() -> useCase.execute(handoverId, draftRequest(), creator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHECKED_IN");

        verify(opHandoverRepository, never()).save(any());
    }

    // ------------------------------------------------------ the clarification loop stays closed

    @Test
    @DisplayName("A checked-in booking still accepts the answer to a Front Office clarification")
    void checkedInBookingStillAcceptsAClarificationAnswer() {
        // Front Office may raise NEED_CLARIFICATION on a CHECKED_IN booking (their guard is the
        // wider LIVE_FOR_ARRIVAL) and READINESS_TRANSITIONS lets that state go only to itself, so
        // refusing the reply here would strand the handover where neither desk could clear it.
        handover(BookingStatus.CHECKED_IN, null, ReadinessStatus.NEED_CLARIFICATION);

        assertThatCode(() -> useCase.execute(handoverId, draftRequest(), creator))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("On the arrival day itself the reply is still accepted — the date gate is not yet closed")
    void arrivalDayStillAcceptsAClarificationAnswer() {
        handover(BookingStatus.CHECKED_IN, null, ReadinessStatus.NEED_CLARIFICATION, LocalDate.now());

        assertThatCode(() -> useCase.execute(handoverId, draftRequest(), creator))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BR-26: once the arrival date has passed the block is absolute — clarification included")
    void pastArrivalRefusesEvenAClarificationAnswer() {
        // A decided trade-off, not an oversight: a clarification raised on the arrival day can no
        // longer be answered the next day, so that handover stays in NEED_CLARIFICATION until
        // CloseFinishedHandoversUseCase closes it at check-out.
        handover(BookingStatus.CONFIRMED, null, ReadinessStatus.NEED_CLARIFICATION,
                LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> useCase.execute(handoverId, draftRequest(), creator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has passed");

        verify(opHandoverRepository, never()).save(any());
    }

    @ParameterizedTest(name = "a {0} booking refuses the reply too — there is no arrival left")
    @CsvSource({"CANCELLED", "REJECTED", "CHECKED_OUT", "NO_SHOW", "PENDING"})
    @DisplayName("The clarification exception is bounded by LIVE_FOR_ARRIVAL, not open-ended")
    void deadBookingRefusesEvenAClarificationAnswer(BookingStatus bookingStatus) {
        handover(bookingStatus, null, ReadinessStatus.NEED_CLARIFICATION);

        assertThatThrownBy(() -> useCase.execute(handoverId, draftRequest(), creator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(bookingStatus.name());

        verify(opHandoverRepository, never()).save(any());
    }
}
