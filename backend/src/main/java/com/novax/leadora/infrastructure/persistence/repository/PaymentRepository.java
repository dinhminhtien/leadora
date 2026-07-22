package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.PaymentStatusAggregate;
import org.springframework.data.domain.Pageable;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID>, JpaSpecificationExecutor<PaymentEntity> {
    List<PaymentEntity> findByBooking_BookingId(UUID bookingId);
    List<PaymentEntity> findByStatus(PaymentStatus status);
    // ── Performance report query (eliminates N+1 and filters at DB level) ──
    @EntityGraph(attributePaths = {"booking", "booking.assignedUser"})
    @Query("""
            SELECT p FROM PaymentEntity p
            WHERE p.status = :status
              AND COALESCE(p.paidAt, p.createdAt) >= :startDate
              AND COALESCE(p.paidAt, p.createdAt) <= :endDate
            """)
    List<PaymentEntity> findPaidPaymentsForReport(
            @Param("status") PaymentStatus status,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    java.util.Optional<PaymentEntity> findByGatewayTransactionId(String gatewayTransactionId);

    @Query(value = "SELECT * FROM payments p WHERE REPLACE(CAST(p.payment_id AS text), '-', '') LIKE :prefix || '%'", nativeQuery = true)
    List<PaymentEntity> findByPaymentIdPrefix(@Param("prefix") String prefix);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────
    // Payments hang off a booking, so they inherit that booking's assignee for scoping.

    @Query("""
            SELECT new com.novax.leadora.application.usecase.chat.dto.PaymentStatusAggregate(
                       p.status, COUNT(p), SUM(p.amount))
            FROM PaymentEntity p
            WHERE (:userId IS NULL OR p.booking.assignedUser.userId = :userId)
            GROUP BY p.status
            """)
    List<PaymentStatusAggregate> aggregateByStatusForChat(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"booking"})
    @Query("""
            SELECT p FROM PaymentEntity p
            WHERE (:userId IS NULL OR p.booking.assignedUser.userId = :userId)
            ORDER BY p.createdAt DESC
            """)
    List<PaymentEntity> findRecentForChat(@Param("userId") UUID userId, Pageable pageable);
}
