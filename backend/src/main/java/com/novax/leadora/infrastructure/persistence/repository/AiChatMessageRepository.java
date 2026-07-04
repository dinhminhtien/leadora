package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.AiChatMessageEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessageEntity, UUID> {

    /**
     * Messages of a session, oldest first. A USER and its ASSISTANT reply can share the exact same
     * {@code created_at} (e.g. a guardrail-blocked turn saved back-to-back with no LLM delay, and on
     * platforms where the clock resolution is coarse) — so we tie-break to put the USER turn before
     * the ASSISTANT turn, otherwise the reply could render above the question after a reload.
     */
    @Query("SELECT m FROM AiChatMessageEntity m WHERE m.session.sessionId = :sessionId "
            + "ORDER BY m.createdAt ASC, CASE WHEN m.role = :userRole THEN 0 ELSE 1 END ASC")
    List<AiChatMessageEntity> findSessionMessagesOrdered(@Param("sessionId") UUID sessionId,
                                                         @Param("userRole") ChatRole userRole);
}
