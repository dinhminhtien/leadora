package com.novax.leadora.application.usecase.handover;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.api.dto.request.CreateInteractionTimelineRequest;
import com.novax.leadora.api.dto.request.UpdateReadinessStatusRequest;
import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.timeline.CreateInteractionTimelineUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UC-22.3 — Update Handover Readiness Status (Front Office).
 *
 * <p>Per BR-27, Front Office may update arrival readiness ONLY — it must not touch the booking
 * confirmation, quotation approval or deal value. So this use case changes nothing but
 * {@code readiness_status} (+ clarification note and the Sales→FO lifecycle markers that follow):
 * the first FO action acknowledges the handover, READY_FOR_ARRIVAL marks the room prepared, and
 * NEED_CLARIFICATION (with a required note) notifies the originating Sales/Reservation user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateHandoverReadinessUseCase {

    /** The readiness values a Front Office Staff is allowed to set (UC-22.3, step 4 / E7.3). */
    private static final Set<ReadinessStatus> FO_SETTABLE = EnumSet.of(
            ReadinessStatus.REVIEWED, ReadinessStatus.READY_FOR_ARRIVAL, ReadinessStatus.NEED_CLARIFICATION);

    /**
     * The readiness workflow (UC-22.3 step 7: "validates ... workflow transition").
     *
     * <p>A whitelist of target values alone is not enough. Two things must be impossible:
     * <ul>
     *   <li><b>POST-4</b> — once readiness is NEED_CLARIFICATION the handover "requires Sales or
     *       Reservation update before final readiness confirmation", so Front Office must not be
     *       able to walk itself back to REVIEWED / READY_FOR_ARRIVAL. Only Sales re-submitting
     *       (UC-20.4, which resets readiness to PENDING_REVIEW) reopens the path.</li>
     *   <li><b>skipping review</b> — PENDING_REVIEW must not jump straight to READY_FOR_ARRIVAL;
     *       confirming a room is ready without having reviewed the handover is the whole thing
     *       this screen exists to prevent.</li>
     * </ul>
     *
     * <p>Each state maps to itself so a retried request (network blip, double click) is idempotent
     * rather than a 422 — and so Front Office can amend the clarification note without having to
     * leave NEED_CLARIFICATION, which POST-4 does not forbid.
     */
    private static final Map<ReadinessStatus, Set<ReadinessStatus>> ALLOWED_TRANSITIONS = Map.of(
            ReadinessStatus.PENDING_REVIEW, EnumSet.of(
                    ReadinessStatus.REVIEWED, ReadinessStatus.NEED_CLARIFICATION),
            ReadinessStatus.REVIEWED, EnumSet.of(
                    ReadinessStatus.REVIEWED, ReadinessStatus.READY_FOR_ARRIVAL,
                    ReadinessStatus.NEED_CLARIFICATION),
            ReadinessStatus.READY_FOR_ARRIVAL, EnumSet.of(
                    ReadinessStatus.READY_FOR_ARRIVAL, ReadinessStatus.NEED_CLARIFICATION),
            ReadinessStatus.NEED_CLARIFICATION, EnumSet.of(
                    ReadinessStatus.NEED_CLARIFICATION));

    private final OpHandoverRepository opHandoverRepository;
    private final NotificationRepository notificationRepository;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;
    private final CreateInteractionTimelineUseCase createInteractionTimelineUseCase;

    @Transactional
    public ArrivalHandoverResponse execute(UUID handoverId, UpdateReadinessStatusRequest request, UserEntity actor) {
        OpHandoverEntity handover = opHandoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Arrival handover", handoverId));

        // PRE-3: only handovers already sent to Front Office can be updated.
        if (handover.getStatus() == HandoverStatus.DRAFT) {
            throw new IllegalStateException("The handover has not been sent to the Front Office yet.");
        }

        // BR-44 — a cancelled / rejected / no-show / checked-out booking is closed. Preparing a
        // room for it is meaningless, so the readiness of its handover is frozen.
        BookingEntity booking = handover.getBooking();
        if (booking != null && !BookingStatus.LIVE_FOR_ARRIVAL.contains(booking.getStatus())) {
            throw new IllegalStateException(
                    "This booking is " + booking.getStatus()
                            + ", so its arrival readiness can no longer be updated.");
        }

        ReadinessStatus previousReadiness = handover.getReadinessStatus();
        ReadinessStatus newReadiness = parseReadiness(request.getReadinessStatus());
        assertTransitionAllowed(previousReadiness, newReadiness);

        // E7.2 — clarification note is required when asking for clarification.
        String note = request.getClarificationNote();
        if (newReadiness == ReadinessStatus.NEED_CLARIFICATION && !StringUtils.hasText(note)) {
            throw new IllegalStateException("Clarification note is required.");
        }

        handover.setReadinessStatus(newReadiness);
        handover.setClarificationNote(
                newReadiness == ReadinessStatus.NEED_CLARIFICATION ? note.trim() : null);
        handover.setUpdatedBy(actor);

        // Stamp the acknowledgement once, on the first FO action.
        if (handover.getAcknowledgedAt() == null) {
            handover.setAcknowledgedAt(OffsetDateTime.now());
        }
        // Keep the Sales→FO status in sync with the FO readiness:
        //  READY_FOR_ARRIVAL → READY (room prepared); otherwise the FO has it in hand → ACKNOWLEDGED
        //  (so a handover sent back to NEED_CLARIFICATION is no longer shown as READY).
        handover.setStatus(newReadiness == ReadinessStatus.READY_FOR_ARRIVAL
                ? HandoverStatus.READY
                : HandoverStatus.ACKNOWLEDGED);

        OpHandoverEntity saved = opHandoverRepository.save(handover);

        // Step 9 / POST-3 — notify the originating Sales/Reservation user on NEED_CLARIFICATION.
        // Only on *entering* the state: amending the note is still a NEED_CLARIFICATION write, and
        // notifying again each time turned a couple of typo fixes into a stack of identical alerts.
        if (newReadiness == ReadinessStatus.NEED_CLARIFICATION
                && previousReadiness != ReadinessStatus.NEED_CLARIFICATION) {
            notifyClarificationNeeded(saved, actor);
        }

        // Whether this write touched an arrival somebody else is responsible for.
        //
        // The desk is deliberately not locked to the assignee: a front desk is a shift rota, and
        // whoever is on duty when the guest walks in has to be able to act. Enforcing ownership
        // here would strand an arrival whose assignee is off shift, and the only way out —
        // Sales reassigning it — resets the readiness to PENDING_REVIEW and throws away the review
        // work already done (UpdateHandoverUseCase, re-submit branch).
        //
        // So this stays a matter of professional conduct rather than a permission. What the code
        // owes in return is a trail: the fact that A edited B's arrival must be answerable later,
        // not reconstructed from guesswork. Recorded in the activity row (queryable) and raised to
        // WARN in the log (greppable) — the plain UpdatedBy field cannot show it, because it names
        // the actor without ever naming who was supposed to act.
        UUID assignedFoUserId = saved.getAssignedFoUserId();
        boolean actedOnAnotherDesk = assignedFoUserId != null
                && actor != null
                && !assignedFoUserId.equals(actor.getUserId());

        // POST-2 / BR-37 — a queryable audit row, not just a line in the log file. The old value
        // matters as much as the new one: without it the trail cannot show what actually changed.
        // Wrapped because an audit write must never turn a completed business operation into an
        // error response, which is how every other publisher in the codebase treats it.
        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("bookingCode", booking != null ? booking.getBookingCode() : null)
                    .put("previousReadiness", previousReadiness != null ? previousReadiness.name() : null)
                    .put("newReadiness", newReadiness.name())
                    .put("handoverStatus", saved.getStatus() != null ? saved.getStatus().name() : null)
                    .put("assignedFoUserId", assignedFoUserId != null ? assignedFoUserId.toString() : null)
                    .put("updatedByAssignee", !actedOnAnotherDesk);
            if (newReadiness == ReadinessStatus.NEED_CLARIFICATION) {
                payload.put("clarificationNote", saved.getClarificationNote());
            }
            activityLogPublisher.publish(
                    ActivityLogType.HANDOVER_READINESS_UPDATED,
                    EntityType.HANDOVER,
                    saved.getHandoverId(),
                    "Arrival readiness " + previousReadiness + " -> " + newReadiness
                            + (actedOnAnotherDesk ? " (updated by someone other than the assignee)" : ""),
                    payload);
        } catch (Exception e) {
            log.warn("Failed to publish handover readiness activity: {}", e.getMessage());
        }

        // BR-37 — same shape as the Sales-side handover log lines, so one grep for "[AUDIT] Action:"
        // returns both halves of a handover instead of only the half Sales wrote.
        log.info("[AUDIT] Action: UPDATE_HANDOVER_READINESS, TargetRecord: {}, OldReadiness: {}, NewReadiness: {}, Status: {}, AssignedTo: {}, UpdatedBy: {}, Timestamp: {}",
                saved.getHandoverId(), previousReadiness, newReadiness, saved.getStatus(),
                assignedFoUserId, actor != null ? actor.getUserId() : null, OffsetDateTime.now());

        // WARN, not INFO: this is the line somebody goes looking for when a shift disputes who
        // changed an arrival, so it has to stand out from the ordinary readiness traffic.
        if (actedOnAnotherDesk) {
            log.warn("[AUDIT] Action: UPDATE_HANDOVER_READINESS_ON_ANOTHER_DESK, TargetRecord: {}, AssignedTo: {}, UpdatedBy: {}, OldReadiness: {}, NewReadiness: {}, Timestamp: {}",
                    saved.getHandoverId(), assignedFoUserId, actor.getUserId(),
                    previousReadiness, newReadiness, OffsetDateTime.now());
        }

        // POST-6 — the Front Office half of the handover has to reach the customer's interaction
        // timeline too. Sales writes HANDOVER_SUBMISSION there when it hands over (UC-20.4); without
        // this the timeline showed the handover leaving Sales and then nothing, so "what happened to
        // this arrival?" could not be answered from the customer's own history.
        //
        // Deferred to after commit rather than wrapped in a try/catch the way the Sales side does
        // it — see recordOnTimeline for why that try/catch does not actually hold.
        recordOnTimeline(saved, previousReadiness, newReadiness, actor);

        return ArrivalHandoverResponse.from(saved);
    }

    /**
     * POST-6 — mirror of the Sales-side timeline write in {@code UpdateHandoverUseCase}: same
     * association resolution (deal via the booking's quotation, plus the customer), same
     * association resolution (deal via the booking's quotation, plus the customer), so the two
     * halves of a handover read as one thread of activity.
     *
     * <p><b>Why the write is deferred to after commit rather than wrapped in a try/catch.</b>
     * {@code CreateInteractionTimelineUseCase.execute} is {@code @Transactional} with the default
     * REQUIRED propagation, so calling it from inside this transaction makes it <em>join</em> this
     * one. A RuntimeException in there — a customer row that no longer resolves, an unresolvable
     * current user, any constraint violation — makes Spring mark the shared transaction
     * rollback-only. Catching it here swallows the exception but not the mark: the commit then
     * fails with {@code UnexpectedRollbackException}, and the readiness update the Front Office
     * just made is silently rolled back behind a 500. A try/catch alone does not buy best-effort
     * semantics; it only hides where the failure came from.
     *
     * <p>Registering an {@code afterCommit} callback gives the property the catch was reaching for:
     * the timeline write runs in its own fresh transaction once readiness is durably committed, so
     * it can neither roll the update back nor leave a timeline entry for an update that never
     * landed. The request object is still built <em>inside</em> the transaction, because reading
     * {@code booking.getQuotation().getDeal()} after commit would hit a detached proxy.
     *
     * <p>The direct-call fallback is for callers with no transaction synchronization active — unit
     * tests, and any future caller outside a transaction — where there is nothing to defer past.
     *
     * <p>The type is {@code HANDOVER_READINESS}, paired with the Sales side's
     * {@code HANDOVER_SUBMISSION}. Both are system-written values that the manual-logging endpoint
     * (UC-13.4) does not offer, which is why neither appears in
     * {@code CreateInteractionTimelineRequest}'s {@code @Pattern} — that constraint guards the REST
     * surface and is not evaluated on this in-process call.
     */
    private void recordOnTimeline(OpHandoverEntity handover, ReadinessStatus previousReadiness,
                                  ReadinessStatus newReadiness, UserEntity actor) {
        BookingEntity booking = handover.getBooking();
        if (booking == null) {
            return; // nothing to hang the entry off — booking_id is NOT NULL, so this is defensive
        }

        // Same normalization assertTransitionAllowed uses: a row predating the readiness column
        // behaves as freshly submitted, so the customer's history reads "PENDING_REVIEW -> REVIEWED"
        // rather than "null -> REVIEWED".
        ReadinessStatus from = previousReadiness != null ? previousReadiness : ReadinessStatus.PENDING_REVIEW;

        // ALLOWED_TRANSITIONS maps every state to itself so a double click or a retried request is
        // idempotent, and so a clarification note can be amended without leaving the state. Neither
        // is a readiness *change*, so neither belongs in the customer's timeline — three note edits
        // would otherwise read as three "changed NEED_CLARIFICATION -> NEED_CLARIFICATION" entries.
        // The amendment is still captured: the activity log row above records every write.
        if (from == newReadiness) {
            return;
        }

        CreateInteractionTimelineRequest timelineReq = new CreateInteractionTimelineRequest();
        timelineReq.setType("HANDOVER_READINESS");
        timelineReq.setDescription("Arrival readiness for booking " + booking.getBookingCode()
                + " changed " + from + " -> " + newReadiness
                + (newReadiness == ReadinessStatus.NEED_CLARIFICATION
                        ? ": " + handover.getClarificationNote() : "")
                + (actor != null && actor.getFullName() != null ? " by " + actor.getFullName() : ""));
        timelineReq.setOccurredAt(OffsetDateTime.now());
        if (booking.getQuotation() != null && booking.getQuotation().getDeal() != null) {
            timelineReq.setDealId(booking.getQuotation().getDeal().getDealId());
        }
        if (booking.getCustomer() != null) {
            timelineReq.setCustomerId(booking.getCustomer().getCustomerId());
        }

        UUID handoverIdForLog = handover.getHandoverId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    writeTimelineQuietly(timelineReq, handoverIdForLog);
                }
            });
        } else {
            writeTimelineQuietly(timelineReq, handoverIdForLog);
        }
    }

    /**
     * Never throws. Spring propagates an exception raised in {@code afterCommit} to whoever called
     * commit, which would put us right back to failing the request over a timeline row.
     */
    private void writeTimelineQuietly(CreateInteractionTimelineRequest timelineReq, UUID handoverId) {
        try {
            createInteractionTimelineUseCase.execute(timelineReq);
        } catch (Exception e) {
            log.error("Failed to record Interaction Timeline for handover {}: {}",
                    handoverId, e.getMessage());
        }
    }

    private void notifyClarificationNeeded(OpHandoverEntity handover, UserEntity actor) {
        BookingEntity booking = handover.getBooking();

        // The originating Sales/Reservation user, or failing that whoever owns the booking.
        // `created_by` is nullable, and the old code returned silently when it was null: Front
        // Office saw "Updated." while nobody was told, leaving the handover waiting on a question
        // no one had been asked. POST-3 must not fail quietly.
        UserEntity recipient = handover.getCreatedBy();
        if (recipient == null && booking != null) {
            recipient = booking.getAssignedUser();
        }
        if (recipient == null) {
            log.warn("Handover {} needs clarification but has no recipient (created_by and "
                            + "booking.assigned_user are both null) — POST-3 notification skipped",
                    handover.getHandoverId());
            return;
        }

        String bookingCode = booking != null ? booking.getBookingCode() : "";
        String by = actor != null && actor.getFullName() != null ? actor.getFullName() : "Front Office";

        NotificationEntity notification = NotificationEntity.builder()
                .user(recipient)
                .title("Handover Clarification Requested")
                .message(by + " requested clarification for handover " + bookingCode + ": " + handover.getClarificationNote())
                .type("HANDOVER")
                .relatedEntity("HANDOVER")
                .relatedId(handover.getHandoverId())
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
        notificationRepository.save(notification);
    }

    private ReadinessStatus parseReadiness(String value) {
        ReadinessStatus parsed;
        try {
            parsed = ReadinessStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("Invalid readiness status: " + value);
        }
        // E7.3 — FO cannot set the initial PENDING_REVIEW (or any non-FO value).
        if (!FO_SETTABLE.contains(parsed)) {
            throw new IllegalStateException("Invalid readiness status: " + value);
        }
        return parsed;
    }

    /**
     * E7.3 / step 7 — the target value is legal in the abstract (checked by
     * {@link #parseReadiness}), but is it legal <em>from where this handover stands</em>?
     */
    private void assertTransitionAllowed(ReadinessStatus current, ReadinessStatus target) {
        // A row predating the readiness column behaves as if it had just been submitted.
        ReadinessStatus from = current != null ? current : ReadinessStatus.PENDING_REVIEW;

        if (ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(target)) {
            return;
        }

        // POST-4 gets its own sentence: "invalid status" would leave the front desk guessing why
        // a value the dropdown offered was refused, when the real answer is that they are waiting
        // on somebody else.
        if (from == ReadinessStatus.NEED_CLARIFICATION) {
            throw new IllegalStateException(
                    "This handover is waiting for Sales/Reservation to clarify it. They have to "
                            + "update and re-submit it before its readiness can be confirmed.");
        }
        throw new IllegalStateException(
                "Invalid readiness status: cannot go from " + from + " to " + target + ".");
    }
}
