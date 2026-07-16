package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateDealUseCase {

    private final DealRepository dealRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final DealMapper dealMapper;
    private final DealValidation dealValidation;

    @Transactional
    public DealResponse execute(DealRequest request) {
        if (request.getCustomerId() == null) {
            throw new IllegalArgumentException(
                    "A Customer ID is required to create a Deal. Convert a Lead first.");
        }
        CustomerEntity customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.getCustomerId()));

        // Find assigned user if owner specified
        UserEntity owner = null;
        if (request.getOwner() != null && !request.getOwner().trim().isEmpty()) {
            owner = userRepository.findFirstByFullNameIgnoreCase(request.getOwner().trim()).orElse(null);
        }
        if (owner == null) {
            // Fallback to first user in system if exists, or leave null
            List<UserEntity> users = userRepository.findAll();
            if (!users.isEmpty()) {
                owner = users.get(0);
            }
        }

        DealPipelineStage initialStage = dealMapper.mapStageToEnum(request.getStage());
        DealEntity tempDeal = DealEntity.builder()
                .customer(customer)
                .notes(request.getNotes())
                .expectedRevenue(request.getValue())
                .expectedCloseDate(request.getExpectedClose())
                .build();
        dealValidation.validateStageTransition(DealPipelineStage.PROSPECTING, initialStage, tempDeal, request);

        DealEntity deal = DealEntity.builder()
                .dealName(request.getTitle())
                .customer(customer)
                .assignedUser(owner)
                .pipelineStage(initialStage)
                .expectedRevenue(request.getValue())
                .expectedCloseDate(request.getExpectedClose())
                .status(dealMapper.mapStatusToEnum(request.getStatus()))
                .notes(request.getNotes())
                .createdBy(owner)
                .build();

        DealEntity savedDeal = dealRepository.save(deal);
        return dealMapper.mapToResponse(savedDeal);
    }
}
