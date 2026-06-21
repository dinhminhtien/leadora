package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SlaTrackingRepository extends JpaRepository<SlaTrackingEntity, UUID> {
    List<SlaTrackingEntity> findByEntityTypeAndEntityId(String entityType, UUID entityId);
    List<SlaTrackingEntity> findByStatusAndDeadlineAtBefore(SlaStatus status, OffsetDateTime before);

    /** UC-17.6: fetch records for report with optional entityType / activityType filters */
    @Query("SELECT s FROM SlaTrackingEntity s " +
           "WHERE s.startedAt >= :from AND s.startedAt <= :to " +
           "AND (:entityType IS NULL OR s.entityType = :entityType) " +
           "AND (:activityType IS NULL OR s.activityType = :activityType) " +
           "ORDER BY s.startedAt ASC")
    List<SlaTrackingEntity> findForReport(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("entityType") String entityType,
            @Param("activityType") String activityType);
}
