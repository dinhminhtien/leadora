package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.ContractConfirmationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractConfirmationTokenRepository extends JpaRepository<ContractConfirmationTokenEntity, UUID> {

    Optional<ContractConfirmationTokenEntity> findByTokenHashAndUsedAtIsNull(String tokenHash);

    Optional<ContractConfirmationTokenEntity> findByContractId(UUID contractId);

    void deleteByContractId(UUID contractId);
}
