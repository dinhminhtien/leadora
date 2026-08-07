package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * When Sales/Reservation may author an operational handover — create it, edit its notes, submit it
 * (BR-26, BR-44).
 *
 * <p>Shared by {@link CreateHandoverUseCase} and {@link UpdateHandoverUseCase} rather than copied
 * into each, the way {@code LeadContactPolicy} and {@code LeadConversionPolicy} are shared on the
 * lead side. The two paths had drifted apart: create compared status <em>names</em> against a
 * blacklist of three strings and let {@code NO_SHOW} / {@code PENDING} through, while update used a
 * whitelist. One rule, one place.
 *
 * <p><b>Two gates, and the order matters</b> — the date is checked first so a guest whose stay is
 * over hears "the arrival date has passed" rather than a status the user cannot act on.
 */
@Component
public class HandoverEditPolicy {

    /**
     * The zone "today" is measured in.
     *
     * <p>Not {@code LocalDate.now()}: that reads the JVM default, which is the hotel's zone on a
     * developer's machine and UTC in a container. With a rule that hard-blocks editing the day
     * after arrival, a UTC server would start refusing edits at 17:00 local <em>on the arrival day
     * itself</em> — the hours when the desk is most likely to be correcting the sheet. BR-32/BR-42
     * ask for a configured timezone for the same reason.
     */
    private final ZoneId zone;

    public HandoverEditPolicy(@Value("${app.time-zone:Asia/Ho_Chi_Minh}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    /**
     * @param booking  the booking behind the handover; {@code null} is accepted (a handover with no
     *                 booking has no arrival to protect) and passes
     * @param existing the handover being edited, or {@code null} when one is being created — a
     *                 record that does not exist yet cannot be under clarification
     * @throws IllegalStateException mapped to 422 BUSINESS_RULE_VIOLATION by
     *                               {@code GlobalExceptionHandler}
     */
    public void assertAuthorable(BookingEntity booking, OpHandoverEntity existing) {
        if (booking == null) {
            return;
        }

        // BR-26 — the arrival has already happened. Absolute: no clarification exception, by
        // decision. The cost is stated where it lands: a clarification Front Office raised on the
        // arrival day can no longer be answered the next day, so that handover stays in
        // NEED_CLARIFICATION until CloseFinishedHandoversUseCase closes it at check-out.
        if (booking.getCheckInDate() != null && LocalDate.now(zone).isAfter(booking.getCheckInDate())) {
            throw new IllegalStateException(
                    "The arrival date (" + booking.getCheckInDate()
                            + ") has passed, so this operational handover can no longer be edited.");
        }

        // BR-44 — the booking is no longer one an arrival is being prepared for.
        if (!BookingStatus.EDITABLE_BY_SALES.contains(booking.getStatus())
                && !isAnsweringClarification(existing, booking)) {
            throw new IllegalStateException(
                    "This booking is " + booking.getStatus()
                            + ", so its operational handover can no longer be edited.");
        }
    }

    /**
     * The one edit that stays legitimate once the booking has left {@link
     * BookingStatus#EDITABLE_BY_SALES}: answering a question Front Office asked.
     *
     * <p>Without it, narrowing the whitelist would trade an editing hole for a deadlock.
     * {@code READINESS_TRANSITIONS} lets {@code NEED_CLARIFICATION} go only to itself, and the sole
     * way out is Sales updating the handover — which flips it back to {@code PENDING_REVIEW}. Front
     * Office may raise a clarification on a {@code CHECKED_IN} booking (their own guard is the wider
     * {@link BookingStatus#LIVE_FOR_ARRIVAL}), so refusing the reply on the arrival day would strand
     * the handover in a state neither desk could clear.
     *
     * <p>Still bounded by {@code LIVE_FOR_ARRIVAL}: on a cancelled or checked-out booking there is
     * no arrival left to clarify.
     */
    private boolean isAnsweringClarification(OpHandoverEntity existing, BookingEntity booking) {
        return existing != null
                && existing.getReadinessStatus() == ReadinessStatus.NEED_CLARIFICATION
                && BookingStatus.LIVE_FOR_ARRIVAL.contains(booking.getStatus());
    }
}
