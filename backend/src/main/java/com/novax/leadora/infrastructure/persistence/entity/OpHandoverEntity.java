package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "op_handovers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpHandoverEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "handover_id")
    private UUID handoverId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserEntity updatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HandoverStatus status;

    @Column(name = "special_requests", columnDefinition = "TEXT")
    private String specialRequests;

    @Column(name = "room_preferences", columnDefinition = "TEXT")
    private String roomPreferences;

    @Column(name = "vip_notes", columnDefinition = "TEXT")
    private String vipNotes;

    @Column(name = "operational_notes", columnDefinition = "TEXT")
    private String operationalNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "readiness_status", nullable = false, length = 20)
    private ReadinessStatus readinessStatus;

    /** Front Office note when readiness = NEED_CLARIFICATION (UC-22.3). */
    @Column(name = "clarification_note", columnDefinition = "TEXT")
    private String clarificationNote;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "assigned_fo_user_id")
    private UUID assignedFoUserId;

    /**
     * Optimistic lock. Two Front Office staff opening the same arrival and saving different
     * readiness values used to be last-write-wins, with neither of them told — one would see
     * "Updated." and walk away believing the room was marked ready.
     *
     * <p>{@code GlobalExceptionHandler} already maps {@code OptimisticLockingFailureException} to
     * HTTP 409, so this field is the whole fix.
     */
    // `columnDefinition` carries the DEFAULT on purpose. Without it Hibernate's ddl-auto=update
    // emits `add column version integer not null`, which Postgres refuses on a table that already
    // has rows ("contains null values") — and Hibernate logs that failure as a WARN and carries on,
    // so the application boots with the column missing and every read of op_handovers then fails
    // with a 500. The DEFAULT lets the same statement backfill existing rows and succeed.
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer version = 0;
}
