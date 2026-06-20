package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
    List<CustomerEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<CustomerEntity> findByStatus(CustomerStatus status);
    Optional<CustomerEntity> findFirstByEmail(String email);
    Optional<CustomerEntity> findFirstByFullName(String fullName);

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
}
