package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.AiChatSessionEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSessionEntity, UUID> {
    List<AiChatSessionEntity> findByUser_UserIdAndStatusOrderByUpdatedAtDesc(UUID userId, ChatSessionStatus status);
}
