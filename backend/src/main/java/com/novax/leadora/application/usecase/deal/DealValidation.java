package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DealValidation {

    private final BookingRepository bookingRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SystemAuditLogService auditLogService;
    private final DealWorkflowResolver dealWorkflowResolver;

    public void validateStageTransition(DealPipelineStage currentStage, DealPipelineStage targetStage, DealEntity deal, DealRequest request) {
        if (currentStage == targetStage) {
            return;
        }

        // BR-DEAL-WON-01/02: Won Immutability check
        if (currentStage == DealPipelineStage.CLOSED_WON) {
            throw new BusinessException("DEAL_STATE_CONFLICT", "A WON deal is immutable and cannot be transitioned to another stage.", HttpStatus.CONFLICT);
        }

        int currentIdx = getStageOrder(currentStage);
        int targetIdx = getStageOrder(targetStage);

        if (targetIdx > currentIdx) {
            for (int i = currentIdx + 1; i <= targetIdx; i++) {
                if (i == 4) {
                    if (targetStage == DealPipelineStage.CLOSED_WON) {
                        validateStep(i, deal, request);
                    }
                } else {
                    validateStep(i, deal, request);
                }
            }
        }

        if (targetStage == DealPipelineStage.CLOSED_WON) {
            validateClosedWonRules(deal, request.getNotes());
        } else if (targetStage == DealPipelineStage.CLOSED_LOST) {
            validateClosedLostRules(deal, request.getNotes());
        }
    }

    public void validateStatusTransition(DealStatus currentStatus, DealStatus targetStatus, DealEntity deal, String notes) {
        if (currentStatus == targetStatus) {
            return;
        }

        // BR-DEAL-WON-01/02: Won Immutability check
        if (currentStatus == DealStatus.WON) {
            throw new BusinessException("DEAL_STATE_CONFLICT", "A WON deal is immutable and cannot be transitioned to another status.", HttpStatus.CONFLICT);
        }

        if (targetStatus == DealStatus.WON) {
            validateClosedWonRules(deal, notes);
        } else if (targetStatus == DealStatus.LOST) {
            validateClosedLostRules(deal, notes);
        }
    }

    private void validateClosedWonRules(DealEntity deal, String notes) {
        boolean hasConfirmedBooking = deal.getDealId() != null 
                && bookingRepository.existsByQuotation_Deal_DealIdAndStatus(deal.getDealId(), BookingStatus.CONFIRMED);
        if (!hasConfirmedBooking) {
            UserEntity currentUser = currentUserProvider.resolve(null);
            String role = currentUser != null && currentUser.getRole() != null && currentUser.getRole().getRoleName() != null
                    ? currentUser.getRole().getRoleName().trim().toUpperCase() : "";
            boolean isManager = "MANAGER".equals(role) || "ADMIN".equals(role);
            
            String reason = notes != null ? notes.trim() : "";
            if (isManager && !reason.isEmpty() && reason.length() >= 5) {
                // Log manager exception audit
                auditLogService.log("DEAL", "Deal", deal.getDealId(), "CLOSED_WON_EXCEPTION", currentUser,
                        deal.getStatus() != null ? deal.getStatus().name() : "OPEN", "WON", "Closed Won with manager exception: " + reason);
            } else if (isManager) {
                throw new BusinessRuleException("A manager exception reason (at least 5 characters) must be provided in the Notes to bypass confirmed booking verification.");
            } else {
                throw new BusinessRuleException("A confirmed booking is required to mark a deal as Closed Won.");
            }
        }
    }

    private void validateClosedLostRules(DealEntity deal, String notes) {
        String lostReason = notes != null ? notes.trim() : "";
        if (lostReason.isEmpty()) {
            throw new BusinessRuleException("A closed-lost reason must be provided in the Notes/Reason field to mark a deal as Closed Lost.");
        }

        // BR-DEAL-LOST-01: Chặn Deal LOST khi đã có thanh toán cho Active Booking
        if (deal != null && deal.getDealId() != null && dealWorkflowResolver.hasPaidPaymentForActiveBooking(deal.getDealId())) {
            throw new BusinessException("WORKFLOW_CONSTRAINT_VIOLATION", "Cannot mark a Deal as Closed Lost when its active Booking has paid payments.", HttpStatus.UNPROCESSABLE_ENTITY);
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
