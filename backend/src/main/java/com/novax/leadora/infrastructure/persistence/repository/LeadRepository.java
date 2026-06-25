package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<LeadEntity, UUID> {

    @EntityGraph(attributePaths = {"assignedUser", "createdBy", "customer"})
    @Query("SELECT l FROM LeadEntity l WHERE l.leadId = :leadId")
    Optional<LeadEntity> findWithUsersById(@Param("leadId") UUID leadId);

    // Date bounds use COALESCE(:param, l.createdAt) and owner uses a typed :unscoped flag
    // instead of ":param IS NULL". A bare "IS NULL" on a null OffsetDateTime/UUID parameter
    // has no column to infer its type from → Postgres "could not determine data type of
    // parameter" (42P18) on the Supabase pooler (jdbc metadata access disabled). Keeping the
    // params inside a typed expression avoids that. dateTo is an inclusive end-of-day bound.
    @EntityGraph(attributePaths = {"assignedUser", "createdBy", "customer"})
    @Query(value = """
        SELECT l FROM LeadEntity l
        LEFT JOIN l.assignedUser au
        LEFT JOIN l.createdBy cb
        WHERE (:search = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR l.status = :status)
          AND (:source = '' OR l.source = :source)
          AND (:isCorporate IS NULL OR l.isCorporate = :isCorporate)
          AND l.createdAt >= COALESCE(:dateFrom, l.createdAt)
          AND l.createdAt <= COALESCE(:dateTo, l.createdAt)
          AND (:unscoped = true OR au.userId = :ownerId OR cb.userId = :ownerId)
        """,
        countQuery = """
        SELECT COUNT(l) FROM LeadEntity l
        LEFT JOIN l.assignedUser au
        LEFT JOIN l.createdBy cb
        WHERE (:search = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR l.status = :status)
          AND (:source = '' OR l.source = :source)
          AND (:isCorporate IS NULL OR l.isCorporate = :isCorporate)
          AND l.createdAt >= COALESCE(:dateFrom, l.createdAt)
          AND l.createdAt <= COALESCE(:dateTo, l.createdAt)
          AND (:unscoped = true OR au.userId = :ownerId OR cb.userId = :ownerId)
        """)
    Page<LeadEntity> searchLeads(
            @Param("search") String search,
            @Param("status") LeadStatus status,
            @Param("source") String source,
            @Param("isCorporate") Boolean isCorporate,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            @Param("unscoped") boolean unscoped,
            @Param("ownerId") UUID ownerId,
            Pageable pageable
    );

    /**
     * Same filters as {@link #searchLeads}, but ordered by status pipeline priority
     * (Converted → Qualified → Contacted → New → Lost) instead of a plain column sort,
     * since the status enum is stored as STRING (alphabetical order would be meaningless).
     * Ordering is fixed high→low, so the Pageable must be unsorted.
     */
    @EntityGraph(attributePaths = {"assignedUser", "createdBy", "customer"})
    @Query(value = """
        SELECT l FROM LeadEntity l
        LEFT JOIN l.assignedUser au
        LEFT JOIN l.createdBy cb
        WHERE (:search = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR l.status = :status)
          AND (:source = '' OR l.source = :source)
          AND (:isCorporate IS NULL OR l.isCorporate = :isCorporate)
          AND l.createdAt >= COALESCE(:dateFrom, l.createdAt)
          AND l.createdAt <= COALESCE(:dateTo, l.createdAt)
          AND (:unscoped = true OR au.userId = :ownerId OR cb.userId = :ownerId)
        ORDER BY CASE l.status
                    WHEN com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus.CONVERTED THEN 4
                    WHEN com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus.QUALIFIED THEN 3
                    WHEN com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus.CONTACTED THEN 2
                    WHEN com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus.NEW THEN 1
                    ELSE 0 END DESC, l.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(l) FROM LeadEntity l
        LEFT JOIN l.assignedUser au
        LEFT JOIN l.createdBy cb
        WHERE (:search = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR l.status = :status)
          AND (:source = '' OR l.source = :source)
          AND (:isCorporate IS NULL OR l.isCorporate = :isCorporate)
          AND l.createdAt >= COALESCE(:dateFrom, l.createdAt)
          AND l.createdAt <= COALESCE(:dateTo, l.createdAt)
          AND (:unscoped = true OR au.userId = :ownerId OR cb.userId = :ownerId)
        """)
    Page<LeadEntity> searchLeadsByStatusPriority(
            @Param("search") String search,
            @Param("status") LeadStatus status,
            @Param("source") String source,
            @Param("isCorporate") Boolean isCorporate,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            @Param("unscoped") boolean unscoped,
            @Param("ownerId") UUID ownerId,
            Pageable pageable
    );

    // ── Duplicate detection (UC-8.1) ───────────────────────────────────────────
    // Newest match wins so the UI can deep-link to the most recent existing lead.

    Optional<LeadEntity> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    Optional<LeadEntity> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    @EntityGraph(attributePaths = {"assignedUser"})
    List<LeadEntity> findByAssignedUser_UserId(UUID assignedUserId);

    List<LeadEntity> findByStatus(LeadStatus status);

    long countByStatus(LeadStatus status);
}
