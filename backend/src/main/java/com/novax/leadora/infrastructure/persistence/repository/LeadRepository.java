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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<LeadEntity, UUID> {

    @EntityGraph(attributePaths = {"assignedUser", "createdBy", "customer"})
    @Query("SELECT l FROM LeadEntity l WHERE l.leadId = :leadId")
    Optional<LeadEntity> findWithUsersById(@Param("leadId") UUID leadId);

    @EntityGraph(attributePaths = {"assignedUser", "createdBy", "customer"})
    @Query(value = """
        SELECT l FROM LeadEntity l
        WHERE (:search = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR l.status = :status)
          AND (:source = '' OR l.source = :source)
          AND (:isCorporate IS NULL OR l.isCorporate = :isCorporate)
        """,
        countQuery = """
        SELECT COUNT(l) FROM LeadEntity l
        WHERE (:search = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR l.status = :status)
          AND (:source = '' OR l.source = :source)
          AND (:isCorporate IS NULL OR l.isCorporate = :isCorporate)
        """)
    Page<LeadEntity> searchLeads(
            @Param("search") String search,
            @Param("status") LeadStatus status,
            @Param("source") String source,
            @Param("isCorporate") Boolean isCorporate,
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
        WHERE (:search = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR l.status = :status)
          AND (:source = '' OR l.source = :source)
          AND (:isCorporate IS NULL OR l.isCorporate = :isCorporate)
        ORDER BY CASE l.status
                    WHEN com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus.CONVERTED THEN 4
                    WHEN com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus.QUALIFIED THEN 3
                    WHEN com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus.CONTACTED THEN 2
                    WHEN com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus.NEW THEN 1
                    ELSE 0 END DESC, l.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(l) FROM LeadEntity l
        WHERE (:search = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(l.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR l.status = :status)
          AND (:source = '' OR l.source = :source)
          AND (:isCorporate IS NULL OR l.isCorporate = :isCorporate)
        """)
    Page<LeadEntity> searchLeadsByStatusPriority(
            @Param("search") String search,
            @Param("status") LeadStatus status,
            @Param("source") String source,
            @Param("isCorporate") Boolean isCorporate,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"assignedUser"})
    List<LeadEntity> findByAssignedUser_UserId(UUID assignedUserId);

    List<LeadEntity> findByStatus(LeadStatus status);

    long countByStatus(LeadStatus status);
}
