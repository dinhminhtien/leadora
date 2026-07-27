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
}
