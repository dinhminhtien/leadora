package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
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
public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

    List<CustomerEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<CustomerEntity> findByStatus(CustomerStatus status);
    long countByStatus(CustomerStatus status);
    long countByCustomerType(CustomerType customerType);
    Optional<CustomerEntity> findFirstByEmail(String email);
    Optional<CustomerEntity> findFirstByPhone(String phone);
    Optional<CustomerEntity> findFirstByFullName(String fullName);

    /** Lightweight autocomplete search — no relation joins needed. */
    @Query(value = """
            SELECT c FROM CustomerEntity c
            WHERE (:search = '' OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(c) FROM CustomerEntity c
            WHERE (:search = '' OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<CustomerEntity> searchCustomers(@Param("search") String search, Pageable pageable);

    /** Full paginated list with optional type/status filters and eager user fetch. */
    @EntityGraph(attributePaths = {"assignedUser", "createdBy"})
    @Query(value = """
            SELECT c FROM CustomerEntity c
            WHERE (:search = '' OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:customerType IS NULL OR c.customerType = :customerType)
              AND (:status IS NULL OR c.status = :status)
            """,
            countQuery = """
            SELECT COUNT(c) FROM CustomerEntity c
            WHERE (:search = '' OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:customerType IS NULL OR c.customerType = :customerType)
              AND (:status IS NULL OR c.status = :status)
            """)
    Page<CustomerEntity> searchCustomersFiltered(
            @Param("search") String search,
            @Param("customerType") CustomerType customerType,
            @Param("status") CustomerStatus status,
            Pageable pageable);

    /** Detail fetch — eagerly loads user relations to avoid N+1. */
    @EntityGraph(attributePaths = {"assignedUser", "createdBy"})
    @Query("SELECT c FROM CustomerEntity c WHERE c.customerId = :id")
    Optional<CustomerEntity> findByIdWithUsers(@Param("id") UUID id);
}
