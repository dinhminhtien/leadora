package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<ContractEntity, UUID> {

    Optional<ContractEntity> findByContractCode(String code);

    List<ContractEntity> findByQuotation_QuotationId(UUID quotationId);

    List<ContractEntity> findByQuotation_QuotationIdAndStatusIn(UUID quotationId, Collection<ContractStatus> statuses);

    Optional<ContractEntity> findTopByQuotation_QuotationIdAndStatus(UUID quotationId, ContractStatus status);

    Optional<ContractEntity> findByQuotation_QuotationIdAndVersion(UUID quotationId, int version);

    List<ContractEntity> findByDeal_DealIdAndStatusIn(UUID dealId, Collection<ContractStatus> statuses);

    List<ContractEntity> findByDeal_DealId(UUID dealId);

    List<ContractEntity> findByStatusInAndValidUntilBefore(Collection<ContractStatus> statuses, LocalDate date);

    @Query("SELECT c FROM ContractEntity c WHERE c.status = 'SENT' AND c.validUntil < :today")
    List<ContractEntity> findExpirableContracts(@Param("today") LocalDate today);

    // -- Chat-assistant snapshot ----------------------------------------------
    // Contracts have no assignee column of their own: like quotations, they are scoped through the
    // deal they belong to, so "my contracts" means those of the deals assigned to the caller.
    // A null :userId means all. The fetch graph is what keeps the listing to one query - the
    // section prints the customer and the deal for every row, and both associations are LAZY, so
    // without it ten rows cost twenty extra round trips to a database in another region.

    @EntityGraph(attributePaths = {"customer", "deal"})
    @Query("""
            SELECT c FROM ContractEntity c
            WHERE (:userId IS NULL OR c.deal.assignedUser.userId = :userId)
              AND c.createdAt >= :from
              AND c.createdAt <= :to
            ORDER BY c.createdAt DESC
            """)
    List<ContractEntity> findRecentForChat(@Param("userId") UUID userId,
                                           @Param("from") OffsetDateTime from,
                                           @Param("to") OffsetDateTime to,
                                           Pageable pageable);
}
