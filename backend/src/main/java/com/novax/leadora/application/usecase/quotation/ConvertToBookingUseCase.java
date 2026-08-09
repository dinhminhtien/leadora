package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
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
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import com.novax.leadora.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConvertToBookingUseCase {

        private final QuotationRepository quotationRepository;
        private final BookingRepository bookingRepository;
        private final QuotationDetailRepository quotationDetailRepository;
        private final BookingDetailRepository bookingDetailRepository;
        private final ProductServiceRepository productServiceRepository;
        private final QuotationAccessPolicy quotationAccessPolicy;
        private final QuotationAvailabilityChecker availabilityChecker;
        private final StartSlaTrackingUseCase startSlaTrackingUseCase;

        @Transactional
        public BookingResponse execute(UUID quotationId, ConvertToBookingRequest request) {
                return execute(quotationId, request, false);
        }

        @Transactional
        public BookingResponse execute(UUID quotationId, ConvertToBookingRequest request, boolean skipAuth) {
                QuotationEntity quotation = quotationRepository.findById(quotationId)
                                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

                if (!skipAuth) {
                        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);
                }

                // PRE-1: Only ACCEPTED quotations can be converted
                if (quotation.getStatus() != QuotationStatus.ACCEPTED) {
                        throw new BusinessException("QUOTATION_INVALID_STATUS",
                                        "Only ACCEPTED quotations can be converted to a booking. Current status: "
                                                        + quotation.getStatus().name(),
                                        HttpStatus.CONFLICT);
                }

                // BR-23: Resolve dates — request values take precedence, fall back to quotation
                LocalDate checkInDate = request.getCheckInDate() != null ? request.getCheckInDate()
                                : quotation.getCheckInDate();
                LocalDate checkOutDate = request.getCheckOutDate() != null ? request.getCheckOutDate()
                                : quotation.getCheckOutDate();

                if (checkInDate == null || checkOutDate == null) {
                        throw new BusinessException("INVALID_DATES",
                                        "Check-in and check-out dates are required for booking conversion (BR-23)",
                                        HttpStatus.BAD_REQUEST);
                }
                if (!checkOutDate.isAfter(checkInDate)) {
                        throw new BusinessException("INVALID_DATES", "Check-out date must be after check-in date",
                                        HttpStatus.BAD_REQUEST);
                }

                // BR-23: Customer identity required
                if (quotation.getCustomer() == null) {
                        throw new BusinessException("CUSTOMER_MISSING", "Customer information is missing (BR-23)",
                                        HttpStatus.UNPROCESSABLE_ENTITY);
                }

                // Load details to get products and check availability
                List<QuotationDetailEntity> quotationDetails = quotationDetailRepository
                                .findByQuotation_QuotationId(quotationId);

                // Acquire PESSIMISTIC_WRITE lock on products to prevent concurrent
                // double-booking.
                // Sort product IDs first to avoid deadlocks.
                List<UUID> productIds = quotationDetails.stream()
                                .map(detail -> detail.getProductService().getProductId())
                                .distinct()
                                .sorted()
                                .toList();
                for (UUID productId : productIds) {
                        productServiceRepository.findByIdWithPessimisticWriteLock(productId)
                                        .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                                                        "Product not found: " + productId, HttpStatus.NOT_FOUND));
                }

                // Sum up quantity for the room type being checked
                int requestedQuantity = quotationDetails.stream()
                                .filter(detail -> detail.getProductService().getCategory() == ProductCategory.ROOM &&
                                                detail.getProductService().getName()
                                                                .equalsIgnoreCase(quotation.getRoomType()))
                                .mapToInt(e -> e.getQuantity())
                                .sum();

                // E3: room must still be available for the (possibly re-confirmed) dates —
                // BR-24
                availabilityChecker.assertRoomAvailable(checkInDate, checkOutDate, quotation.getRoomType(),
                                requestedQuantity);

                // Generate booking code from year + quotation UUID prefix (unique per
                // quotation)
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

                // Copy quotation line items into booking details, holding inventory for each
                // room/service

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

                // UC-17.2: start SLA tracking — non-fatal if no BOOKING_CONFIRM rule configured
                try {
                        startSlaTrackingUseCase.execute("BOOKING_CONFIRM", "BOOKING", saved.getBookingId());
                } catch (Exception e) {
                        log.warn("SLA tracking failed for booking {}: {}", saved.getBookingId(), e.getMessage());
                }

                return BookingResponse.from(saved);
        }
}
