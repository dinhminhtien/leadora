package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class DealValidation {

    public void validateStageTransition(DealPipelineStage currentStage, DealPipelineStage targetStage, DealEntity deal, DealRequest request) {
        if (currentStage == targetStage) {
            return;
        }

        int currentIdx = getStageOrder(currentStage);
        int targetIdx = getStageOrder(targetStage);

        if (targetIdx > currentIdx) {
            for (int i = currentIdx + 1; i <= targetIdx; i++) {
                validateStep(i, deal, request);
            }
        }
    }

    private int getStageOrder(DealPipelineStage stage) {
        if (stage == null) {
            return 0;
        }
        switch (stage) {
            case PROSPECTING:
                return 0;
            case QUALIFICATION:
                return 1;
            case PROPOSAL:
                return 2;
            case NEGOTIATION:
                return 3;
            case CLOSED_WON:
                return 4;
            case CLOSED_LOST:
                return 4;
            default:
                return 0;
        }
    }

    private void validateStep(int stepIndex, DealEntity deal, DealRequest request) {
        switch (stepIndex) {
            case 1: // Site Visit (QUALIFICATION)
                String email = request.getEmail() != null ? request.getEmail().trim() : "";
                String phone = request.getPhone() != null ? request.getPhone().trim() : "";
                if (email.isEmpty() && phone.isEmpty() && deal.getCustomer() != null) {
                    email = deal.getCustomer().getEmail() != null ? deal.getCustomer().getEmail().trim() : "";
                    phone = deal.getCustomer().getPhone() != null ? deal.getCustomer().getPhone().trim() : "";
                }
                if (email.isEmpty() && phone.isEmpty()) {
                    throw new BusinessRuleException(
                            "A Phone number or Email address is required to coordinate a Site Visit.");
                }
                break;

            case 2: // Proposal (PROPOSAL)
                BigDecimal value = request.getValue();
                if (value == null) {
                    value = deal.getExpectedRevenue();
                }
                if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessRuleException("A deal value greater than $0 is required to generate a Proposal.");
                }
                break;

            case 3: // Negotiation (NEGOTIATION)
                String notes = request.getNotes() != null ? request.getNotes().trim() : "";
                if (notes.isEmpty() && deal.getNotes() != null) {
                    notes = deal.getNotes().trim();
                }
                if (notes.length() < 5) {
                    throw new BusinessRuleException(
                            "Please fill in Notes/Details (at least 5 characters) about guest requirements before starting Negotiation.");
                }
                break;

            case 4: // Contract / Confirmed (CLOSED_WON)
                LocalDate closeDate = request.getExpectedClose();
                if (closeDate == null) {
                    closeDate = deal.getExpectedCloseDate();
                }
                if (closeDate == null) {
                    throw new BusinessRuleException("An Estimated Close Date must be set before drafting a Contract.");
                }
                break;
        }
    }
}
