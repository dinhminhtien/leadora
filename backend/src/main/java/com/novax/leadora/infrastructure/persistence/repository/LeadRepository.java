package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.LeadStatusCount;
import com.novax.leadora.application.usecase.chat.dto.RepLeadCount;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.specification.LeadSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadRepository
        extends JpaRepository<LeadEntity, UUID>,
                JpaSpecificationExecutor<LeadEntity> {

    // ── Single-entity fetch with associations ─────────────────────────────────

    @EntityGraph(attributePaths = {"assignedUser", "createdBy", "customer"})
    @Query("SELECT l FROM LeadEntity l WHERE l.leadId = :leadId")
    Optional<LeadEntity> findWithUsersById(@Param("leadId") UUID leadId);

    // ── Search (delegates to LeadSpecification) ──────────────────────────

    /**
     * Filtered, paginated search with standard column-based sort from {@code pageable}.
     */
    default Page<LeadEntity> searchLeads(
            String search, LeadStatus status, String source, Boolean isCorporate,
            OffsetDateTime dateFrom, OffsetDateTime dateTo,
            boolean unscoped, UUID ownerId, boolean createdByMe, Pageable pageable
    ) {
        return findAll(LeadSpecification.filter(search, status, source, isCorporate,
                dateFrom, dateTo, unscoped, ownerId, createdByMe), pageable);
    }

    /**
     * Same filters, but ordered by pipeline status priority
     * (Converted → Qualified → Contacted → New → Lost), then {@code createdAt} DESC.
     *
     * <p>Status is stored as STRING so alphabetical DB ordering is meaningless.
     * Priority is applied in-memory after fetching the filtered result set;
     * acceptable for typical CRM volumes (&lt; 50k leads).
     */
    default Page<LeadEntity> searchLeadsByStatusPriority(
            String search, LeadStatus status, String source, Boolean isCorporate,
            OffsetDateTime dateFrom, OffsetDateTime dateTo,
            boolean unscoped, UUID ownerId, boolean createdByMe, Pageable pageable
    ) {
        List<LeadEntity> all = findAll(
                LeadSpecification.filter(search, status, source, isCorporate,
                        dateFrom, dateTo, unscoped, ownerId, createdByMe),
                Sort.unsorted()
        );

        List<LeadEntity> sorted = all.stream()
                .sorted(LeadSpecification.STATUS_PRIORITY_COMPARATOR)
                .toList();

        int total   = sorted.size();
        int offset  = (int) pageable.getOffset();
        int size    = pageable.getPageSize();
        return new PageImpl<>(
                sorted.subList(Math.min(offset, total), Math.min(offset + size, total)),
                pageable,
                total
        );
    }

    // ── Duplicate detection (UC-8.1) ──────────────────────────────────────────
    // Newest match wins so the UI can deep-link to the most recent existing lead.

    Optional<LeadEntity> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    Optional<LeadEntity> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    // ── Assignment & status helpers ───────────────────────────────────────────

    @EntityGraph(attributePaths = {"assignedUser"})
    List<LeadEntity> findByAssignedUser_UserId(UUID assignedUserId);

    List<LeadEntity> findByStatus(LeadStatus status);

    long countByStatus(LeadStatus status);

    // ── Performance report query (eliminates N+1 and filters at DB level) ──
    @EntityGraph(attributePaths = {"assignedUser"})
    @Query("""
            SELECT l FROM LeadEntity l
            WHERE l.createdAt >= :startDate
              AND l.createdAt <= :endDate
            """)
    List<LeadEntity> findByCreatedAtRange(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────
    // A null :userId means "every lead" (Manager/Admin scope); a non-null value restricts to that
    // user's records. One parameterised query serves both scopes, so the BR-36 scope filter is
    // always applied in SQL and can never be forgotten by a caller branching in Java.

    @Query("""
            SELECT new com.novax.leadora.application.usecase.chat.dto.LeadStatusCount(l.status, COUNT(l))
            FROM LeadEntity l
            WHERE (:userId IS NULL OR l.assignedUser.userId = :userId)
            GROUP BY l.status
            """)
    List<LeadStatusCount> countByStatusForChat(@Param("userId") UUID userId);

    /** Newest leads only — the assistant lists at most a couple of dozen, so never fetch more. */
    @EntityGraph(attributePaths = {"assignedUser"})
    @Query("""
            SELECT l FROM LeadEntity l
            WHERE (:userId IS NULL OR l.assignedUser.userId = :userId)
            ORDER BY l.createdAt DESC
            """)
    List<LeadEntity> findRecentForChat(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Lead counts per assignee, for the "ask about someone else's leads instead" suggestion.
     * Callers must gate this on the caller being allowed to see all records (BR-36).
     */
    @Query("""
            SELECT new com.novax.leadora.application.usecase.chat.dto.RepLeadCount(u.fullName, COUNT(l))
            FROM LeadEntity l JOIN l.assignedUser u
            GROUP BY u.fullName
            ORDER BY COUNT(l) DESC
            """)
    List<RepLeadCount> countPerAssignee(Pageable pageable);
}