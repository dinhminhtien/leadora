package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class DealMapper {

    @Value("${app.deal.probability.inquiry:10}")
    private int inquiryProbability;

    @Value("${app.deal.probability.qualification:30}")
    private int qualificationProbability;

    @Value("${app.deal.probability.quotation-sent:50}")
    private int quotationSentProbability;

    @Value("${app.deal.probability.negotiation:70}")
    private int negotiationProbability;

    @Value("${app.deal.probability.pending-confirmation:80}")
    private int pendingConfirmationProbability;

    @Value("${app.deal.probability.booking-confirmed:90}")
    private int bookingConfirmedProbability;

    public DealResponse mapToResponse(DealEntity deal) {
        String contactName = deal.getCustomer() != null ? deal.getCustomer().getFullName() : "N/A";
        String email = deal.getCustomer() != null ? deal.getCustomer().getEmail() : "";
        String phone = deal.getCustomer() != null ? deal.getCustomer().getPhone() : "";
        String ownerName = deal.getAssignedUser() != null ? deal.getAssignedUser().getFullName() : "Unassigned";
        String ownerEmail = deal.getAssignedUser() != null ? deal.getAssignedUser().getEmail() : null;
        UUID ownerId = deal.getAssignedUser() != null ? deal.getAssignedUser().getUserId() : null;

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
                .ownerEmail(ownerEmail)
                .ownerId(ownerId)
                .status(mapStatusToString(deal.getStatus()))
                .expectedClose(deal.getExpectedCloseDate())
                .createdAt(deal.getCreatedAt() != null ? deal.getCreatedAt().toLocalDate() : LocalDate.now())
                .notes(deal.getNotes())
                .build();
    }

    public DealPipelineStage mapStageToEnum(String stage) {
        if (stage == null) {
            return DealPipelineStage.INQUIRY;
        }
        switch (stage.toLowerCase()) {
            case "inquiry":
                return DealPipelineStage.INQUIRY;
            case "qualification":
            case "site visit":
            case "qualified":
                return DealPipelineStage.QUALIFICATION;
            case "proposal":
            case "quotation sent":
                return DealPipelineStage.QUOTATION_SENT;
            case "negotiation":
                return DealPipelineStage.NEGOTIATION;
            case "contract":
                return DealPipelineStage.PENDING_CONFIRMATION;
            case "confirmed":
                return DealPipelineStage.BOOKING_CONFIRMED;
            default:
                try {
                    return DealPipelineStage.valueOf(stage.toUpperCase());
                } catch (Exception e) {
                    return DealPipelineStage.INQUIRY;
                }
        }
    }

    public String mapStageToString(DealPipelineStage stage, DealStatus status) {
        if (stage == null) {
            return "Inquiry";
        }
        switch (stage) {
            case INQUIRY:
                return "Inquiry";
            case QUALIFICATION:
                return "Qualification";
            case QUOTATION_SENT:
                return "Proposal";
            case NEGOTIATION:
                return "Negotiation";
            case PENDING_CONFIRMATION:
                return "Contract";
            case BOOKING_CONFIRMED:
            case CLOSED_WON:
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
            return inquiryProbability;
        }
        switch (stage) {
            case INQUIRY:
                return inquiryProbability;
            case QUALIFICATION:
                return qualificationProbability;
            case QUOTATION_SENT:
                return quotationSentProbability;
            case NEGOTIATION:
                return negotiationProbability;
            case PENDING_CONFIRMATION:
                return pendingConfirmationProbability;
            case BOOKING_CONFIRMED:
                return bookingConfirmedProbability;
            case CLOSED_WON:
                return 100;
            case CLOSED_LOST:
                return 0;
            default:
                return inquiryProbability;
        }
    }
}
