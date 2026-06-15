package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID> {
    List<ReminderEntity> findByUser_UserIdOrderByRemindAtAsc(UUID userId);
    List<ReminderEntity> findByUser_UserIdAndStatusOrderByRemindAtAsc(UUID userId, ReminderStatus status);
    List<ReminderEntity> findByStatusAndRemindAtBefore(ReminderStatus status, OffsetDateTime time);
}
