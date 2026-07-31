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
    boolean existsByRuleId(UUID ruleId);
    List<SlaTrackingEntity> findByEntityType(String entityType);
    List<SlaTrackingEntity> findByEntityTypeAndEntityId(String entityType, UUID entityId);
    List<SlaTrackingEntity> findByStatusAndDeadlineAtBefore(SlaStatus status, OffsetDateTime before);
    List<SlaTrackingEntity> findByStatusIn(List<SlaStatus> statuses);
    List<SlaTrackingEntity> findByStatusInAndEntityType(List<SlaStatus> statuses, String entityType);

    List<SlaTrackingEntity> findByStatusAndWarningAtBeforeAndWarningNotifiedFalse(SlaStatus status, OffsetDateTime before);

    /** UC-17.4: BREACHED records past escalationAt that haven't been escalated yet */
    List<SlaTrackingEntity> findByStatusAndEscalationAtBeforeAndEscalationNotifiedFalse(SlaStatus status, OffsetDateTime before);

    /**
     * UC-17.6 / UC-23.3: the tuples the compliance report classifies, with optional entityType /
     * activityType filters.
     *
     * <p>Five scalars per record rather than the managed entity: the report only ever needs to
     * compare {@code resolvedAt} against {@code deadlineAt} and bucket by activity, and selecting
     * entities made every row a persistence-context citizen for no benefit.
     *
     * <p>Returns {@code [activityType, status, startedAt, warningAt, deadlineAt, resolvedAt]}.
     * The range is half-open: {@code [start, end)} — see ReportRange.
     */
    @Query("""
            SELECT s.activityType, s.status, s.startedAt, s.warningAt, s.deadlineAt, s.resolvedAt
            FROM SlaTrackingEntity s
            WHERE s.startedAt >= :start AND s.startedAt < :end
              AND (:entityType IS NULL OR s.entityType = :entityType)
              AND (:activityType IS NULL OR s.activityType = :activityType)
            ORDER BY s.startedAt ASC
            """)
    List<Object[]> findComplianceRows(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("entityType") String entityType,
            @Param("activityType") String activityType);
}
