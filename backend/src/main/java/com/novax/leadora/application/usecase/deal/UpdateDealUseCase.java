package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateDealUseCase {

    private final DealRepository dealRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final DealMapper dealMapper;
    private final DealValidation dealValidation;
    private final DealAccessPolicy dealAccessPolicy;

    @Transactional
    public DealResponse execute(UUID id, DealRequest request) {
        DealEntity deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        dealAccessPolicy.assertCanView(dealAccessPolicy.currentUser(), deal);

        if (deal.getStatus() != DealStatus.OPEN) {
            throw new BusinessRuleException("Closed deals cannot be modified.");
        }

        // Validate stage transition rules before applying updates
        if (request.getStage() != null) {
            DealPipelineStage targetStage = dealMapper.mapStageToEnum(request.getStage());
            dealValidation.validateStageTransition(deal.getPipelineStage(), targetStage, deal, request);
            deal.setPipelineStage(targetStage);

            // Auto-assign status based on stage name
            String stageStr = request.getStage().toLowerCase();
            if (stageStr.equals("confirmed")) {
                deal.setStatus(DealStatus.WON);
            } else if (stageStr.equals("contract")) {
                deal.setStatus(DealStatus.OPEN);
            }
        }

        deal.setDealName(request.getTitle());

        // Update customer details if they changed
        CustomerEntity customer = deal.getCustomer();
        if (customer != null) {
            customer.setFullName(request.getContactName());
            if (request.getEmail() != null) {
                customer.setEmail(request.getEmail());
            }
            if (request.getPhone() != null) {
                customer.setPhone(request.getPhone());
            }
            customerRepository.save(customer);
        }

        if (request.getValue() != null) {
            deal.setExpectedRevenue(request.getValue());
        }
        if (request.getStatus() != null) {
            deal.setStatus(dealMapper.mapStatusToEnum(request.getStatus()));
        }
        if (request.getExpectedClose() != null) {
            deal.setExpectedCloseDate(request.getExpectedClose());
        }
        if (request.getNotes() != null) {
            deal.setNotes(request.getNotes());
        }

        if (request.getOwner() != null && !request.getOwner().trim().isEmpty()) {
            UserEntity owner = userRepository.findFirstByFullNameIgnoreCase(request.getOwner().trim()).orElse(null);
            if (owner != null) {
                deal.setAssignedUser(owner);
            }
        }

        DealEntity updatedDeal = dealRepository.save(deal);
        return dealMapper.mapToResponse(updatedDeal);
    }

    @Transactional
    public DealResponse updateDealStatus(UUID id, String status) {
        DealEntity deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        dealAccessPolicy.assertCanView(dealAccessPolicy.currentUser(), deal);

        if (deal.getStatus() != DealStatus.OPEN) {
            throw new BusinessRuleException("Closed deals cannot be modified.");
        }

        DealStatus enumStatus = dealMapper.mapStatusToEnum(status);
        deal.setStatus(enumStatus);

        if (enumStatus == DealStatus.WON) {
            deal.setPipelineStage(DealPipelineStage.CLOSED_WON);
        } else if (enumStatus == DealStatus.LOST) {
            deal.setPipelineStage(DealPipelineStage.CLOSED_LOST);
        }

        DealEntity updatedDeal = dealRepository.save(deal);
        return dealMapper.mapToResponse(updatedDeal);
    }
}
