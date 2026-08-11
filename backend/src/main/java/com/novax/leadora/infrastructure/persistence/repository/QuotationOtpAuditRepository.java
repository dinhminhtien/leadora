package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.QuotationOtpAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuotationOtpAuditRepository extends JpaRepository<QuotationOtpAuditEntity, UUID> {
    List<QuotationOtpAuditEntity> findByQuotationId(UUID quotationId);
}
