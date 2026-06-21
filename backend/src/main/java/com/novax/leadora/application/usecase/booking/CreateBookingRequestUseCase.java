package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.api.dto.request.CreateBookingRequest;
import com.novax.leadora.api.dto.response.BookingDetailResponse;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.InventoryStatus;
import com.novax.leadora.infrastructure.persistence.repository.*;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateBookingRequestUseCase {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final CustomerRepository customerRepository;
    private final QuotationRepository quotationRepository;
    private final UserRepository userRepository;
    private final ProductServiceRepository productServiceRepository;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;

    @Transactional
    public BookingResponse execute(CreateBookingRequest request) {
        if (request.getCheckInDate() == null || request.getCheckOutDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-in and check-out dates are required");
        }
        if (!request.getCheckInDate().isBefore(request.getCheckOutDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-in date must be strictly before check-out date");
        }

        CustomerEntity customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found"));

        QuotationEntity quotation = quotationRepository.findById(request.getQuotationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quotation not found"));

        UserEntity assignedUser = null;
        if (request.getAssignedUserId() != null) {
            assignedUser = userRepository.findById(request.getAssignedUserId()).orElse(null);
        }

        // Generate a unique booking code
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomSuffix = String.format("%04d", new Random().nextInt(10000));
        String bookingCode = "BK-" + dateStr + "-" + randomSuffix;

        BigDecimal totalAmount = BigDecimal.ZERO;

        // Build Booking Entity (Set status to PENDING by default)
        BookingEntity booking = BookingEntity.builder()
                .customer(customer)
                .quotation(quotation)
                .assignedUser(assignedUser)
                .bookingCode(bookingCode)
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .status(BookingStatus.PENDING)
                .specialRequests(request.getSpecialRequests())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BookingEntity savedBooking = bookingRepository.save(booking);

        List<BookingDetailEntity> detailEntities = new ArrayList<>();
        for (var detailReq : request.getDetails()) {
            ProductServiceEntity product = productServiceRepository.findById(detailReq.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product not found"));

            BigDecimal lineTotal = detailReq.getUnitPrice()
                    .multiply(BigDecimal.valueOf(detailReq.getQuantity()))
                    .multiply(BigDecimal.valueOf(detailReq.getNights()));

            totalAmount = totalAmount.add(lineTotal);

            BookingDetailEntity detail = BookingDetailEntity.builder()
                    .booking(savedBooking)
                    .productService(product)
                    .roomNumber(detailReq.getRoomNumber())
                    .quantity(detailReq.getQuantity())
                    .unitPrice(detailReq.getUnitPrice())
                    .nights(detailReq.getNights())
                    .lineTotal(lineTotal)
                    .inventoryStatus(InventoryStatus.ALLOCATED)
                    .build();

            detailEntities.add(bookingDetailRepository.save(detail));
        }

        savedBooking.setTotalAmount(totalAmount);
        BookingEntity finalSavedBooking = bookingRepository.save(savedBooking);

        List<BookingDetailResponse> detailResponses = detailEntities.stream()
                .map(BookingDetailResponse::from)
                .collect(Collectors.toList());

        // UC-17.2: start SLA tracking — non-fatal if no rule configured
        try {
            startSlaTrackingUseCase.execute("BOOKING_CONFIRM", "BOOKING", finalSavedBooking.getBookingId());
        } catch (Exception e) {
            log.warn("SLA tracking failed for booking {}: {}", finalSavedBooking.getBookingId(), e.getMessage());
        }

        return BookingResponse.from(finalSavedBooking, detailResponses);
    }
}
