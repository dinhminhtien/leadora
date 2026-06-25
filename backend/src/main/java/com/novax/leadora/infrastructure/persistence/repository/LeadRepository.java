package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    // ── Search (delegates to Spec inner class below) ──────────────────────────

    /**
     * Filtered, paginated search with standard column-based sort from {@code pageable}.
     */
    default Page<LeadEntity> searchLeads(
            String search, LeadStatus status, String source, Boolean isCorporate,
            OffsetDateTime dateFrom, OffsetDateTime dateTo,
            boolean unscoped, UUID ownerId, Pageable pageable
    ) {
        return findAll(Spec.filter(search, status, source, isCorporate,
                dateFrom, dateTo, unscoped, ownerId), pageable);
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
            boolean unscoped, UUID ownerId, Pageable pageable
    ) {
        List<LeadEntity> all = findAll(
                Spec.filter(search, status, source, isCorporate,
                        dateFrom, dateTo, unscoped, ownerId),
                Sort.unsorted()
        );

        List<LeadEntity> sorted = all.stream()
                .sorted(Spec.STATUS_PRIORITY_COMPARATOR)
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

    // ── Specification (filter logic lives here, not duplicated in JPQL) ───────

    /**
     * Static inner class so filter logic stays in one place — no separate file,
     * no copy-pasted WHERE clause across query / countQuery variants.
     *
     * <p><b>Supabase pooler / 42P18:</b> null params simply skip the predicate,
     * so no untyped bind variables ({@code :param IS NULL}) reach the driver.
     */
    final class Spec {

        private Spec() {}

        /** Pipeline priority: higher value → shown first. LOST / unknown → 0. */
        private static final Map<LeadStatus, Integer> PRIORITY = Map.of(
                LeadStatus.CONVERTED, 4,
                LeadStatus.QUALIFIED, 3,
                LeadStatus.CONTACTED, 2,
                LeadStatus.NEW,       1
        );

        static final Comparator<LeadEntity> STATUS_PRIORITY_COMPARATOR =
                Comparator.comparingInt((LeadEntity l) ->
                                PRIORITY.getOrDefault(l.getStatus(), 0))
                        .reversed()
                        .thenComparing(Comparator.comparing(LeadEntity::getCreatedAt).reversed());

        /**
         * Builds a single AND-combined {@link Specification} from the active filters.
         * Null / blank parameters are skipped — they contribute no predicate.
         */
        public static Specification<LeadEntity> filter(
                String search,
                LeadStatus status,
                String source,
                Boolean isCorporate,
                OffsetDateTime dateFrom,
                OffsetDateTime dateTo,
                boolean unscoped,
                UUID ownerId
        ) {
            return (root, query, cb) -> {

                // Fetch associations for data queries; skip for count queries.
                if (Long.class != query.getResultType()) {
                    root.fetch("assignedUser", JoinType.LEFT);
                    root.fetch("createdBy",    JoinType.LEFT);
                    root.fetch("customer",     JoinType.LEFT);
                }

                List<Predicate> predicates = new ArrayList<>();

                // Free-text: fullName, email, companyName
                if (search != null && !search.isBlank()) {
                    String pattern = "%" + search.toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("fullName")),    pattern),
                            cb.like(cb.lower(root.get("email")),       pattern),
                            cb.like(cb.lower(root.get("companyName")), pattern)
                    ));
                }

                if (status     != null)              predicates.add(cb.equal(root.get("status"),      status));
                if (source     != null && !source.isBlank()) predicates.add(cb.equal(root.get("source"), source));
                if (isCorporate != null)             predicates.add(cb.equal(root.get("isCorporate"), isCorporate));
                if (dateFrom   != null)              predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
                if (dateTo     != null)              predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"),   dateTo));

                // Owner scope: skip when unscoped (admin/manager); otherwise
                // the lead must be assigned to OR created by the caller.
                if (!unscoped && ownerId != null) {
                    var auJoin = root.join("assignedUser", JoinType.LEFT);
                    var cbJoin = root.join("createdBy",    JoinType.LEFT);
                    predicates.add(cb.or(
                            cb.equal(auJoin.get("userId"), ownerId),
                            cb.equal(cbJoin.get("userId"), ownerId)
                    ));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };
        }
    }
}