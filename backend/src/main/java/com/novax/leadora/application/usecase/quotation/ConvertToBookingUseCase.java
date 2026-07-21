package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.InventoryStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConvertToBookingUseCase {

    private final QuotationRepository quotationRepository;
    private final BookingRepository bookingRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final BookingDetailRepository bookingDetailRepository;

    @Transactional
    public BookingResponse execute(UUID quotationId, ConvertToBookingRequest request) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new RuntimeException("Quotation not found: " + quotationId));

        // PRE-1: Only ACCEPTED quotations can be converted
        if (quotation.getStatus() != QuotationStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "Only ACCEPTED quotations can be converted to a booking. Current status: "
                            + quotation.getStatus().name());
        }

        // BR-23: Resolve dates — request values take precedence, fall back to quotation
        LocalDate checkInDate  = request.getCheckInDate()  != null ? request.getCheckInDate()  : quotation.getCheckInDate();
        LocalDate checkOutDate = request.getCheckOutDate() != null ? request.getCheckOutDate() : quotation.getCheckOutDate();

        if (checkInDate == null || checkOutDate == null) {
            throw new IllegalArgumentException(
                    "Check-in and check-out dates are required for booking conversion (BR-23)");
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        // BR-23: Customer identity required
        if (quotation.getCustomer() == null) {
            throw new IllegalArgumentException("Customer information is missing (BR-23)");
        }

        // Generate booking code from year + quotation UUID prefix (unique per quotation)
        String bookingCode = "BK-" + checkInDate.getYear() + "-"
                + quotationId.toString().substring(0, 8).toUpperCase();

        // POST-2: Create pending booking record
        BookingEntity booking = BookingEntity.builder()
                .quotation(quotation)
                .customer(quotation.getCustomer())
                .assignedUser(quotation.getCreatedBy())
                .bookingCode(bookingCode)
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .status(BookingStatus.PENDING)
                .specialRequests(request.getSpecialRequests())
                .totalAmount(quotation.getTotalAmount())
                .build();

        BookingEntity saved = bookingRepository.save(booking);

        // Copy quotation line items into booking details, holding inventory for each room/service
        List<QuotationDetailEntity> quotationDetails =
                quotationDetailRepository.findByQuotation_QuotationId(quotationId);

        List<BookingDetailEntity> bookingDetails = quotationDetails.stream()
                .map(detail -> BookingDetailEntity.builder()
                        .booking(saved)
                        .productService(detail.getProductService())
                        .description(detail.getDescription())
                        .quantity(detail.getQuantity())
                        .unitPrice(detail.getUnitPrice())
                        .nights(detail.getNights())
                        .lineTotal(detail.getLineTotal())
                        .inventoryStatus(InventoryStatus.ALLOCATED)
                        .build())
                .toList();

        bookingDetailRepository.saveAll(bookingDetails);

        // POST-1: Update quotation status to CONVERTED
        quotation.setStatus(QuotationStatus.CONVERTED);
        quotationRepository.save(quotation);

        return BookingResponse.from(saved);
    }
}
