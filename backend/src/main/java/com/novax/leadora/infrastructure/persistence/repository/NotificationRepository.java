package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID>,
        JpaSpecificationExecutor<NotificationEntity> {
    List<NotificationEntity> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);
    List<NotificationEntity> findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);

    Page<NotificationEntity> findByUser_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<NotificationEntity> findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Manager/Admin aggregate feed — every user's notifications, newest first.
    Page<NotificationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<NotificationEntity> findByIsReadFalseOrderByCreatedAtDesc(Pageable pageable);

    long countByUser_UserIdAndIsReadFalse(UUID userId);
}
