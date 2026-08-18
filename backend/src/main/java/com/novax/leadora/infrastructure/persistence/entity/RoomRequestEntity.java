package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A room-availability question Sales asks the Reservation team, plus the answer.
 *
 * <p>This is a Sales CRM, not a PMS — it owns no room inventory. {@link #heldUntil}
 * is therefore the Reservation team's <b>commitment</b>, recorded verbatim; the room
 * itself is held in the hotel's real PMS. Nothing here is computed from booking rows.
 */
@Entity
@Table(name = "room_requests", indexes = {
    @Index(name = "idx_room_requests_quotation_status", columnList = "quotation_id, status"),
    @Index(name = "idx_room_requests_status", columnList = "status"),
    @Index(name = "idx_room_requests_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomRequestEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id")
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private QuotationEntity quotation;

    /**
     * Free text, deliberately not an FK to product_services: this is the question as
     * Sales phrased it ("Deluxe twin, sea view"), matching {@code QuotationEntity.roomType}.
     * The Reservation team reads it and answers from the PMS.
     */
    @Column(name = "room_type_requested", length = 255)
    private String roomTypeRequested;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomRequestStatus status;

    /**
     * Context supplied with the question, so the Reservation team can see what the CRM already
     * believes before opening the PMS — "quota shows 10, 12 needed, 2 short, published 08:00".
     *
     * <p>Filled in automatically when a quotation cannot be covered from allotment. Without it
     * the request reads as a bare "is this available?", and the answer comes back as a bare
     * yes/no, which costs another round trip to establish what was actually short.
     */
    @Column(name = "requester_note", columnDefinition = "TEXT")
    private String requesterNote;

    /** The Reservation team's answer in their own words ("5 Deluxe left" / "Full, try Suite"). */
    @Column(name = "reservation_note", columnDefinition = "TEXT")
    private String reservationNote;

    /**
     * How long Reservation promises to hold the rooms. Recorded, never derived —
     * the actual hold lives in the PMS. Null means "confirmed, no hold deadline given".
     */
    @Column(name = "held_until")
    private OffsetDateTime heldUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private UserEntity requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by")
    private UserEntity respondedBy;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    /** Two Reservation staff answering the same request concurrently — second one loses. */
    @Version
    @Column(name = "version_lock", nullable = false)
    @Builder.Default
    private Integer versionLock = 0;
}
