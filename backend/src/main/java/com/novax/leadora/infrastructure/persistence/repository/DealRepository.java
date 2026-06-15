package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DealRepository extends JpaRepository<DealEntity, UUID> {
    List<DealEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<DealEntity> findByCustomer_CustomerId(UUID customerId);
    List<DealEntity> findByStatus(DealStatus status);
}
