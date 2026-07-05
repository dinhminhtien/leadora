package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
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
    List<QuotationEntity> findByDeal_DealId(UUID dealId);
    List<QuotationEntity> findByCustomer_CustomerId(UUID customerId);
    List<QuotationEntity> findByStatus(QuotationStatus status);
    List<QuotationEntity> findByStatusInAndValidUntilBefore(List<QuotationStatus> statuses, LocalDate date);

    // ── Performance report query (filters at DB level) ──
    @Query("""
            SELECT q FROM QuotationEntity q
            WHERE q.createdAt >= :startDate
              AND q.createdAt <= :endDate
            """)
    List<QuotationEntity> findByCreatedAtRange(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);
}
