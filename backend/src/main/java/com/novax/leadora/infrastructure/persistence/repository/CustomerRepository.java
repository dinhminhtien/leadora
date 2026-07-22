package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.CustomerStatusCount;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID>, JpaSpecificationExecutor<CustomerEntity> {

    // ── Paginated list (dynamic filtering via Specification) ───────────────

    /**
     * Overrides JpaSpecificationExecutor to apply the customer EntityGraph on every
     * paginated Specification query. Associations are @ManyToOne, so no in-memory
     * pagination issues. Sort is driven by the Pageable passed from the use case.
     */
    @Override
    @EntityGraph(attributePaths = {"assignedUser", "createdBy"})
    Page<CustomerEntity> findAll(Specification<CustomerEntity> spec, Pageable pageable);

    // ── Lightweight autocomplete (no relation joins needed) ────────────────

    @Query("""
            SELECT c FROM CustomerEntity c
            WHERE (:search = '' OR LOWER(c.fullName)    LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.email)       LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.phone)       LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.createdAt DESC
            """)
    Page<CustomerEntity> searchCustomers(@Param("search") String search, Pageable pageable);

    // ── Single entity fetch ────────────────────────────────────────────────

    @EntityGraph(attributePaths = {"assignedUser", "createdBy"})
    @Query("SELECT c FROM CustomerEntity c WHERE c.customerId = :id")
    Optional<CustomerEntity> findByIdWithUsers(@Param("id") UUID id);

    // ── Lightweight relation lookups (no eager load required) ─────────────

    List<CustomerEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<CustomerEntity> findByStatus(CustomerStatus status);
    long countByStatus(CustomerStatus status);
    long countByCustomerType(CustomerType customerType);
    Optional<CustomerEntity> findFirstByEmail(String email);
    Optional<CustomerEntity> findFirstByPhone(String phone);
    Optional<CustomerEntity> findFirstByFullName(String fullName);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────

    @Query("""
            SELECT new com.novax.leadora.application.usecase.chat.dto.CustomerStatusCount(
                       c.status, COUNT(c))
            FROM CustomerEntity c
            WHERE (:userId IS NULL OR c.assignedUser.userId = :userId)
            GROUP BY c.status
            """)
    List<CustomerStatusCount> countByStatusForChat(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"assignedUser"})
    @Query("""
            SELECT c FROM CustomerEntity c
            WHERE (:userId IS NULL OR c.assignedUser.userId = :userId)
            ORDER BY c.createdAt DESC
            """)
    List<CustomerEntity> findRecentForChat(@Param("userId") UUID userId, Pageable pageable);
}
