package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
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
}
