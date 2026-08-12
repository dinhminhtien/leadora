package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.application.event.ContractActivatedEvent;
import com.novax.leadora.application.event.ContractSupersededEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivateContractUseCase {

    private final ContractRepository contractRepository;
    private final ActivityLogPublisher activityLogPublisher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Activates an ACKNOWLEDGED contract. Older active or acknowledged contracts
     * for the same deal are automatically set to SUPERSEDED.
     */
    @Transactional
    public ContractEntity execute(UUID contractId) {
        log.info("Activating contract id: {}", contractId);

        ContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "Contract not found", org.springframework.http.HttpStatus.NOT_FOUND));

        if (contract.getStatus() != ContractStatus.ACKNOWLEDGED) {
            throw new BusinessException("INVALID_CONTRACT_STATUS", 
                    "Only ACKNOWLEDGED contracts can be activated. Current status: " + contract.getStatus(), org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // 1. Supersede any other active or acknowledged contracts for the same deal
        List<ContractEntity> olderContracts = contractRepository.findByDeal_DealId(contract.getDeal().getDealId());
        for (ContractEntity older : olderContracts) {
            if (!older.getId().equals(contractId) && 
                    (older.getStatus() == ContractStatus.ACTIVE || older.getStatus() == ContractStatus.ACKNOWLEDGED)) {
                
                log.info("Superseding contract: {} version {}", older.getContractCode(), older.getVersion());
                older.setStatus(ContractStatus.SUPERSEDED);
                contractRepository.save(older);

                activityLogPublisher.publish(
                        ActivityLogType.CONTRACT_SUPERSEDED,
                        EntityType.CONTRACT,
                        older.getId(),
                        "Contract " + older.getContractCode() + " superseded by " + contract.getContractCode(),
                        null
                );
                eventPublisher.publishEvent(new ContractSupersededEvent(older));
            }
        }

        // 2. Set contract status to ACTIVE
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setEffectiveDate(java.time.OffsetDateTime.now());
        contract = contractRepository.save(contract);

        log.info("Contract {} successfully activated.", contract.getContractCode());

        // Publish activity
        activityLogPublisher.publish(
                ActivityLogType.CONTRACT_ACTIVATED,
                EntityType.CONTRACT,
                contract.getId(),
                "Contract " + contract.getContractCode() + " activated",
                null
        );

        // Publish event
        eventPublisher.publishEvent(new ContractActivatedEvent(contract));

        return contract;
    }
}
