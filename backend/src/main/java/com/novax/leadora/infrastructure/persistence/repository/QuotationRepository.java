package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuotationRepository extends JpaRepository<QuotationEntity, UUID> {
    List<QuotationEntity> findByDeal_DealId(UUID dealId);
    List<QuotationEntity> findByCustomer_CustomerId(UUID customerId);
    List<QuotationEntity> findByStatus(QuotationStatus status);
}
