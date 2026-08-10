package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.SubmitQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmitQuotationUseCase {

    @Value("${app.quotation.discount-threshold:10}")
    private BigDecimal discountApprovalThreshold;

    private final QuotationRepository quotationRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final QuotationAccessPolicy quotationAccessPolicy;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuotationResponse execute(UUID id, SubmitQuotationRequest request) {
        QuotationEntity quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));

        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT quotations can be submitted. Current status: " + quotation.getStatus());
        }

        BigDecimal discountPct = quotation.getDiscountPercent() != null
                ? quotation.getDiscountPercent() : BigDecimal.ZERO;

        // BR-21/BR-40: discount > threshold → pending manager approval; ≤ threshold → auto-approved
        QuotationStatus newStatus = discountPct.compareTo(discountApprovalThreshold) > 0
                ? QuotationStatus.PENDING_APPROVAL
                : QuotationStatus.APPROVED;

        List<UserEntity> managers = List.of();
        if (newStatus == QuotationStatus.PENDING_APPROVAL) {
            managers = userRepository.findByRoleName("MANAGER");
            // E3: discount exceeds authority and no manager exists to approve it —
            // block submission instead of parking it in an unreachable queue.
            if (managers.isEmpty()) {
                throw new BusinessException("NO_MANAGER_AVAILABLE",
                        "Discount exceeds authority and no manager is available for approval",
                        HttpStatus.CONFLICT);
            }
        }

        quotation.setStatus(newStatus);
        if (newStatus == QuotationStatus.APPROVED) {
            quotation.setApprovedAt(OffsetDateTime.now());
        }

        QuotationEntity saved = quotationRepository.save(quotation);

        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("discountPercent", discountPct.toString())
                    .put("newStatus", newStatus.name());
            activityLogPublisher.publish(
                    ActivityLogType.QUOTATION_SUBMITTED,
                    EntityType.QUOTATION,
                    saved.getQuotationId(),
                    "Quotation submitted",
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish quotation submission activity: {}", e.getMessage());
        }

        if (newStatus == QuotationStatus.APPROVED) {
            try {
                startSlaTrackingUseCase.execute("QUOTATION_SENT", "QUOTATION", saved.getQuotationId());
            } catch (Exception e) {
                log.warn("SLA tracking failed for quotation {}: {}", saved.getQuotationId(), e.getMessage());
            }
        }

        // BR-21/BR-34: alert Sales Managers so a discount >10% quotation doesn't sit
        // unnoticed in the pending-approvals queue
        if (newStatus == QuotationStatus.PENDING_APPROVAL) {
            String message = "Quotation " + saved.getQuotationId().toString().substring(0, 8).toUpperCase()
                    + " requires approval — discount " + discountPct + "% exceeds the " + discountApprovalThreshold + "% threshold.";
            for (UserEntity manager : managers) {
                NotificationEntity notification = NotificationEntity.builder()
                        .user(manager)
                        .title("Quotation Pending Approval")
                        .message(message)
                        .type("QUOTATION_PENDING_APPROVAL")
                        .relatedEntity("QUOTATION")
                        .relatedId(saved.getQuotationId())
                        .build();
                notificationRepository.save(notification);
            }
            log.info("Quotation {} submitted with discount {}% — notified {} manager(s)",
                    saved.getQuotationId(), discountPct, managers.size());
        }

        List<QuotationDetailEntity> details =
                quotationDetailRepository.findByQuotation_QuotationId(saved.getQuotationId());

        return QuotationResponse.fromWithDetails(saved, details);
    }
}