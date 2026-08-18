package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingDetailRepository extends JpaRepository<BookingDetailEntity, UUID> {

    @EntityGraph(attributePaths = {"productService"})
    List<BookingDetailEntity> findByBooking_BookingId(UUID bookingId);

    @EntityGraph(attributePaths = {"productService"})
    List<BookingDetailEntity> findByBooking_BookingIdIn(Collection<UUID> bookingIds);

    /** How many units of one product this CRM has already committed in a date range. */
    interface ProductCommitment {
        UUID getProductId();
        Long getCommitted();
    }

    /**
     * Units committed per product across bookings overlapping the range — aggregated in
     * SQL so availability reporting no longer loads every booking into memory and then
     * queries details per booking.
     */
    @Query("""
            SELECT bd.productService.productId AS productId, SUM(bd.quantity) AS committed
            FROM BookingDetailEntity bd
            WHERE bd.booking.status IN :statuses
              AND bd.booking.checkInDate < :checkOutDate
              AND bd.booking.checkOutDate > :checkInDate
              AND bd.productService IS NOT NULL
            GROUP BY bd.productService.productId
            """)
    List<ProductCommitment> sumCommittedByProduct(
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate);

    /** One committed stay: which room type, how many rooms, and the nights it spans. */
    interface BookingNightSpan {
        UUID getProductId();
        LocalDate getCheckInDate();
        LocalDate getCheckOutDate();
        Integer getQuantity();
    }

    /**
     * Stays overlapping a window, for working out how much quota each <em>night</em> has already
     * consumed. {@link #sumCommittedByProduct} cannot answer that: it returns a single total for
     * the whole range, which says nothing about which night is the tight one.
     *
     * <p>Returns spans rather than per-night totals, leaving the expansion to
     * {@code RoomAvailabilityService}. Doing it in SQL would need {@code generate_series}, which
     * is PostgreSQL-only and — as a native query — would hand back an interface projection whose
     * column-alias mapping is easy to get subtly wrong. The window is capped at 90 days
     * (BR-46), so the row count is bounded and the expansion is a few thousand iterations at
     * worst. This still avoids the pathology the aggregate query above was written to fix:
     * loading whole bookings and then querying details one booking at a time.
     *
     * <p>Overlap test is half-open on both sides — a stay ending on the first day of the window
     * released its last night the evening before, so it must not count.
     */
    @Query("""
            SELECT bd.productService.productId AS productId,
                   bd.booking.checkInDate      AS checkInDate,
                   bd.booking.checkOutDate     AS checkOutDate,
                   bd.quantity                 AS quantity
            FROM BookingDetailEntity bd
            WHERE bd.booking.status IN :statuses
              AND bd.booking.checkInDate < :toExclusive
              AND bd.booking.checkOutDate > :from
              AND bd.productService.productId IN :productIds
            """)
    List<BookingNightSpan> findCommittedSpans(
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("productIds") Collection<UUID> productIds,
            @Param("from") LocalDate from,
            @Param("toExclusive") LocalDate toExclusive);

    /**
     * Bookings of one room type that overlap a window — who to warn when the hotel cuts quota
     * below what has already been sold (BR-49).
     *
     * <p>Fetches the assigned rep eagerly: the caller's whole purpose is to notify them, and a
     * lazy association would produce one query per booking at exactly the moment a batch of them
     * has just been affected.
     */
    @Query("""
            SELECT DISTINCT bd.booking FROM BookingDetailEntity bd
            LEFT JOIN FETCH bd.booking.assignedUser
            WHERE bd.booking.status IN :statuses
              AND bd.productService.productId = :productId
              AND bd.booking.checkInDate < :toExclusive
              AND bd.booking.checkOutDate > :from
            """)
    List<com.novax.leadora.infrastructure.persistence.entity.BookingEntity> findAffectedBookings(
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("productId") UUID productId,
            @Param("from") LocalDate from,
            @Param("toExclusive") LocalDate toExclusive);
}
