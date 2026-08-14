package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetContractListUseCase {

    private final ContractRepository contractRepository;
    private final ContractAccessPolicy contractAccessPolicy;

    @Transactional(readOnly = true)
    public List<ContractEntity> execute() {
        log.info("Retrieving all contracts with access checks");

        UserEntity currentUser = contractAccessPolicy.currentUser();
        UUID ownerId = contractAccessPolicy.listScopeOwnerId(currentUser);

        List<ContractEntity> contracts = contractRepository.findAll(
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );
        if (ownerId != null) {
            contracts = contracts.stream()
                    .filter(c -> c.getCreatedBy() != null && ownerId.equals(c.getCreatedBy().getUserId()))
                    .toList();
        }

        return contracts;
    }
}
