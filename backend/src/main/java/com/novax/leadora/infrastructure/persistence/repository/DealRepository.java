package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
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
public interface DealRepository extends JpaRepository<DealEntity, UUID>, JpaSpecificationExecutor<DealEntity> {
    List<DealEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<DealEntity> findByCustomer_CustomerId(UUID customerId);
    List<DealEntity> findByStatus(DealStatus status);

    // ── Performance report query (eliminates N+1 and filters at DB level) ──
    @EntityGraph(attributePaths = {"assignedUser"})
    @Query("""
            SELECT d FROM DealEntity d
            WHERE d.createdAt >= :startDate
              AND d.createdAt <= :endDate
            """)
    List<DealEntity> findByCreatedAtRange(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);
}
