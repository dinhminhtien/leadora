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

    @EntityGraph(attributePaths = {"assignedUser", "createdBy"})
    @Query("SELECT l FROM LeadEntity l WHERE l.leadId = :leadId")
    Optional<LeadEntity> findWithUsersById(@Param("leadId") UUID leadId);

    @EntityGraph(attributePaths = {"assignedUser", "createdBy"})
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

    @EntityGraph(attributePaths = {"assignedUser"})
    List<LeadEntity> findByAssignedUser_UserId(UUID assignedUserId);

    List<LeadEntity> findByStatus(LeadStatus status);
}
