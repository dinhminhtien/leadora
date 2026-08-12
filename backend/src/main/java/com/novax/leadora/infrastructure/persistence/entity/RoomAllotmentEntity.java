package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * How many rooms of one type the hotel has allocated to this CRM to sell, for one night.
 *
 * <p>This is an <b>allotment</b>, not the hotel's real inventory. The hotel never discloses how
 * many rooms it actually has free — it releases a quota to the sales channel, and the channel
 * sells within it. So a row here answers "how much are we still allowed to sell", never "how
 * many rooms exist". Every user-facing label must say so; see BR-45.
 *
 * <p><b>One row per (room type, night).</b> The Reservation team enters a date range on screen
 * and the server fans it out to individual nights. Storing ranges instead would mean overlapping
 * ranges have to be merged on read and split on edit, which is where this kind of feature usually
 * breaks. A night is ~1 row per room type per day — a few thousand rows a year, which is nothing.
 *
 * <p>Nothing here is derived from bookings. Rooms already sold are counted separately (see
 * {@code BookingDetailRepository.sumCommittedByProductPerDay}) and subtracted at read time, so
 * this table stays a record of what the hotel granted rather than a running balance that could
 * drift out of step with the bookings.
 */
@Entity
@Table(
        name = "room_allotments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_room_allotments_product_date",
                columnNames = {"product_id", "stay_date"}),
        indexes = {
                @Index(name = "idx_room_allotments_stay_date", columnList = "stay_date"),
                @Index(name = "idx_room_allotments_product_date", columnList = "product_id, stay_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAllotmentEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "allotment_id")
    private UUID allotmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductServiceEntity product;

    /** The night being sold. A stay of 20→22 occupies the nights of the 20th and the 21st. */
    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    /** Rooms granted for this night. Zero means the quota is used up, not that the hotel is full. */
    @Column(name = "allotted_qty", nullable = false)
    private Integer allottedQty;

    /**
     * Stop-sell: the hotel has closed this date to us entirely.
     *
     * <p>Deliberately separate from {@code allottedQty == 0}. "Quota exhausted" is worth asking
     * the Reservation team about — they may be able to get more. "Closed" is not, and folding the
     * two together is how Sales ends up raising room requests the hotel has already refused.
     */
    @Column(name = "closed", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean closed = false;

    @Column(name = "note", length = 255)
    private String note;

    /**
     * When the Reservation team last vouched for this number — <b>not</b> when the row was
     * written. If the hotel sends its figures at 08:00 and they are keyed in at 10:00, this is
     * 08:00. Staleness warnings (BR-50) are meaningless measured against {@code updatedAt},
     * because re-saving an untouched row would make old numbers look fresh.
     */
    @Column(name = "as_of", nullable = false)
    private OffsetDateTime asOf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserEntity updatedBy;

    /** Guards two Reservation staff editing the same night's quota; selling is guarded by a
     * pessimistic lock instead — see {@code RoomAllotmentRepository#lockNightsForUpdate}. */
    @Version
    @Column(name = "version_lock", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer versionLock = 0;
}
