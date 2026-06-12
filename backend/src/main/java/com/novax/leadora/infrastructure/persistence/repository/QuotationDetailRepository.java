package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuotationDetailRepository extends JpaRepository<QuotationDetailEntity, UUID> {
    List<QuotationDetailEntity> findByQuotation_QuotationId(UUID quotationId);
}
