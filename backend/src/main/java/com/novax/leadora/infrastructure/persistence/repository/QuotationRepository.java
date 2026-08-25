package com.novax.leadora.infrastructure.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.api.dto.response.QuotationSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuotationRepository extends JpaRepository<QuotationEntity, UUID> {
    @Query("""
            SELECT new com.novax.leadora.api.dto.response.QuotationSummaryDto(
                q.quotationId,
                d.dealId,
                d.dealName,
                c.customerId,
                c.fullName,
                c.email,
                c.phone,
                q.roomType,
                q.checkInDate,
                q.checkOutDate,
                q.paymentPolicy,
                q.subtotal,
                q.discountPercent,
                q.discountAmount,
                q.totalAmount,
                q.validUntil,
                q.status,
                q.notes,
                q.version,
                q.parentQuotationId,
                q.changeReason,
                q.createdAt
            )
            FROM QuotationEntity q
            LEFT JOIN q.deal d
            LEFT JOIN q.customer c
            LEFT JOIN q.createdBy cb
            WHERE (:ownerId IS NULL OR cb.userId = :ownerId)
              AND (:status IS NULL OR q.status = :status)
              AND (coalesce(:statuses, null) IS NULL OR q.status IN :statuses)
              AND (CAST(:search AS string) IS NULL OR
                   LOWER(c.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
                   LOWER(d.dealName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                  )
            """)
    Page<QuotationSummaryDto> findAllSummaries(
            @Param("ownerId") UUID ownerId,
            @Param("status") QuotationStatus status,
            @Param("statuses") List<QuotationStatus> statuses,
            @Param("search") String search,
            Pageable pageable);

    /**
     * The same page, ordered by what the rep has to do next rather than by a column.
     *
     * <p>Opening the list on "newest first" put whatever happened to be created last at the top,
     * which is rarely the quotation anyone needs. This orders by <b>whose move it is</b>: the ones
     * waiting on this rep first — a manager asked for changes, a rejection to revise, an approved
     * quotation to send, an accepted one to convert — then the ones waiting on someone else, then
     * everything closed out. {@code validUntil} breaks ties, so within a band the quotation
     * expiring soonest leads.
     *
     * <p>Ordering lives in the query rather than in a {@code Sort}, because Spring Data can only
     * sort by a property and this is a ranking over the status enum. Callers must therefore pass an
     * <b>unsorted</b> {@code Pageable}: a {@code Sort} would be appended after this {@code ORDER
     * BY} and silently take precedence over none of it, but it would still confuse the two.
     */
    @Query("""
            SELECT new com.novax.leadora.api.dto.response.QuotationSummaryDto(
                q.quotationId,
                d.dealId,
                d.dealName,
                c.customerId,
                c.fullName,
                c.email,
                c.phone,
                q.roomType,
                q.checkInDate,
                q.checkOutDate,
                q.paymentPolicy,
                q.subtotal,
                q.discountPercent,
                q.discountAmount,
                q.totalAmount,
                q.validUntil,
                q.status,
                q.notes,
                q.version,
                q.parentQuotationId,
                q.changeReason,
                q.createdAt
            )
            FROM QuotationEntity q
            LEFT JOIN q.deal d
            LEFT JOIN q.customer c
            LEFT JOIN q.createdBy cb
            WHERE (:ownerId IS NULL OR cb.userId = :ownerId)
              AND (:status IS NULL OR q.status = :status)
              AND (coalesce(:statuses, null) IS NULL OR q.status IN :statuses)
              AND (CAST(:search AS string) IS NULL OR
                   LOWER(c.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
                   LOWER(d.dealName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                  )
            ORDER BY
              CASE q.status
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.PENDING_REVISION THEN 0
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.REJECTED THEN 1
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.RESERVATION_REJECTED THEN 2
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.APPROVED THEN 3
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.ACCEPTED THEN 4
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.ACCEPTED_BY_CUSTOMER THEN 5
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.DRAFT THEN 6
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.INTERESTED THEN 7
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.RESERVATION_PENDING THEN 8
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.PENDING_CUSTOMER_RESPONSE THEN 9
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.SENT THEN 10
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.PENDING_APPROVAL THEN 11
                WHEN com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus.BOOKING_REQUEST THEN 12
                ELSE 13
              END ASC,
              q.validUntil ASC NULLS LAST,
              q.createdAt DESC
            """)
    Page<QuotationSummaryDto> findAllSummariesByPriority(
            @Param("ownerId") UUID ownerId,
            @Param("status") QuotationStatus status,
            @Param("statuses") List<QuotationStatus> statuses,
            @Param("search") String search,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM QuotationEntity q WHERE q.quotationId = :id")
    java.util.Optional<QuotationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"customer", "deal"})
    java.util.Optional<QuotationEntity> findById(UUID id);

    List<QuotationEntity> findByDeal_DealId(UUID dealId);
    List<QuotationEntity> findByDeal_DealIdIn(List<UUID> dealIds);
    List<QuotationEntity> findByCustomer_CustomerId(UUID customerId);
    List<QuotationEntity> findByStatus(QuotationStatus status);
    List<QuotationEntity> findByStatus(QuotationStatus status, Pageable pageable);
    List<QuotationEntity> findByStatusInAndValidUntilBefore(List<QuotationStatus> statuses, LocalDate date);

    // ── UC-23.1 / UC-23.5 report aggregates ───────────────────────────────────
    // Ranges are half-open: [start, end) — see ReportRange.

    /** {@code [status, count]} rows. */
    @Query("""
            SELECT q.status, count(q) FROM QuotationEntity q
            WHERE q.createdAt >= :start
              AND q.createdAt < :end
            GROUP BY q.status
            """)
    List<Object[]> aggregateByStatus(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /**
     * Quotations that actually cleared manager approval, identified by {@code approved_at} rather
     * than by the current status.
     *
     * <p>UC-23.5's approval rate cannot be read off {@code status}: approval is overwritten in
     * place by SEND → ACCEPTED → CONVERTED, so the better the team performs the fewer rows are
     * left sitting at {@code APPROVED}. {@code approvedAt} is written once and never cleared, so it
     * is the only durable record that an approval happened.
     */
    @Query("""
            SELECT count(q) FROM QuotationEntity q
            WHERE q.createdAt >= :start
              AND q.createdAt < :end
              AND q.approvedAt IS NOT NULL
              AND q.status <> :excludedStatus
            """)
    long countApproved(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("excludedStatus") QuotationStatus excludedStatus);

    /**
     * Quotations rejected <em>by the approver</em>, i.e. REJECTED without ever having been approved.
     * A quotation the customer turned down also ends up REJECTED, but only after being approved and
     * sent — so {@code approvedAt IS NULL} separates the two populations that BR-20/BR-21 treat as
     * different outcomes.
     */
    @Query("""
            SELECT count(q) FROM QuotationEntity q
            WHERE q.createdAt >= :start
              AND q.createdAt < :end
              AND q.approvedAt IS NULL
              AND q.status = :rejectedStatus
            """)
    long countRejectedByApprover(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("rejectedStatus") QuotationStatus rejectedStatus);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────
    // Quotations have no assignee column: they are scoped through the deal they belong to, so
    // "my quotations" means those of the deals assigned to the caller. A null :userId means all.

    @EntityGraph(attributePaths = {"customer", "deal"})
    @Query("""
            SELECT q FROM QuotationEntity q
            WHERE (:userId IS NULL OR q.deal.assignedUser.userId = :userId)
              AND q.createdAt >= :from
              AND q.createdAt <= :to
            ORDER BY q.createdAt DESC
            """)
    List<QuotationEntity> findRecentForChat(@Param("userId") UUID userId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    @Query("SELECT q.quotationId FROM QuotationEntity q WHERE q.deal.assignedUser.userId = :userId")
    List<UUID> findQuotationIdsByAssignedUser_UserId(@Param("userId") UUID userId);
}
