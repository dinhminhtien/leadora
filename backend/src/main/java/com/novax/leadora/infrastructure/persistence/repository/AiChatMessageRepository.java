package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.AiChatMessageEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatRole;
import org.springframework.data.domain.Pageable;
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

    /**
     * The most recent messages of a session, <b>newest first</b> — the prompt only replays the last
     * few turns, so loading the whole transcript on every message wastes a growing amount of work
     * as a conversation gets long. Callers reverse the result to get chronological order.
     *
     * <p>The tie-break is inverted relative to {@link #findSessionMessagesOrdered}: sorting newest
     * first must put the ASSISTANT reply <em>before</em> its USER question when the two share a
     * timestamp, so that reversing yields question-then-answer. Copying the ascending tie-break
     * here would silently swap every same-timestamp pair in the prompt.
     */
    @Query("SELECT m FROM AiChatMessageEntity m WHERE m.session.sessionId = :sessionId "
            + "ORDER BY m.createdAt DESC, CASE WHEN m.role = :userRole THEN 1 ELSE 0 END ASC")
    List<AiChatMessageEntity> findRecentForPrompt(@Param("sessionId") UUID sessionId,
                                                  @Param("userRole") ChatRole userRole,
                                                  Pageable pageable);
}
