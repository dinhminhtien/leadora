package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
