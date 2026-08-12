package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.ReservationRejectReason;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * BR-15 — Reservation Decision Log.
 * Records the decision (approve or reject) made by Reservation staff on a quotation
 * that was in RESERVATION_PENDING status.
 */
@Entity
@Table(name = "reservation_decision_logs", indexes = {
    @Index(name = "idx_rdl_quotation_id", columnList = "quotation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDecisionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "quotation_id", nullable = false)
    private UUID quotationId;

    /** "APPROVED" or "REJECTED" */
    @Column(name = "decision", nullable = false, length = 10)
    private String decision;

    /** Mandatory when decision is REJECTED, null when APPROVED */
    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason", length = 50)
    private ReservationRejectReason rejectReason;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false)
    @Builder.Default
    private OffsetDateTime decidedAt = OffsetDateTime.now();

    /** The created booking ID if decision is APPROVED, null if REJECTED */
    @Column(name = "booking_id")
    private UUID bookingId;
}
