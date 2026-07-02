package com.novax.leadora.infrastructure.persistence.entity.enums;

/**
 * Front Office arrival-readiness of an operational handover (UC-22.3).
 *
 * <ul>
 *   <li>{@code PENDING_REVIEW} — submitted to Front Office, not yet reviewed (initial state).</li>
 *   <li>{@code REVIEWED} — Front Office has checked the handover information.</li>
 *   <li>{@code READY_FOR_ARRIVAL} — room/service prepared, ready to receive the guest.</li>
 *   <li>{@code NEED_CLARIFICATION} — FO needs Sales/Reservation to clarify before final readiness.</li>
 * </ul>
 */
public enum ReadinessStatus {
    PENDING_REVIEW,
    REVIEWED,
    READY_FOR_ARRIVAL,
    NEED_CLARIFICATION
}
