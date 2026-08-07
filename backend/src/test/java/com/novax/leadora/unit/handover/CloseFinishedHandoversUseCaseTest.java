package com.novax.leadora.unit.handover;
import com.novax.leadora.application.usecase.handover.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Bug G — the handover lifecycle previously had no terminal state. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloseFinishedHandoversUseCaseTest {

    @Mock private OpHandoverRepository opHandoverRepository;
    @Mock private ActivityLogPublisher activityLogPublisher;

    private CloseFinishedHandoversUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CloseFinishedHandoversUseCase(
                opHandoverRepository, activityLogPublisher, new ObjectMapper());
    }

    private OpHandoverEntity handover(HandoverStatus status, BookingStatus bookingStatus) {
        return OpHandoverEntity.builder()
                .handoverId(UUID.randomUUID())
                .status(status)
                .booking(BookingEntity.builder()
                        .bookingId(UUID.randomUUID())
                        .bookingCode("BK-X")
                        .status(bookingStatus)
                        .build())
                .build();
    }

    @Test
    @DisplayName("A finished arrival is closed and audited")
    void closesFinishedHandovers() {
        OpHandoverEntity ready = handover(HandoverStatus.READY, BookingStatus.CHECKED_OUT);
        when(opHandoverRepository.findFinishedButNotClosed(any())).thenReturn(List.of(ready));

        int closed = useCase.execute();

        assertThat(closed).isEqualTo(1);
        assertThat(ready.getStatus()).isEqualTo(HandoverStatus.CLOSED);
        verify(opHandoverRepository).save(ready);
        verify(activityLogPublisher).publish(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Nothing to close is not an error and writes nothing")
    void doesNothingWhenNoneFinished() {
        when(opHandoverRepository.findFinishedButNotClosed(any())).thenReturn(List.of());

        assertThat(useCase.execute()).isZero();

        verify(opHandoverRepository, never()).save(any());
        verify(activityLogPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("The query is asked for exactly the live-arrival whitelist, not a hand-typed list")
    void queriesUsingTheSharedWhitelist() {
        when(opHandoverRepository.findFinishedButNotClosed(any())).thenReturn(List.of());

        useCase.execute();

        // CHECKED_IN must be on the "still live" side: a guest at the desk is not a finished
        // arrival, and closing at check-in would clear the handover while it is still needed.
        verify(opHandoverRepository).findFinishedButNotClosed(BookingStatus.LIVE_FOR_ARRIVAL);
        assertThat(BookingStatus.LIVE_FOR_ARRIVAL)
                .containsExactlyInAnyOrder(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);
    }

    @Test
    @DisplayName("A failing audit write does not stop the sweep")
    void auditFailureDoesNotStopClosing() {
        OpHandoverEntity a = handover(HandoverStatus.READY, BookingStatus.CANCELLED);
        OpHandoverEntity b = handover(HandoverStatus.ACKNOWLEDGED, BookingStatus.NO_SHOW);
        when(opHandoverRepository.findFinishedButNotClosed(any())).thenReturn(List.of(a, b));
        doThrow(new RuntimeException("audit sink down"))
                .when(activityLogPublisher).publish(any(), any(), any(), any(), any());

        assertThat(useCase.execute()).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo(HandoverStatus.CLOSED);
        assertThat(b.getStatus()).isEqualTo(HandoverStatus.CLOSED);
    }

    @Test
    @DisplayName("CLOSED is outside the statuses the arrival desk treats as live")
    void closedIsNotALiveArrivalStatus() {
        assertThat(Set.of(HandoverStatus.values())).contains(HandoverStatus.CLOSED);
        assertThat(HandoverStatus.CLOSED).isNotIn(
                HandoverStatus.DRAFT, HandoverStatus.SUBMITTED,
                HandoverStatus.ACKNOWLEDGED, HandoverStatus.READY);
    }
}
