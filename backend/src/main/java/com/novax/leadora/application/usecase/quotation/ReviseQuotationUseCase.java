package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.ReviseQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviseQuotationUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationDetailRepository quotationDetailRepository;

    @Transactional
    public QuotationResponse execute(UUID parentId, ReviseQuotationRequest request) {
        // Validate dates
        if (!request.getCheckOutDate().isAfter(request.getCheckInDate())) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        // Load parent quotation to inherit deal + customer (BR-22)
        QuotationEntity parent = quotationRepository.findById(parentId)
                .orElseThrow(() -> new RuntimeException("Quotation not found: " + parentId));

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

        // POST-1: Create new version (BR-22: preserve previous, create new)
        QuotationEntity revision = QuotationEntity.builder()
                .deal(parent.getDeal())
                .customer(parent.getCustomer())
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

        return QuotationResponse.fromWithDetail(saved, (int) nights,
                request.getNumberOfRooms(), request.getPricePerNight());
    }
}
