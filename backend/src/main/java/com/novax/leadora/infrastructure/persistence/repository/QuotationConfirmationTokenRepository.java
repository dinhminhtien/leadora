package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.QuotationConfirmationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotationConfirmationTokenRepository extends JpaRepository<QuotationConfirmationTokenEntity, UUID> {
    Optional<QuotationConfirmationTokenEntity> findByQuotationId(UUID quotationId);
    void deleteByQuotationId(UUID quotationId);
}
