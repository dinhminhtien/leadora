package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.ReviseQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviseQuotationUseCase {

    // Only a quotation still "in play" can be revised — matches the set of statuses
    // the frontend already shows the Revise action for (QuotationListScreen.tsx).
    private static final Set<QuotationStatus> REVISABLE_STATUSES = Set.of(
            QuotationStatus.DRAFT, QuotationStatus.SENT, QuotationStatus.INTERESTED,
            QuotationStatus.REJECTED, QuotationStatus.PENDING_REVISION
    );

    private final QuotationRepository quotationRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final CurrentUserProvider currentUserProvider;
    private final QuotationAccessPolicy quotationAccessPolicy;
    private final QuotationAvailabilityChecker availabilityChecker;
    private final SystemAuditLogService systemAuditLogService;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuotationResponse execute(UUID parentId, ReviseQuotationRequest request) {
        // Validate dates
        if (!request.getCheckOutDate().isAfter(request.getCheckInDate())) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        // Load parent quotation to inherit deal + customer (BR-22)
        QuotationEntity parent = quotationRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", parentId));

        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), parent);

        if (!REVISABLE_STATUSES.contains(parent.getStatus())) {
            throw new BusinessException("QUOTATION_NOT_REVISABLE",
                    "Quotation cannot be revised from status " + parent.getStatus().name(), HttpStatus.CONFLICT);
        }

        // E2: room type must exist and be available for the requested dates (BR-24)
        availabilityChecker.assertRoomAvailable(request.getCheckInDate(), request.getCheckOutDate(), request.getRoomType());

        // Pricing calculations
        long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
        BigDecimal discountPct = request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO;

        BigDecimal subtotal = request.getPricePerNight()
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(request.getNumberOfRooms()));

        BigDecimal discountAmount = subtotal
                .multiply(discountPct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = subtotal.subtract(discountAmount);

        // Always save as DRAFT — status is resolved on explicit Submit
        QuotationStatus status = QuotationStatus.DRAFT;

        // The reviser owns the new version (drives SALES owner-scoping). Non-fatal if unresolved.
        UserEntity creator = null;
        try {
            creator = currentUserProvider.resolve(null);
        } catch (Exception e) {
            log.warn("Could not resolve creator for quotation revision: {}", e.getMessage());
        }

        // POST-1: Create new version (BR-22: preserve previous, create new)
        QuotationEntity revision = QuotationEntity.builder()
                .deal(parent.getDeal())
                .customer(parent.getCustomer())
                .createdBy(creator)
                .version((parent.getVersion() != null ? parent.getVersion() : 1) + 1)
                .roomType(request.getRoomType())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .paymentPolicy(request.getPaymentPolicy())
                .status(status)
                .subtotal(subtotal)
                .discountPercent(discountPct)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .validUntil(request.getValidUntil())
                .notes(request.getNotes())
                .parentQuotationId(parentId)
                .changeReason(request.getChangeReason())
                .build();

        QuotationEntity saved = quotationRepository.save(revision);

        // Save detail line (room)
        QuotationDetailEntity detail = QuotationDetailEntity.builder()
                .quotation(saved)
                .description(request.getRoomType())
                .quantity(request.getNumberOfRooms())
                .unitPrice(request.getPricePerNight())
                .nights((int) nights)
                .lineTotal(totalAmount)
                .build();
        quotationDetailRepository.save(detail);

        // BR-22: exactly one version stays "active" — supersede the parent now that a
        // new version exists, instead of leaving both live simultaneously.
        parent.setStatus(QuotationStatus.SUPERSEDED);
        quotationRepository.save(parent);

        // BR-37/BR-22: record the price change in the audit trail — the parent row
        // itself is preserved (SUPERSEDED, not deleted/overwritten), but without this
        // entry there is no audit-log trace of what the price changed from/to.
        systemAuditLogService.log("QUOTATION", "QUOTATION", saved.getQuotationId(), "REVISED", creator,
                "version=" + parent.getVersion() + ", totalAmount=" + parent.getTotalAmount(),
                "version=" + saved.getVersion() + ", totalAmount=" + saved.getTotalAmount(),
                "parentQuotationId=" + parentId + ", changeReason=" + request.getChangeReason());

        // Publish Activity Log for the new version
        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("parentQuotationId", parentId.toString())
                    .put("changeReason", request.getChangeReason())
                    .put("version", saved.getVersion())
                    .put("totalAmount", saved.getTotalAmount().toString());
            activityLogPublisher.publish(
                    ActivityLogType.QUOTATION_CREATED,
                    EntityType.QUOTATION,
                    saved.getQuotationId(),
                    "Quotation revised (new version created)",
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish revision quotation creation activity: {}", e.getMessage());
        }

        // Publish Activity Log for the superseded parent
        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("previousStatus", QuotationStatus.SUPERSEDED.name())
                    .put("newStatus", QuotationStatus.SUPERSEDED.name());
            activityLogPublisher.publish(
                    ActivityLogType.QUOTATION_UPDATED,
                    EntityType.QUOTATION,
                    parent.getQuotationId(),
                    "Quotation superseded by version " + saved.getVersion(),
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish parent quotation supersede activity: {}", e.getMessage());
        }

        return QuotationResponse.fromWithDetail(saved, (int) nights,
                request.getNumberOfRooms(), request.getPricePerNight());
    }
}
