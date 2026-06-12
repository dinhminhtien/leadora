package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<LeadEntity, UUID> {
    List<LeadEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<LeadEntity> findByStatus(LeadStatus status);
}
