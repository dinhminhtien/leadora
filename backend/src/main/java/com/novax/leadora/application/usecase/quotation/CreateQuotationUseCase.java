package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.CreateQuotationRequest;
import com.novax.leadora.api.dto.request.RoomLineRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.inventory.RoomAvailabilityAssessment;
import com.novax.leadora.application.usecase.inventory.RoomLineDemand;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.application.usecase.deal.DealWorkflowSyncService;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateQuotationUseCase {

        private final QuotationRepository quotationRepository;
        private final QuotationDetailRepository quotationDetailRepository;
        private final DealRepository dealRepository;
        private final CurrentUserProvider currentUserProvider;
        private final QuotationAvailabilityChecker availabilityChecker;
        private final DealWorkflowSyncService dealWorkflowSyncService;
        private final ActivityLogPublisher activityLogPublisher;
        private final ObjectMapper objectMapper;

        @Transactional
        public QuotationResponse execute(CreateQuotationRequest request) {
                // 1. Validate dates
                if (!request.getCheckOutDate().isAfter(request.getCheckInDate())) {
                        throw new IllegalArgumentException("Check-out date must be after check-in date");
                }

                // Resolves the room types and reports how they look against the quota Reservation
                // published. Advisory: a shortfall never stops a quotation, because an offer the
                // hotel may still be able to service is worth making, and only Reservation can say.
                List<RoomLineDemand> demands = request.getRoomLines().stream()
                                .map(line -> new RoomLineDemand(line.getProductId(), line.getNumberOfRooms()))
                                .toList();
                RoomAvailabilityAssessment assessment = availabilityChecker.assess(
                                request.getCheckInDate(), request.getCheckOutDate(), demands);

                // 2. Fetch deal and get linked customer
                DealEntity deal = dealRepository.findById(request.getDealId())
                                .orElseThrow(() -> new ResourceNotFoundException("Deal", request.getDealId()));

                CustomerEntity customer = deal.getCustomer();
                if (customer == null) {
                        throw new IllegalArgumentException("The selected deal does not have a linked customer");
                }

                // 3. Calculate pricing — one line per room type, summed into the quotation total
                long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
                BigDecimal discountPct = request.getDiscountPercent() != null
                                ? request.getDiscountPercent()
                                : BigDecimal.ZERO;

                BigDecimal subtotal = request.getRoomLines().stream()
                                .map(line -> lineSubtotal(line, nights))
                                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

                BigDecimal discountAmount = subtotal
                                .multiply(discountPct)
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                BigDecimal totalAmount = subtotal.subtract(discountAmount);

                // 4. Always save as DRAFT — status is resolved on explicit Submit (UC-14.1)
                QuotationStatus status = QuotationStatus.DRAFT;

                // The creator owns the quotation (drives SALES owner-scoping). Non-fatal if
                // unresolved.
                UserEntity creator = null;
                try {
                        creator = currentUserProvider.resolve(null);
                } catch (Exception e) {
                        log.warn("Could not resolve creator for new quotation: {}", e.getMessage());
                }

                // 5. Save quotation
                QuotationEntity quotation = QuotationEntity.builder()
                                .deal(deal)
                                .customer(customer)
                                .createdBy(creator)
                                .version(1)
                                .status(status)
                                .roomType(roomTypeSummary(request.getRoomLines(), assessment))
                                .checkInDate(request.getCheckInDate())
                                .checkOutDate(request.getCheckOutDate())
                                .paymentPolicy(request.getPaymentPolicy())
                                .subtotal(subtotal)
                                .discountPercent(discountPct)
                                .discountAmount(discountAmount)
                                .totalAmount(totalAmount)
                                .validUntil(request.getValidUntil())
                                .notes(request.getNotes())
                                .build();

                QuotationEntity saved = quotationRepository.save(quotation);

                // 6. Save one detail line per room type. product_id is what later stages resolve
                // the room by — it used to be left null, forcing Convert to re-match on the
                // free-text description.
                List<QuotationDetailEntity> details = request.getRoomLines().stream()
                                .map(line -> {
                                        ProductServiceEntity product = assessment.products().get(line.getProductId());
                                        return QuotationDetailEntity.builder()
                                                .quotation(saved)
                                                .productService(product)
                                                .description(product.getName())
                                                .quantity(line.getNumberOfRooms())
                                                .unitPrice(line.getPricePerNight())
                                                .nights((int) nights)
                                                .lineTotal(lineSubtotal(line, nights))
                                                .build();
                                })
                                .toList();

                quotationDetailRepository.saveAll(details);

                // Nothing is held or reserved here. Creating a quotation is an offer, and Report 1
                // (FE-19, LI-02) puts holding and locking room inventory on the Reservation side of
                // the boundary — this used to take a soft hold on the quota and, when it could not,
                // put a question to the Reservation team on the rep's behalf. The availability
                // question is now raised once, at the point it is actually needed: when the customer
                // accepts. A rep who wants an earlier answer can still ask from the quotation.

                // Publish Activity Log event
                try {
                        ObjectNode payload = objectMapper.createObjectNode()
                                        .put("dealId", deal.getDealId().toString())
                                        .put("customerId", customer.getCustomerId().toString())
                                        .put("totalAmount", totalAmount.toString())
                                        .put("status", status.name());
                        activityLogPublisher.publish(
                                        ActivityLogType.QUOTATION_CREATED,
                                        EntityType.QUOTATION,
                                        saved.getQuotationId(),
                                        "Quotation created",
                                        payload
                        );
                } catch (Exception e) {
                        log.warn("Failed to publish quotation creation activity: {}", e.getMessage());
                }

                dealWorkflowSyncService.syncPipelineStage(deal.getDealId());

                return QuotationResponse.fromWithDetails(saved, details);
        }

        private static BigDecimal lineSubtotal(RoomLineRequest line, long nights) {
                return line.getPricePerNight()
                                .multiply(BigDecimal.valueOf(nights))
                                .multiply(BigDecimal.valueOf(line.getNumberOfRooms()));
        }

        /**
         * Human-readable summary for single-line display surfaces (list, emails, chat).
         *
         * <p>Names come from the resolved products, not from what the client sent, so the summary
         * cannot disagree with the room the line actually points at.
         */
        private static String roomTypeSummary(List<RoomLineRequest> roomLines,
                        RoomAvailabilityAssessment assessment) {
                String first = assessment.products().get(roomLines.get(0).getProductId()).getName();
                return roomLines.size() == 1 ? first : first + " +" + (roomLines.size() - 1) + " more";
        }
}
