package com.novax.leadora.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID>, JpaSpecificationExecutor<PaymentEntity> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.paymentId = :id")
    java.util.Optional<PaymentEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT p FROM PaymentEntity p LEFT JOIN FETCH p.booking b LEFT JOIN FETCH b.customer LEFT JOIN FETCH p.createdBy WHERE p.paymentId = :id")
    java.util.Optional<PaymentEntity> findByIdWithRelations(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"booking", "booking.customer", "createdBy"})
    org.springframework.data.domain.Page<PaymentEntity> findAll(org.springframework.data.jpa.domain.Specification<PaymentEntity> spec, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.customer"})
    List<PaymentEntity> findByBooking_BookingId(UUID bookingId);

    @EntityGraph(attributePaths = {"booking", "booking.customer"})
    List<PaymentEntity> findByBooking_BookingIdIn(List<UUID> bookingIds);
    boolean existsByBooking_BookingIdAndStatus(UUID bookingId, PaymentStatus status);
    List<PaymentEntity> findByStatus(PaymentStatus status);
    // ── UC-23.1 revenue aggregates ────────────────────────────────────────────
    // Revenue is placed in the period it was *collected* (paidAt), falling back to createdAt for
    // rows a gateway confirmed without stamping a time. Ranges are half-open: [start, end).

    /** Total collected amount in the period. */
    @Query("""
            SELECT sum(p.amount) FROM PaymentEntity p
            WHERE p.status = :status
              AND ((p.paidAt IS NOT NULL AND p.paidAt >= :start AND p.paidAt < :end) OR (p.paidAt IS NULL AND p.createdAt >= :start AND p.createdAt < :end))
            """)
    BigDecimal sumCollected(
            @Param("status") PaymentStatus status,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /**
     * {@code [ownerId, ownerName, sumAmount]} rows. Attribution follows the booking's assignee —
     * payments carry no owner of their own — and LEFT JOINs keep unattributable revenue visible
     * instead of quietly shrinking the per-rep column below the headline figure.
     */
    @Query("""
            SELECT u.userId, u.fullName, sum(p.amount)
            FROM PaymentEntity p LEFT JOIN p.booking b LEFT JOIN b.assignedUser u
            WHERE p.status = :status
              AND ((p.paidAt IS NOT NULL AND p.paidAt >= :start AND p.paidAt < :end) OR (p.paidAt IS NULL AND p.createdAt >= :start AND p.createdAt < :end))
            GROUP BY u.userId, u.fullName
            """)
    List<Object[]> aggregateCollectedByOwner(
            @Param("status") PaymentStatus status,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    java.util.Optional<PaymentEntity> findByGatewayTransactionId(String gatewayTransactionId);

    @Query(value = "SELECT * FROM payments p WHERE REPLACE(CAST(p.payment_id AS text), '-', '') LIKE :prefix || '%'", nativeQuery = true)
    List<PaymentEntity> findByPaymentIdPrefix(@Param("prefix") String prefix);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────
    // Payments hang off a booking, so they inherit that booking's assignee for scoping.

    @EntityGraph(attributePaths = {"booking"})
    @Query("""
            SELECT p FROM PaymentEntity p
            WHERE (:userId IS NULL OR p.booking.assignedUser.userId = :userId)
            ORDER BY p.createdAt DESC
            """)
    List<PaymentEntity> findRecentForChat(@Param("userId") UUID userId, Pageable pageable);
}
