package com.novax.leadora.unit.handover;
import com.novax.leadora.application.usecase.handover.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novax.leadora.api.dto.request.CreateInteractionTimelineRequest;
import com.novax.leadora.api.dto.request.UpdateReadinessStatusRequest;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.timeline.CreateInteractionTimelineUseCase;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-22.3 — the readiness workflow and the guards around it.
 *
 * <p>The interesting cases are the two that a whitelist of target values alone let through:
 * POST-4 (Front Office must not walk itself out of NEED_CLARIFICATION) and BR-44 (a dead
 * booking freezes readiness).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateHandoverReadinessUseCaseTest {

    @Mock
    private OpHandoverRepository opHandoverRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ActivityLogPublisher activityLogPublisher;

    @Mock
    private CreateInteractionTimelineUseCase createInteractionTimelineUseCase;

    private UpdateHandoverReadinessUseCase useCase;

    private UUID handoverId;
    private UserEntity actor;

    @BeforeEach
    void setUp() {
        useCase = new UpdateHandoverReadinessUseCase(
                opHandoverRepository, notificationRepository, activityLogPublisher, new ObjectMapper(),
                createInteractionTimelineUseCase);
        handoverId = UUID.randomUUID();
        actor = UserEntity.builder().userId(UUID.randomUUID()).fullName("FO Desk").build();
    }

    /** A submitted handover on a live booking, sitting at {@code from}. */
    private OpHandoverEntity handoverAt(ReadinessStatus from, BookingStatus bookingStatus) {
        BookingEntity booking = BookingEntity.builder()
                .bookingId(UUID.randomUUID())
                .bookingCode("BK-001")
                .status(bookingStatus)
                .checkInDate(LocalDate.now().plusDays(2))
                .build();
        OpHandoverEntity handover = OpHandoverEntity.builder()
                .handoverId(handoverId)
                .booking(booking)
                .status(HandoverStatus.SUBMITTED)
                .readinessStatus(from)
                .createdBy(UserEntity.builder().userId(UUID.randomUUID()).fullName("Sales Rep").build())
                .build();
        when(opHandoverRepository.findById(handoverId)).thenReturn(Optional.of(handover));
        when(opHandoverRepository.save(any(OpHandoverEntity.class))).thenAnswer(i -> i.getArgument(0));
        return handover;
    }

    private UpdateReadinessStatusRequest request(String readiness, String note) {
        UpdateReadinessStatusRequest r = new UpdateReadinessStatusRequest();
        r.setReadinessStatus(readiness);
        r.setClarificationNote(note);
        return r;
    }

    // ------------------------------------------------------------------ allowed transitions

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "PENDING_REVIEW,     REVIEWED",
            "PENDING_REVIEW,     NEED_CLARIFICATION",
            "REVIEWED,           READY_FOR_ARRIVAL",
            "REVIEWED,           NEED_CLARIFICATION",
            "READY_FOR_ARRIVAL,  NEED_CLARIFICATION",
            // self-transitions keep a retried request idempotent instead of a 422
            "REVIEWED,           REVIEWED",
            "READY_FOR_ARRIVAL,  READY_FOR_ARRIVAL",
            "NEED_CLARIFICATION, NEED_CLARIFICATION",
    })
    void allowsValidTransition(ReadinessStatus from, ReadinessStatus to) {
        handoverAt(from, BookingStatus.CONFIRMED);

        var response = useCase.execute(handoverId, request(to.name(), "needs a crib"), actor);

        assertThat(response.getReadinessStatus()).isEqualTo(to.name());
    }

    // ------------------------------------------------------------------ POST-4

    @ParameterizedTest(name = "POST-4: NEED_CLARIFICATION -> {0} is refused")
    @CsvSource({"REVIEWED", "READY_FOR_ARRIVAL"})
    @DisplayName("POST-4 — Front Office cannot confirm readiness while waiting on Sales")
    void blocksEscapeFromNeedClarification(ReadinessStatus to) {
        handoverAt(ReadinessStatus.NEED_CLARIFICATION, BookingStatus.CONFIRMED);

        assertThatThrownBy(() -> useCase.execute(handoverId, request(to.name(), null), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("re-submit");

        verify(opHandoverRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST-4 — but the clarification note itself can still be amended")
    void allowsAmendingTheClarificationNote() {
        handoverAt(ReadinessStatus.NEED_CLARIFICATION, BookingStatus.CONFIRMED);

        var response = useCase.execute(
                handoverId, request("NEED_CLARIFICATION", "  which room type exactly?  "), actor);

        assertThat(response.getReadinessStatus()).isEqualTo("NEED_CLARIFICATION");
        assertThat(response.getClarificationNote()).isEqualTo("which room type exactly?");
    }

    // ------------------------------------------------------------------ skipping review

    @Test
    @DisplayName("A room cannot be declared ready before the handover has been reviewed")
    void blocksSkippingReview() {
        handoverAt(ReadinessStatus.PENDING_REVIEW, BookingStatus.CONFIRMED);

        assertThatThrownBy(() ->
                useCase.execute(handoverId, request("READY_FOR_ARRIVAL", null), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_REVIEW");

        verify(opHandoverRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ BR-44

    @ParameterizedTest(name = "BR-44: a {0} booking freezes readiness")
    @CsvSource({"CANCELLED", "REJECTED", "NO_SHOW", "CHECKED_OUT", "PENDING"})
    void blocksUpdateOnADeadBooking(BookingStatus bookingStatus) {
        handoverAt(ReadinessStatus.REVIEWED, bookingStatus);

        assertThatThrownBy(() ->
                useCase.execute(handoverId, request("READY_FOR_ARRIVAL", null), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can no longer be updated");

        verify(opHandoverRepository, never()).save(any());
    }

    @Test
    @DisplayName("A guest who has already checked in can still be worked on")
    void allowsUpdateWhileCheckedIn() {
        handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CHECKED_IN);

        var response = useCase.execute(handoverId, request("READY_FOR_ARRIVAL", null), actor);

        assertThat(response.getReadinessStatus()).isEqualTo("READY_FOR_ARRIVAL");
    }

    // ------------------------------------------------------------------ pre-existing guards

    @Test
    @DisplayName("PRE-3 — a DRAFT has not been sent to the Front Office yet")
    void blocksDraft() {
        OpHandoverEntity handover = handoverAt(ReadinessStatus.PENDING_REVIEW, BookingStatus.CONFIRMED);
        handover.setStatus(HandoverStatus.DRAFT);

        assertThatThrownBy(() -> useCase.execute(handoverId, request("REVIEWED", null), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not been sent");
    }

    @Test
    @DisplayName("E7.2 — NEED_CLARIFICATION without a note is refused")
    void requiresClarificationNote() {
        handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);

        assertThatThrownBy(() ->
                useCase.execute(handoverId, request("NEED_CLARIFICATION", "   "), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Clarification note is required");

        verify(opHandoverRepository, never()).save(any());
    }

    @ParameterizedTest(name = "E7.3: Front Office cannot set {0}")
    @CsvSource({"PENDING_REVIEW", "SOMETHING_ELSE", "''"})
    void rejectsNonSettableValues(String raw) {
        handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);

        assertThatThrownBy(() -> useCase.execute(handoverId, request(raw, null), actor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid readiness status");
    }

    // ------------------------------------------------------------------ side effects

    @Test
    @DisplayName("Step 9 / POST-3 — asking for clarification notifies the originating Sales user")
    void notifiesSalesOnClarification() {
        handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);

        useCase.execute(handoverId, request("NEED_CLARIFICATION", "no cot available?"), actor);

        verify(notificationRepository).save(any());
    }

    @Test
    @DisplayName("Confirming readiness does not notify anybody")
    void doesNotNotifyOnReadyForArrival() {
        handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);

        useCase.execute(handoverId, request("READY_FOR_ARRIVAL", null), actor);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Amending the note does not notify again — a few typo fixes were a stack of alerts")
    void doesNotRenotifyWhenOnlyTheNoteChanges() {
        handoverAt(ReadinessStatus.NEED_CLARIFICATION, BookingStatus.CONFIRMED);

        useCase.execute(handoverId, request("NEED_CLARIFICATION", "clearer wording"), actor);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST-3 falls back to the booking owner when created_by is null")
    void notifiesBookingOwnerWhenCreatorIsMissing() {
        OpHandoverEntity handover = handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);
        handover.setCreatedBy(null);
        UserEntity bookingOwner = UserEntity.builder()
                .userId(UUID.randomUUID()).fullName("Booking Owner").build();
        handover.getBooking().setAssignedUser(bookingOwner);

        useCase.execute(handoverId, request("NEED_CLARIFICATION", "who owns this?"), actor);

        ArgumentCaptor<NotificationEntity> sent = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(sent.capture());
        assertThat(sent.getValue().getUser().getUserId()).isEqualTo(bookingOwner.getUserId());
    }

    @Test
    @DisplayName("With no recipient at all the readiness still saves — but nothing is silently lost")
    void savesButSkipsNotificationWhenNobodyCanBeNotified() {
        OpHandoverEntity handover = handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);
        handover.setCreatedBy(null);
        handover.getBooking().setAssignedUser(null);

        // The business operation must not fail because there is no one to tell; the use case logs
        // a warning instead of the old silent `return`.
        var response = useCase.execute(handoverId, request("NEED_CLARIFICATION", "orphan"), actor);

        assertThat(response.getReadinessStatus()).isEqualTo("NEED_CLARIFICATION");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("BR-37 / POST-2 — the audit row carries the old value, not just the new one")
    void publishesAuditRowWithBothValues() {
        handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);

        useCase.execute(handoverId, request("READY_FOR_ARRIVAL", null), actor);

        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(activityLogPublisher).publish(
                org.mockito.ArgumentMatchers.eq(ActivityLogType.HANDOVER_READINESS_UPDATED),
                org.mockito.ArgumentMatchers.eq(EntityType.HANDOVER),
                org.mockito.ArgumentMatchers.eq(handoverId),
                org.mockito.ArgumentMatchers.contains("REVIEWED -> READY_FOR_ARRIVAL"),
                payload.capture());
        assertThat(payload.getValue().get("previousReadiness").asText()).isEqualTo("REVIEWED");
        assertThat(payload.getValue().get("newReadiness").asText()).isEqualTo("READY_FOR_ARRIVAL");
        assertThat(payload.getValue().get("bookingCode").asText()).isEqualTo("BK-001");
    }

    @Test
    @DisplayName("A failing audit write must not fail the business operation")
    void auditFailureDoesNotBreakTheUpdate() {
        handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);
        org.mockito.Mockito.doThrow(new RuntimeException("audit sink down"))
                .when(activityLogPublisher).publish(any(), any(), any(), any(), any());

        var response = useCase.execute(handoverId, request("READY_FOR_ARRIVAL", null), actor);

        assertThat(response.getReadinessStatus()).isEqualTo("READY_FOR_ARRIVAL");
    }

    // ------------------------------------------------------------------ POST-6 timeline

    @Test
    @DisplayName("POST-6 — the readiness change lands on the interaction timeline, like the Sales half")
    void recordsTheReadinessChangeOnTheTimeline() {
        handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);

        useCase.execute(handoverId, request("READY_FOR_ARRIVAL", null), actor);

        ArgumentCaptor<CreateInteractionTimelineRequest> entry =
                ArgumentCaptor.forClass(CreateInteractionTimelineRequest.class);
        verify(createInteractionTimelineUseCase).execute(entry.capture());
        assertThat(entry.getValue().getType()).isEqualTo("HANDOVER_READINESS");
        assertThat(entry.getValue().getDescription())
                .contains("BK-001")
                .contains("REVIEWED -> READY_FOR_ARRIVAL");
    }

    @Test
    @DisplayName("A throwing timeline write is swallowed on the no-transaction path")
    void timelineFailureIsSwallowed() {
        // Scope note: with no transaction synchronization active these mocks exercise the direct
        // fallback branch only. The guarantee that a timeline failure cannot roll the readiness
        // update back comes from running the write in afterCommit, which no unit test with a
        // mocked collaborator can observe — it needs a @SpringBootTest with a real transaction.
        handoverAt(ReadinessStatus.REVIEWED, BookingStatus.CONFIRMED);
        org.mockito.Mockito.doThrow(new RuntimeException("timeline down"))
                .when(createInteractionTimelineUseCase).execute(any());

        var response = useCase.execute(handoverId, request("READY_FOR_ARRIVAL", null), actor);

        assertThat(response.getReadinessStatus()).isEqualTo("READY_FOR_ARRIVAL");
    }

    @ParameterizedTest(name = "a {0} -> {0} retry writes no timeline entry")
    @CsvSource({"REVIEWED", "READY_FOR_ARRIVAL", "NEED_CLARIFICATION"})
    @DisplayName("A self-transition is a retry or a note edit, not a readiness change")
    void doesNotRecordSelfTransitionsOnTheTimeline(ReadinessStatus state) {
        handoverAt(state, BookingStatus.CONFIRMED);

        useCase.execute(handoverId, request(state.name(), "still unclear"), actor);

        verify(createInteractionTimelineUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("A legacy null readiness reads as PENDING_REVIEW on the timeline, not \"null\"")
    void normalisesNullPreviousReadinessOnTheTimeline() {
        handoverAt(null, BookingStatus.CONFIRMED);

        useCase.execute(handoverId, request("REVIEWED", null), actor);

        ArgumentCaptor<CreateInteractionTimelineRequest> entry =
                ArgumentCaptor.forClass(CreateInteractionTimelineRequest.class);
        verify(createInteractionTimelineUseCase).execute(entry.capture());
        assertThat(entry.getValue().getDescription())
                .contains("PENDING_REVIEW -> REVIEWED")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("A legacy row with no readiness value behaves as freshly submitted")
    void treatsNullReadinessAsPendingReview() {
        handoverAt(null, BookingStatus.CONFIRMED);

        var response = useCase.execute(handoverId, request("REVIEWED", null), actor);

        assertThat(response.getReadinessStatus()).isEqualTo("REVIEWED");
    }
}
