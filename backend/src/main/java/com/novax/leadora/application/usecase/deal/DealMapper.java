package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DealMapper {

    public DealResponse mapToResponse(DealEntity deal) {
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
                .stage(mapStageToString(deal.getPipelineStage(), deal.getStatus()))
                .stageCode(deal.getPipelineStage())
                .owner(ownerName)
                .status(mapStatusToString(deal.getStatus()))
                .expectedClose(deal.getExpectedCloseDate())
                .createdAt(deal.getCreatedAt() != null ? deal.getCreatedAt().toLocalDate() : LocalDate.now())
                .notes(deal.getNotes())
                .build();
    }

    public DealPipelineStage mapStageToEnum(String stage) {
        if (stage == null) {
            return DealPipelineStage.PROSPECTING;
        }
        switch (stage.toLowerCase()) {
            case "inquiry":
                return DealPipelineStage.PROSPECTING;
            case "site visit":
                return DealPipelineStage.QUALIFICATION;
            case "proposal":
                return DealPipelineStage.PROPOSAL;
            case "negotiation":
                return DealPipelineStage.NEGOTIATION;
            case "contract":
                return DealPipelineStage.CLOSED_WON;
            case "confirmed":
                return DealPipelineStage.CLOSED_WON;
            default:
                try {
                    return DealPipelineStage.valueOf(stage.toUpperCase());
                } catch (Exception e) {
                    return DealPipelineStage.PROSPECTING;
                }
        }
    }

    public String mapStageToString(DealPipelineStage stage, DealStatus status) {
        if (stage == null) {
            return "Inquiry";
        }
        switch (stage) {
            case PROSPECTING:
                return "Inquiry";
            case QUALIFICATION:
                return "Site Visit";
            case PROPOSAL:
                return "Proposal";
            case NEGOTIATION:
                return "Negotiation";
            case CLOSED_WON:
                if (status == DealStatus.WON) {
                    return "Confirmed";
                } else {
                    return "Contract";
                }
            case CLOSED_LOST:
                return "Confirmed";
            default:
                return "Inquiry";
        }
    }

    public DealStatus mapStatusToEnum(String status) {
        if (status == null) {
            return DealStatus.OPEN;
        }
        switch (status.toLowerCase()) {
            case "active":
                return DealStatus.OPEN;
            case "won":
                return DealStatus.WON;
            case "lost":
                return DealStatus.LOST;
            default:
                try {
                    return DealStatus.valueOf(status.toUpperCase());
                } catch (Exception e) {
                    return DealStatus.OPEN;
                }
        }
    }

    public String mapStatusToString(DealStatus status) {
        if (status == null) {
            return "active";
        }
        switch (status) {
            case OPEN:
                return "active";
            case WON:
                return "won";
            case LOST:
                return "lost";
            default:
                return "active";
        }
    }

    public int calculateProbability(DealPipelineStage stage, DealStatus status) {
        if (status == DealStatus.WON) {
            return 100;
        }
        if (status == DealStatus.LOST) {
            return 0;
        }
        if (stage == null) {
            return 50;
        }
        switch (stage) {
            case PROSPECTING:
                return 10;
            case QUALIFICATION:
                return 30;
            case PROPOSAL:
                return 50;
            case NEGOTIATION:
                return 70;
            case CLOSED_WON:
                return 100;
            case CLOSED_LOST:
                return 0;
            default:
                return 50;
        }
    }
}
