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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmitQuotationUseCase {

    private static final BigDecimal DISCOUNT_APPROVAL_THRESHOLD = new BigDecimal("10");

    private final QuotationRepository quotationRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final QuotationAccessPolicy quotationAccessPolicy;

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

        // BR-21/BR-40: discount > 10% → pending manager approval; ≤ 10% → auto-approved
        QuotationStatus newStatus = discountPct.compareTo(DISCOUNT_APPROVAL_THRESHOLD) > 0
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

        // BR-21/BR-34: alert Sales Managers so a discount >10% quotation doesn't sit
        // unnoticed in the pending-approvals queue
        if (newStatus == QuotationStatus.PENDING_APPROVAL) {
            String message = "Quotation " + saved.getQuotationId().toString().substring(0, 8).toUpperCase()
                    + " requires approval — discount " + discountPct + "% exceeds the 10% threshold.";
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
        QuotationDetailEntity detail = details.isEmpty() ? null : details.get(0);

        int nights = detail != null ? detail.getNights() : 0;
        int numberOfRooms = detail != null ? detail.getQuantity() : 0;
        BigDecimal pricePerNight = detail != null ? detail.getUnitPrice() : BigDecimal.ZERO;

        return QuotationResponse.fromWithDetail(saved, nights, numberOfRooms, pricePerNight);
    }
}