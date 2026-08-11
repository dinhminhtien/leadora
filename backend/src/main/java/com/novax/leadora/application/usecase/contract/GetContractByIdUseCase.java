package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetContractByIdUseCase {

    private final ContractRepository contractRepository;
    private final ContractAccessPolicy contractAccessPolicy;

    @Transactional(readOnly = true)
    public ContractEntity execute(UUID contractId) {
        log.info("Getting contract detail for id: {}", contractId);

        ContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "Contract not found with id: " + contractId, org.springframework.http.HttpStatus.NOT_FOUND));

        // Enforce access control policy
        contractAccessPolicy.assertCanView(contractAccessPolicy.currentUser(), contract);

        return contract;
    }
}
