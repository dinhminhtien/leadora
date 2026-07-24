package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.CreateQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateQuotationUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final DealRepository dealRepository;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final QuotationAvailabilityChecker availabilityChecker;

    @Transactional
    public QuotationResponse execute(CreateQuotationRequest request) {
        // 1. Validate dates
        if (!request.getCheckOutDate().isAfter(request.getCheckInDate())) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        // E2: room type must exist and be available for the requested dates (BR-24)
        availabilityChecker.assertRoomAvailable(request.getCheckInDate(), request.getCheckOutDate(), request.getRoomType());

        // 2. Fetch deal and get linked customer
        DealEntity deal = dealRepository.findById(request.getDealId())
                .orElseThrow(() -> new ResourceNotFoundException("Deal", request.getDealId()));

        CustomerEntity customer = deal.getCustomer();
        if (customer == null) {
            throw new IllegalArgumentException("The selected deal does not have a linked customer");
        }

        // 3. Calculate pricing
        long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
        BigDecimal discountPct = request.getDiscountPercent() != null
                ? request.getDiscountPercent() : BigDecimal.ZERO;

        BigDecimal subtotal = request.getPricePerNight()
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(request.getNumberOfRooms()));

        BigDecimal discountAmount = subtotal
                .multiply(discountPct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = subtotal.subtract(discountAmount);

        // 4. Always save as DRAFT — status is resolved on explicit Submit (UC-14.1)
        QuotationStatus status = QuotationStatus.DRAFT;

        // The creator owns the quotation (drives SALES owner-scoping). Non-fatal if unresolved.
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
                .roomType(request.getRoomType())
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

        // 6. Save detail line (room)
        QuotationDetailEntity detail = QuotationDetailEntity.builder()
                .quotation(saved)
                .description(request.getRoomType())
                .quantity(request.getNumberOfRooms())
                .unitPrice(request.getPricePerNight())
                .nights((int) nights)
                .lineTotal(totalAmount)
                .build();

        quotationDetailRepository.save(detail);

        // UC-17.2: start SLA tracking — non-fatal if no rule configured
        try {
            startSlaTrackingUseCase.execute("QUOTATION_SENT", "QUOTATION", saved.getQuotationId());
        } catch (Exception e) {
            log.warn("SLA tracking failed for quotation {}: {}", saved.getQuotationId(), e.getMessage());
        }

        return QuotationResponse.fromWithDetail(saved, (int) nights,
                request.getNumberOfRooms(), request.getPricePerNight());
    }
}