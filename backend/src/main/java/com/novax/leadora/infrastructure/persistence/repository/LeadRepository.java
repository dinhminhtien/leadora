package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.RepLeadCount;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;

@Repository
public interface LeadRepository
                extends JpaRepository<LeadEntity, UUID>,
                JpaSpecificationExecutor<LeadEntity> {

        @Override
        @EntityGraph(attributePaths = { "assignedUser", "createdBy", "customer" })
        Page<LeadEntity> findAll(Specification<LeadEntity> spec, Pageable pageable);

        @Override
        @EntityGraph(attributePaths = { "assignedUser", "createdBy", "customer" })
        List<LeadEntity> findAll(Specification<LeadEntity> spec, Sort sort);

        @Override
        @EntityGraph(attributePaths = { "assignedUser", "createdBy", "customer" })
        List<LeadEntity> findAll(Specification<LeadEntity> spec);

        // ── Single-entity fetch with associations ─────────────────────────────────

        @EntityGraph(attributePaths = { "assignedUser", "createdBy", "customer" })
        @Query("SELECT l FROM LeadEntity l WHERE l.leadId = :leadId")
        Optional<LeadEntity> findWithUsersById(@Param("leadId") UUID leadId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @EntityGraph(attributePaths = { "assignedUser", "createdBy", "customer" })
        @Query("SELECT l FROM LeadEntity l WHERE l.leadId = :leadId")
        Optional<LeadEntity> findWithUsersByIdForUpdate(@Param("leadId") UUID leadId);

        // ── Search (delegates to LeadSpecification) ──────────────────────────

        /**
         * Filtered, paginated search with standard column-based sort from
         * {@code pageable}.
         *
         * <p>
         * Takes the built {@link Specification} rather than re-listing every filter.
         * The parameter
         * list used to mirror {@code LeadSpecification.filter} field for field — ten
         * positional
         * arguments including three adjacent booleans — repeated at each call site,
         * where swapping two
         * of them compiles cleanly and silently returns the wrong rows.
         * {@link LeadFilterParams}
         * already owns the job of turning a query string into a specification; this
         * only runs it.
         */
        default Page<LeadEntity> searchLeads(Specification<LeadEntity> spec, Pageable pageable) {
                return findAll(spec, pageable);
        }

        /**
         * Same filters, but ordered by pipeline status priority
         * (Converted → Qualified → Contacted → New → Lost), then {@code createdAt}
         * DESC.
         *
         * <p>
         * Status is stored as STRING so alphabetical DB ordering is meaningless.
         * Priority is applied in-memory after fetching the filtered result set;
         * acceptable for typical CRM volumes (&lt; 50k leads).
         */
        default Page<LeadEntity> searchLeadsByStatusPriority(
                        Specification<LeadEntity> spec, Pageable pageable) {
                Specification<LeadEntity> orderedSpec = (root, query, cb) -> {
                        if (query.getResultType() != Long.class) {
                                query.orderBy(
                                                cb.desc(
                                                                cb.selectCase()
                                                                                .when(cb.equal(root.get("status"),
                                                                                                LeadStatus.QUALIFIED),
                                                                                                4)
                                                                                .when(cb.equal(root.get("status"),
                                                                                                LeadStatus.CONTACTED),
                                                                                                3)
                                                                                .when(cb.equal(root.get("status"),
                                                                                                LeadStatus.NEW), 2)
                                                                                .when(cb.equal(root.get("status"),
                                                                                                LeadStatus.CONVERTED),
                                                                                                1)
                                                                                .when(cb.equal(root.get("status"),
                                                                                                LeadStatus.LOST), 0)
                                                                                .otherwise(0)),
                                                cb.desc(root.get("createdAt")));
                        }
                        return spec.toPredicate(root, query, cb);
                };
                return findAll(orderedSpec, pageable);
        }

        // ── Duplicate detection (UC-8.1) ──────────────────────────────────────────
        // Newest match wins so the UI can deep-link to the most recent existing lead.

        Optional<LeadEntity> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

        Optional<LeadEntity> findFirstByPhoneOrderByCreatedAtDesc(String phone);

        // ── Assignment & status helpers ───────────────────────────────────────────

        @EntityGraph(attributePaths = { "assignedUser" })
        List<LeadEntity> findByAssignedUser_UserId(UUID assignedUserId);

        List<LeadEntity> findByStatus(LeadStatus status);

        long countByStatus(LeadStatus status);

        // ── UC-23.1 report aggregates ─────────────────────────────────────────────
        // Counting happens in SQL. The previous form selected whole entities (plus an
        // @EntityGraph join on assignedUser) and counted them with Java streams, so an
        // unbounded date range meant "SELECT * FROM leads" into heap — and the default
        // state of the screen is exactly that unbounded range.
        // Ranges are half-open: [start, end) — see ReportRange.

        /** {@code [status, count]} rows. */
        @Query("""
                        SELECT l.status, count(l) FROM LeadEntity l
                        WHERE l.createdAt >= :start
                          AND l.createdAt < :end
                        GROUP BY l.status
                        """)
        List<Object[]> aggregateByStatus(
                        @Param("start") OffsetDateTime start,
                        @Param("end") OffsetDateTime end);

        /**
         * {@code [ownerId, ownerName, count]} rows. LEFT JOIN so unassigned leads
         * survive as a
         * null-owner group: dropping them is what makes a per-rep table stop
         * reconciling with the
         * headline total.
         */
        @Query("""
                        SELECT u.userId, u.fullName, count(l)
                        FROM LeadEntity l LEFT JOIN l.assignedUser u
                        WHERE l.createdAt >= :start
                          AND l.createdAt < :end
                        GROUP BY u.userId, u.fullName
                        """)
        List<Object[]> aggregateByOwner(
                        @Param("start") OffsetDateTime start,
                        @Param("end") OffsetDateTime end);

        // ── Chat-assistant snapshot ───────────────────────────────────────────────
        // A null :userId means "every lead" (Manager/Admin scope); a non-null value
        // restricts to that user's records. One parameterised query serves both scopes,
        // so the BR-36 scope filter is always applied in SQL and can never be forgotten
        // by a caller branching in Java. The :from/:to bounds are always supplied — an
        // open-ended side is substituted with a sentinel — so both stay sargable.

        /**
         * Newest leads only — the assistant lists at most a couple of dozen, so never
         * fetch more.
         */
        @EntityGraph(attributePaths = { "assignedUser" })
        @Query("""
                        SELECT l FROM LeadEntity l
                        WHERE (:userId IS NULL OR l.assignedUser.userId = :userId)
                          AND l.createdAt >= :from
                          AND l.createdAt <= :to
                        ORDER BY l.createdAt DESC
                        """)
        List<LeadEntity> findRecentForChat(@Param("userId") UUID userId,
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to,
                        Pageable pageable);

        /**
         * Lead counts per assignee, for the "ask about someone else's leads instead"
         * suggestion.
         * Callers must gate this on the caller being allowed to see all records
         * (BR-36).
         */
        @Query("""
                        SELECT new com.novax.leadora.application.usecase.chat.dto.RepLeadCount(u.fullName, COUNT(l))
                        FROM LeadEntity l JOIN l.assignedUser u
                        WHERE l.createdAt >= :from
                          AND l.createdAt <= :to
                        GROUP BY u.fullName
                        ORDER BY COUNT(l) DESC
                        """)
        List<RepLeadCount> countPerAssignee(@Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to, Pageable pageable);

        @Query("SELECT l.leadId FROM LeadEntity l WHERE l.assignedUser.userId = :userId")
        List<UUID> findLeadIdsByAssignedUser_UserId(@Param("userId") UUID userId);
}