package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.BookingStatusAggregate;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, UUID>, JpaSpecificationExecutor<BookingEntity> {
    Optional<BookingEntity> findByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = {"customer", "assignedUser", "quotation"})
    List<BookingEntity> findByCustomer_CustomerId(UUID customerId);

    @EntityGraph(attributePaths = {"customer", "assignedUser", "quotation"})
    List<BookingEntity> findByStatus(BookingStatus status);

    @Override
    @EntityGraph(attributePaths = {"customer", "assignedUser", "quotation"})
    Page<BookingEntity> findAll(Specification<BookingEntity> spec, Pageable pageable);

    // ── Performance report query (eliminates N+1 and filters at DB level) ──
    @EntityGraph(attributePaths = {"assignedUser"})
    @Query("""
            SELECT b FROM BookingEntity b
            WHERE b.createdAt >= :startDate
              AND b.createdAt <= :endDate
            """)
    List<BookingEntity> findByCreatedAtRange(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────

    @Query("""
            SELECT new com.novax.leadora.application.usecase.chat.dto.BookingStatusAggregate(
                       b.status, COUNT(b), SUM(b.totalAmount))
            FROM BookingEntity b
            WHERE (:userId IS NULL OR b.assignedUser.userId = :userId)
            GROUP BY b.status
            """)
    List<BookingStatusAggregate> aggregateByStatusForChat(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"customer", "assignedUser"})
    @Query("""
            SELECT b FROM BookingEntity b
            WHERE (:userId IS NULL OR b.assignedUser.userId = :userId)
            ORDER BY b.createdAt DESC
            """)
    List<BookingEntity> findRecentForChat(@Param("userId") UUID userId, Pageable pageable);
}
