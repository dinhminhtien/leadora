package com.novax.leadora.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuotationRepository extends JpaRepository<QuotationEntity, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM QuotationEntity q WHERE q.quotationId = :id")
    java.util.Optional<QuotationEntity> findByIdForUpdate(@Param("id") UUID id);

    List<QuotationEntity> findByDeal_DealId(UUID dealId);
    List<QuotationEntity> findByDeal_DealIdIn(List<UUID> dealIds);
    List<QuotationEntity> findByCustomer_CustomerId(UUID customerId);
    List<QuotationEntity> findByStatus(QuotationStatus status);
    List<QuotationEntity> findByStatusInAndValidUntilBefore(List<QuotationStatus> statuses, LocalDate date);

    // UC-23.5's aggregates live in QuotationOutcomeRepository: they need the satellite tables
    // (approval history, customer responses, closure logs) and the event timestamps, which a
    // status roll-up over this entity cannot reach.

    // ── Chat-assistant snapshot ───────────────────────────────────────────────
    // Quotations have no assignee column: they are scoped through the deal they belong to, so
    // "my quotations" means those of the deals assigned to the caller. A null :userId means all.

    @EntityGraph(attributePaths = {"customer", "deal"})
    @Query("""
            SELECT q FROM QuotationEntity q
            WHERE (:userId IS NULL OR q.deal.assignedUser.userId = :userId)
            ORDER BY q.createdAt DESC
            """)
    List<QuotationEntity> findRecentForChat(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT q.quotationId FROM QuotationEntity q WHERE q.deal.assignedUser.userId = :userId")
    List<UUID> findQuotationIdsByAssignedUser_UserId(@Param("userId") UUID userId);
}
