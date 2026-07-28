package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateDealUseCase {

    private final DealRepository dealRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final DealMapper dealMapper;
    private final DealValidation dealValidation;
    private final DealAccessPolicy dealAccessPolicy;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public DealResponse execute(UUID id, DealRequest request) {
        DealEntity deal = dealRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        dealAccessPolicy.assertCanView(dealAccessPolicy.currentUser(), deal);

        if (deal.getStatus() != DealStatus.OPEN) {
            throw new BusinessRuleException("Closed deals cannot be modified.");
        }

        ObjectNode updatePayload = objectMapper.createObjectNode();
        boolean detailsChanged = false;
        boolean stageChanged = false;
        DealPipelineStage oldStage = deal.getPipelineStage();
        DealPipelineStage targetStage = null;

        // Validate stage transition rules before applying updates
        if (request.getStage() != null) {
            targetStage = dealMapper.mapStageToEnum(request.getStage());
            dealValidation.validateStageTransition(deal.getPipelineStage(), targetStage, deal, request);
            if (targetStage != oldStage) {
                deal.setPipelineStage(targetStage);
                updatePayload.put("previousStage", oldStage != null ? oldStage.name() : null);
                updatePayload.put("newStage", targetStage.name());
                stageChanged = true;
            }
        }

        if (request.getTitle() != null && !request.getTitle().equals(deal.getDealName())) {
            updatePayload.put("previousTitle", deal.getDealName());
            updatePayload.put("newTitle", request.getTitle());
            deal.setDealName(request.getTitle());
            detailsChanged = true;
        }

        // Update customer details if they changed
        CustomerEntity customer = deal.getCustomer();
        if (customer != null) {
            if (request.getContactName() != null && !request.getContactName().equals(customer.getFullName())) {
                updatePayload.put("previousContactName", customer.getFullName());
                updatePayload.put("newContactName", request.getContactName());
                customer.setFullName(request.getContactName());
                detailsChanged = true;
            }
            if (request.getEmail() != null && !request.getEmail().equals(customer.getEmail())) {
                updatePayload.put("previousContactEmail", customer.getEmail());
                updatePayload.put("newContactEmail", request.getEmail());
                customer.setEmail(request.getEmail());
                detailsChanged = true;
            }
            if (request.getPhone() != null && !request.getPhone().equals(customer.getPhone())) {
                updatePayload.put("previousContactPhone", customer.getPhone());
                updatePayload.put("newContactPhone", request.getPhone());
                customer.setPhone(request.getPhone());
                detailsChanged = true;
            }
            customerRepository.save(customer);
        }

        if (request.getValue() != null && (deal.getExpectedRevenue() == null || request.getValue().compareTo(deal.getExpectedRevenue()) != 0)) {
            updatePayload.put("previousValue", deal.getExpectedRevenue() != null ? deal.getExpectedRevenue().toString() : null);
            updatePayload.put("newValue", request.getValue().toString());
            deal.setExpectedRevenue(request.getValue());
            detailsChanged = true;
        }
        if (request.getStatus() != null) {
            DealStatus targetStatus = dealMapper.mapStatusToEnum(request.getStatus());
            if (targetStatus != deal.getStatus()) {
                dealValidation.validateStatusTransition(deal.getStatus(), targetStatus, deal, request.getNotes());
                updatePayload.put("previousStatus", deal.getStatus().name());
                updatePayload.put("newStatus", targetStatus.name());
                deal.setStatus(targetStatus);
                detailsChanged = true;
            }
        }
        if (request.getExpectedClose() != null && !request.getExpectedClose().equals(deal.getExpectedCloseDate())) {
            updatePayload.put("previousExpectedClose", deal.getExpectedCloseDate() != null ? deal.getExpectedCloseDate().toString() : null);
            updatePayload.put("newExpectedClose", request.getExpectedClose().toString());
            deal.setExpectedCloseDate(request.getExpectedClose());
            detailsChanged = true;
        }
        if (request.getNotes() != null && !request.getNotes().equals(deal.getNotes())) {
            updatePayload.put("previousNotes", deal.getNotes());
            updatePayload.put("newNotes", request.getNotes());
            deal.setNotes(request.getNotes());
            detailsChanged = true;
        }

        if (request.getOwner() != null && !request.getOwner().trim().isEmpty()) {
            UserEntity owner = resolveOwner(request.getOwner());
            if (owner != null) {
                UserEntity currentAssigned = deal.getAssignedUser();
                boolean isChanging = currentAssigned == null || !currentAssigned.getUserId().equals(owner.getUserId());
                if (isChanging) {
                    UserEntity currentUser = dealAccessPolicy.currentUser();
                    boolean isAssigningToSelf = currentUser != null && currentUser.getUserId().equals(owner.getUserId());
                    if (!isAssigningToSelf) {
                        String role = currentUser != null && currentUser.getRole() != null && currentUser.getRole().getRoleName() != null
                                ? currentUser.getRole().getRoleName().trim().toUpperCase() : "";
                        boolean isManager = "MANAGER".equals(role) || "ADMIN".equals(role);
                        if (!isManager) {
                            throw new BusinessException("ROLE_RESTRICTION", "Only a manager or admin can assign a deal to another user.", HttpStatus.FORBIDDEN);
                        }
                    }
                    updatePayload.put("previousOwnerId", currentAssigned != null ? currentAssigned.getUserId().toString() : null);
                    updatePayload.put("newOwnerId", owner.getUserId().toString());
                    deal.setAssignedUser(owner);
                    detailsChanged = true;
                }
            }
        }

        DealEntity updatedDeal = dealRepository.save(deal);

        if (stageChanged) {
            try {
                activityLogPublisher.publish(
                        ActivityLogType.DEAL_STAGE_UPDATED,
                        EntityType.DEAL,
                        updatedDeal.getDealId(),
                        "Deal pipeline stage updated to " + updatedDeal.getPipelineStage(),
                        updatePayload
                );
            } catch (Exception e) {
                log.warn("Failed to publish deal stage update activity: {}", e.getMessage());
            }
        } else if (detailsChanged) {
            try {
                activityLogPublisher.publish(
                        ActivityLogType.DEAL_UPDATED,
                        EntityType.DEAL,
                        updatedDeal.getDealId(),
                        "Deal details updated",
                        updatePayload
                );
            } catch (Exception e) {
                log.warn("Failed to publish deal update activity: {}", e.getMessage());
            }
        }

        return dealMapper.mapToResponse(updatedDeal);
    }

    @Transactional
    public DealResponse updateDealStatus(UUID id, String status, String notes) {
        DealEntity deal = dealRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        dealAccessPolicy.assertCanView(dealAccessPolicy.currentUser(), deal);

        if (deal.getStatus() != DealStatus.OPEN) {
            throw new BusinessRuleException("Closed deals cannot be modified.");
        }

        DealStatus enumStatus = dealMapper.mapStatusToEnum(status);
        dealValidation.validateStatusTransition(deal.getStatus(), enumStatus, deal, notes);

        DealStatus previousStatus = deal.getStatus();
        deal.setStatus(enumStatus);

        if (notes != null && !notes.trim().isEmpty() && deal.getNotes() == null) {
            deal.setNotes(notes);
        }

        DealEntity updatedDeal = dealRepository.save(deal);

        if (enumStatus != previousStatus) {
            try {
                ObjectNode payload = objectMapper.createObjectNode()
                        .put("previousStatus", previousStatus.name())
                        .put("newStatus", enumStatus.name());
                if (notes != null) {
                    payload.put("notes", notes);
                }
                activityLogPublisher.publish(
                        ActivityLogType.DEAL_UPDATED,
                        EntityType.DEAL,
                        updatedDeal.getDealId(),
                        "Deal status updated from " + previousStatus + " to " + enumStatus,
                        payload
                );
            } catch (Exception e) {
                log.warn("Failed to publish deal status update activity: {}", e.getMessage());
            }
        }

        return dealMapper.mapToResponse(updatedDeal);
    }

    private UserEntity resolveOwner(String ownerInput) {
        if (ownerInput == null || ownerInput.trim().isEmpty()) {
            return null;
        }
        String input = ownerInput.trim();

        // 1. Try parsing as UUID
        try {
            java.util.UUID userId = java.util.UUID.fromString(input);
            return userRepository.findById(userId).orElse(null);
        } catch (IllegalArgumentException e) {
            // Not a UUID, ignore and proceed
        }

        // 2. Try lookup by email
        if (input.contains("@")) {
            return userRepository.findByEmail(input).orElse(null);
        }

        // 3. Fallback to lookup by full name
        return userRepository.findFirstByFullNameIgnoreCase(input).orElse(null);
    }
}
