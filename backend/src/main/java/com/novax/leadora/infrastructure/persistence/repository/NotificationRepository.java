package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);
    List<NotificationEntity> findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);
}
