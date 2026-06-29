package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID> {

    @EntityGraph(attributePaths = {"user", "createdBy"})
    List<ReminderEntity> findByUser_UserIdOrderByRemindAtAsc(UUID userId);

    @EntityGraph(attributePaths = {"user", "createdBy"})
    List<ReminderEntity> findByUser_UserIdAndStatusOrderByRemindAtAsc(UUID userId, ReminderStatus status);

    @EntityGraph(attributePaths = {"user", "createdBy"})
    List<ReminderEntity> findByUser_UserIdAndStatusNotOrderByRemindAtAsc(UUID userId, ReminderStatus excludedStatus);

    /** Used by scheduler — only needs user for notification, no createdBy needed */
    List<ReminderEntity> findByStatusAndRemindAtBefore(ReminderStatus status, OffsetDateTime time);

    List<ReminderEntity> findByRelatedEntityAndRelatedId(String relatedEntity, UUID relatedId);

    @EntityGraph(attributePaths = {"user", "createdBy"})
    List<ReminderEntity> findAllByOrderByRemindAtAsc();

    /** Manager "all staff" view — excludes CANCELLED by default */
    @EntityGraph(attributePaths = {"user", "createdBy"})
    List<ReminderEntity> findByStatusNotOrderByRemindAtAsc(ReminderStatus excludedStatus);

    /** Manager "all staff" with specific status filter */
    @EntityGraph(attributePaths = {"user", "createdBy"})
    List<ReminderEntity> findByStatusOrderByRemindAtAsc(ReminderStatus status);
}