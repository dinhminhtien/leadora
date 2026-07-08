package com.novax.leadora.application.usecase.payment;

import com.novax.leadora.api.dto.request.GeneratePaymentRequest;
import com.novax.leadora.api.dto.response.PaymentResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * UC-21.1 — Generate Payment Request Use Case with Local VietQR Generation (Direct MB Bank Integration).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeneratePaymentRequestUseCase {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    @Value("${app.bank.account-number:123456789}")
    private String bankAccountNumber;

    @Transactional
    public PaymentResponse execute(GeneratePaymentRequest request, UserEntity actor) {
        BookingEntity booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found", request.getBookingId()));

        // BR-44: Check if booking is cancelled or checked out
        String bStatus = booking.getStatus() != null ? booking.getStatus().name() : "";
        if (bStatus.equals("CANCELLED") || bStatus.equals("CHECKED_OUT")) {
            throw new IllegalStateException("Booking is cancelled or checked out, cannot generate payment request.");
        }

        // BR-28: Save new Payment request linked to Booking
        PaymentEntity payment = PaymentEntity.builder()
                .booking(booking)
                .createdBy(actor)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .status(PaymentStatus.PENDING)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "TRANSFER")
                .dueDate(request.getDueDate() != null ? request.getDueDate() : LocalDate.now().plusDays(7))
                .verificationNote(request.getNotes())
                .gatewayProvider("MBBANK")
                .build();

        // Save first to get the payment ID for VietQR addInfo note
        PaymentEntity saved = paymentRepository.save(payment);

        double rate = fetchUsdVndRate();
        long amountInVnd = Math.round(request.getAmount().doubleValue() * rate);
        log.info("Converting USD amount {} to VND amount {} using rate {}", request.getAmount(), amountInVnd, rate);

        // Generate dynamic VietQR link pointing directly to our MB Bank account
        String qrCodeUrl = "https://img.vietqr.io/image/MB-" + bankAccountNumber 
                + "-compact2.png?amount=" + amountInVnd 
                + "&addInfo=LEADORAPAY" + saved.getPaymentId();

        saved.setQrCodeUrl(qrCodeUrl);
        saved.setGatewayTransactionId(null); // Will be mapped by bank check scheduler once cleared
        if (saved.getVerificationNote() == null || saved.getVerificationNote().isBlank()) {
            saved.setVerificationNote("Transfer instruction: Direct payment to MB Bank Account " + bankAccountNumber);
        }

        PaymentEntity updated = paymentRepository.save(saved);

        log.info(
                "[AUDIT] Action: GENERATE_PAYMENT_REQUEST, PaymentId: {}, BookingCode: {}, Amount: {}, PaymentType: {}, CreatedBy: {}",
                updated.getPaymentId(), booking.getBookingCode(), updated.getAmount(), updated.getPaymentType(),
                actor != null ? actor.getUserId() : null);

        return PaymentResponse.from(updated);
    }

    @SuppressWarnings("unchecked")
    private double fetchUsdVndRate() {
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            java.util.Map<String, Object> response = restTemplate.getForObject("https://open.er-api.com/v6/latest/USD",
                    java.util.Map.class);
            if (response != null && "success".equals(response.get("result"))) {
                java.util.Map<String, Object> rates = (java.util.Map<String, Object>) response.get("rates");
                if (rates != null && rates.containsKey("VND")) {
                    Number vndRate = (Number) rates.get("VND");
                    log.info("Fetched real-time USD to VND exchange rate: {}", vndRate);
                    return vndRate.doubleValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch real-time exchange rate: {}. Falling back to default 25400.0", e.getMessage());
        }
        return 25400.0;
    }
}
