package com.novax.leadora.application.usecase;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManageDealUseCase {

    private final DealRepository dealRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<DealResponse> getAllDeals() {
        return dealRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DealResponse getDealById(UUID id) {
        DealEntity deal = dealRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found with ID: " + id));
        return mapToResponse(deal);
    }

    @Transactional
    public DealResponse createDeal(DealRequest request) {
        // Find or create customer
        CustomerEntity customer = null;
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            customer = customerRepository.findFirstByEmail(request.getEmail().trim()).orElse(null);
        }
        if (customer == null) {
            customer = customerRepository.findFirstByFullName(request.getContactName().trim()).orElse(null);
        }
        if (customer == null) {
            customer = CustomerEntity.builder()
                    .fullName(request.getContactName())
                    .email(request.getEmail())
                    .phone(request.getPhone())
                    .customerType(CustomerType.INDIVIDUAL)
                    .status(CustomerStatus.ACTIVE)
                    .build();
            customer = customerRepository.save(customer);
        }

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

        DealEntity deal = DealEntity.builder()
                .dealName(request.getTitle())
                .customer(customer)
                .assignedUser(owner)
                .pipelineStage(mapStageToEnum(request.getStage()))
                .expectedRevenue(request.getValue())
                .expectedCloseDate(request.getExpectedClose())
                .status(mapStatusToEnum(request.getStatus()))
                .notes(request.getNotes())
                .createdBy(owner)
                .build();

        DealEntity savedDeal = dealRepository.save(deal);
        return mapToResponse(savedDeal);
    }

    @Transactional
    public DealResponse updateDeal(UUID id, DealRequest request) {
        DealEntity deal = dealRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found with ID: " + id));

        deal.setDealName(request.getTitle());

        // Update customer details if they changed
        CustomerEntity customer = deal.getCustomer();
        if (customer != null) {
            customer.setFullName(request.getContactName());
            if (request.getEmail() != null) customer.setEmail(request.getEmail());
            if (request.getPhone() != null) customer.setPhone(request.getPhone());
            customerRepository.save(customer);
        }

        if (request.getValue() != null) {
            deal.setExpectedRevenue(request.getValue());
        }
        if (request.getStage() != null) {
            deal.setPipelineStage(mapStageToEnum(request.getStage()));
        }
        if (request.getStatus() != null) {
            deal.setStatus(mapStatusToEnum(request.getStatus()));
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
        return mapToResponse(updatedDeal);
    }

    @Transactional
    public DealResponse updateDealStatus(UUID id, String status) {
        DealEntity deal = dealRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found with ID: " + id));

        DealStatus enumStatus = mapStatusToEnum(status);
        deal.setStatus(enumStatus);

        if (enumStatus == DealStatus.WON) {
            deal.setPipelineStage(DealPipelineStage.CLOSED_WON);
        } else if (enumStatus == DealStatus.LOST) {
            deal.setPipelineStage(DealPipelineStage.CLOSED_LOST);
        }

        DealEntity updatedDeal = dealRepository.save(deal);
        return mapToResponse(updatedDeal);
    }

    private DealResponse mapToResponse(DealEntity deal) {
        String contactName = deal.getCustomer() != null ? deal.getCustomer().getFullName() : "N/A";
        String email = deal.getCustomer() != null ? deal.getCustomer().getEmail() : "";
        String phone = deal.getCustomer() != null ? deal.getCustomer().getPhone() : "";
        String ownerName = deal.getAssignedUser() != null ? deal.getAssignedUser().getFullName() : "Unassigned";

        return DealResponse.builder()
                .id(deal.getDealId())
                .title(deal.getDealName())
                .contactName(contactName)
                .email(email)
                .phone(phone)
                .value(deal.getExpectedRevenue())
                .probability(calculateProbability(deal.getPipelineStage(), deal.getStatus()))
                .stage(mapStageToString(deal.getPipelineStage()))
                .owner(ownerName)
                .status(mapStatusToString(deal.getStatus()))
                .expectedClose(deal.getExpectedCloseDate())
                .createdAt(deal.getCreatedAt() != null ? deal.getCreatedAt().toLocalDate() : java.time.LocalDate.now())
                .build();
    }

    private DealPipelineStage mapStageToEnum(String stage) {
        if (stage == null) return DealPipelineStage.PROSPECTING;
        switch (stage.toLowerCase()) {
            case "inquiry": return DealPipelineStage.PROSPECTING;
            case "site visit": return DealPipelineStage.QUALIFICATION;
            case "proposal": return DealPipelineStage.PROPOSAL;
            case "negotiation": return DealPipelineStage.NEGOTIATION;
            case "contract": return DealPipelineStage.CLOSED_WON;
            case "confirmed": return DealPipelineStage.CLOSED_WON;
            default:
                try {
                    return DealPipelineStage.valueOf(stage.toUpperCase());
                } catch (Exception e) {
                    return DealPipelineStage.PROSPECTING;
                }
        }
    }

    private String mapStageToString(DealPipelineStage stage) {
        if (stage == null) return "Inquiry";
        switch (stage) {
            case PROSPECTING: return "Inquiry";
            case QUALIFICATION: return "Site Visit";
            case PROPOSAL: return "Proposal";
            case NEGOTIATION: return "Negotiation";
            case CLOSED_WON: return "Confirmed";
            case CLOSED_LOST: return "Confirmed";
            default: return "Inquiry";
        }
    }

    private DealStatus mapStatusToEnum(String status) {
        if (status == null) return DealStatus.OPEN;
        switch (status.toLowerCase()) {
            case "active": return DealStatus.OPEN;
            case "won": return DealStatus.WON;
            case "lost": return DealStatus.LOST;
            default:
                try {
                    return DealStatus.valueOf(status.toUpperCase());
                } catch (Exception e) {
                    return DealStatus.OPEN;
                }
        }
    }

    private String mapStatusToString(DealStatus status) {
        if (status == null) return "active";
        switch (status) {
            case OPEN: return "active";
            case WON: return "won";
            case LOST: return "lost";
            default: return "active";
        }
    }

    private int calculateProbability(DealPipelineStage stage, DealStatus status) {
        if (status == DealStatus.WON) return 100;
        if (status == DealStatus.LOST) return 0;
        if (stage == null) return 50;
        switch (stage) {
            case PROSPECTING: return 10;
            case QUALIFICATION: return 30;
            case PROPOSAL: return 50;
            case NEGOTIATION: return 70;
            case CLOSED_WON: return 100;
            case CLOSED_LOST: return 0;
            default: return 50;
        }
    }
}
