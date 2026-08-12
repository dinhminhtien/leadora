package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.HoldStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Rooms provisionally taken out of allotment while a quotation is live.
 *
 * <p>Without this, two sales reps who both see "1 left" can both quote it, and the CRM itself
 * causes the oversell. The hold is internal bookkeeping only — the hotel is not told, and nothing
 * is reserved anywhere outside this table.
 *
 * <p><b>Stored as a range, unlike {@link RoomAllotmentEntity}.</b> Quota is stock and is kept per
 * night so it can be edited a night at a time; a hold is a transaction with a beginning and an
 * end, and only a handful are ever live at once, so ranges cost nothing to expand on read.
 *
 * <p>{@code expiresAt} is what keeps abandoned quotations from sitting on stock forever — see
 * {@code AllotmentHoldExpiryScheduler}. Only {@link HoldStatus#ACTIVE} rows count against
 * availability (BR-47).
 */
@Entity
@Table(
        name = "room_allotment_holds",
        indexes = {
                @Index(name = "idx_holds_status_expires", columnList = "status, expires_at"),
                @Index(name = "idx_holds_product_dates", columnList = "product_id, check_in_date, check_out_date"),
                @Index(name = "idx_holds_quotation", columnList = "quotation_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAllotmentHoldEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hold_id")
    private UUID holdId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductServiceEntity product;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    /** Exclusive: the guest leaves this morning, so it is not one of the nights held. */
    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** The quotation this hold serves. Also how a revision recognises its own earlier hold. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private QuotationEntity quotation;

    /** Set when the hold converts, so the booking it handed over to can be traced. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private BookingEntity booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HoldStatus status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;
}
