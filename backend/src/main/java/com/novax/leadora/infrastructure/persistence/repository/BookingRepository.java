package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, UUID>, JpaSpecificationExecutor<BookingEntity> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookingEntity b WHERE b.bookingId = :id")
    Optional<BookingEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT b FROM BookingEntity b LEFT JOIN FETCH b.customer LEFT JOIN FETCH b.assignedUser WHERE b.bookingId = :id")
    Optional<BookingEntity> findByIdWithCustomerAndAssignedUser(@Param("id") UUID id);

    Optional<BookingEntity> findByBookingCode(String bookingCode);

    boolean existsByQuotation_Deal_DealIdAndStatus(UUID dealId, BookingStatus status);

    List<BookingEntity> findByQuotation_QuotationId(UUID quotationId);

    List<BookingEntity> findByQuotation_QuotationIdIn(List<UUID> quotationIds);

    @EntityGraph(attributePaths = {"customer", "assignedUser", "quotation"})
    List<BookingEntity> findByCustomer_CustomerId(UUID customerId);

    @EntityGraph(attributePaths = {"customer", "assignedUser", "quotation"})
    List<BookingEntity> findByStatus(BookingStatus status);

    @Override
    @EntityGraph(attributePaths = {"customer", "assignedUser", "quotation"})
    Page<BookingEntity> findAll(Specification<BookingEntity> spec, Pageable pageable);

    // ── UC-23.1 report aggregates ─────────────────────────────────────────────
    // Ranges are half-open: [start, end) — see ReportRange.

    /** {@code [status, count]} rows. */
    @Query("""
            SELECT b.status, count(b) FROM BookingEntity b
            WHERE b.createdAt >= :start
              AND b.createdAt < :end
            GROUP BY b.status
            """)
    List<Object[]> aggregateByStatus(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /** {@code [ownerId, ownerName, count]} rows, restricted to the statuses that count as booked. */
    @Query("""
            SELECT u.userId, u.fullName, count(b)
            FROM BookingEntity b LEFT JOIN b.assignedUser u
            WHERE b.createdAt >= :start
              AND b.createdAt < :end
              AND b.status IN :statuses
            GROUP BY u.userId, u.fullName
            """)
    List<Object[]> aggregateByOwnerForStatuses(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("statuses") Collection<BookingStatus> statuses);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────

    @EntityGraph(attributePaths = {"customer", "assignedUser"})
    @Query("""
            SELECT b FROM BookingEntity b
            WHERE (:userId IS NULL OR b.assignedUser.userId = :userId)
            ORDER BY b.createdAt DESC
            """)
    List<BookingEntity> findRecentForChat(@Param("userId") UUID userId, Pageable pageable);
}
