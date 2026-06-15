package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.SlaRecordEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlaRecordRepository extends JpaRepository<SlaRecordEntity, UUID> {
    Optional<SlaRecordEntity> findByDeal_DealId(UUID dealId);
    List<SlaRecordEntity> findByStatus(SlaStatus status);
}
