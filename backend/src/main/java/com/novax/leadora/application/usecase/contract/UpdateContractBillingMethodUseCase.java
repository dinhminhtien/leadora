package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BillingMethod;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateContractBillingMethodUseCase {

    private final ContractRepository contractRepository;
    private final ContractAccessPolicy contractAccessPolicy;

    @Transactional
    public ContractEntity execute(UUID contractId, BillingMethod billingMethod) {
        log.info("Updating billing method to {} for contract id: {}", billingMethod, contractId);

        ContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "Contract not found", org.springframework.http.HttpStatus.NOT_FOUND));

        // Access check
        contractAccessPolicy.assertCanView(contractAccessPolicy.currentUser(), contract);

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BusinessException("INVALID_CONTRACT_STATUS", 
                    "Billing method can only be updated for contracts in DRAFT status. Current status: " + contract.getStatus(), org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        contract.setBillingMethod(billingMethod);
        return contractRepository.save(contract);
    }
}
