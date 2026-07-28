package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class DealWorkflowResolver {

    private final QuotationRepository quotationRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    private static final Set<QuotationStatus> ACTIVE_QUOTATION_STATUSES = Set.of(
            QuotationStatus.DRAFT,
            QuotationStatus.PENDING_APPROVAL,
            QuotationStatus.SENT,
            QuotationStatus.APPROVED,
            QuotationStatus.CONVERTED,
            QuotationStatus.PENDING_REVISION,
            QuotationStatus.ACCEPTED,
            QuotationStatus.INTERESTED
    );

    private static final Set<BookingStatus> ACTIVE_BOOKING_STATUSES = Set.of(
            BookingStatus.PENDING,
            BookingStatus.CONFIRMED,
            BookingStatus.CHECKED_IN,
            BookingStatus.CHECKED_OUT
    );

    public Optional<QuotationEntity> resolveActiveQuotation(UUID dealId) {
        if (dealId == null) {
            return Optional.empty();
        }
        List<QuotationEntity> quotations = quotationRepository.findByDeal_DealId(dealId);
        if (quotations.isEmpty()) {
            return Optional.empty();
        }
        return quotations.stream()
                .filter(q -> q.getStatus() != null && ACTIVE_QUOTATION_STATUSES.contains(q.getStatus()))
                .max(Comparator.comparing(q -> q.getCreatedAt()));
    }

    public Optional<QuotationEntity> resolveLatestQuotation(UUID dealId) {
        if (dealId == null) {
            return Optional.empty();
        }
        List<QuotationEntity> quotations = quotationRepository.findByDeal_DealId(dealId);
        if (quotations.isEmpty()) {
            return Optional.empty();
        }
        return quotations.stream()
                .max(Comparator.comparing(q -> q.getCreatedAt()));
    }

    public Optional<BookingEntity> resolveActiveBooking(UUID quotationId) {
        if (quotationId == null) {
            return Optional.empty();
        }
        List<BookingEntity> bookings = bookingRepository.findByQuotation_QuotationId(quotationId);
        if (bookings.isEmpty()) {
            return Optional.empty();
        }
        return bookings.stream()
                .filter(b -> b.getStatus() != null && ACTIVE_BOOKING_STATUSES.contains(b.getStatus()))
                .max(Comparator.comparing(b -> b.getCreatedAt()));
    }

    public Optional<BookingEntity> resolveLatestBooking(UUID quotationId) {
        if (quotationId == null) {
            return Optional.empty();
        }
        List<BookingEntity> bookings = bookingRepository.findByQuotation_QuotationId(quotationId);
        if (bookings.isEmpty()) {
            return Optional.empty();
        }
        return bookings.stream()
                .max(Comparator.comparing(b -> b.getCreatedAt()));
    }

    public PaymentStatus resolveCurrentPaymentStatus(UUID bookingId) {
        if (bookingId == null) {
            return null;
        }
        List<PaymentEntity> payments = paymentRepository.findByBooking_BookingId(bookingId);
        if (payments.isEmpty()) {
            return null;
        }
        if (payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PAID)) {
            return PaymentStatus.PAID;
        }
        if (payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PENDING)) {
            return PaymentStatus.PENDING;
        }
        return payments.stream()
                .max(Comparator.comparing(p -> p.getCreatedAt()))
                .map(p -> p.getStatus())
                .orElse(null);
    }

    public boolean hasPaidPaymentForActiveBooking(UUID dealId) {
        if (dealId == null) {
            return false;
        }
        Optional<QuotationEntity> activeQuotationOpt = resolveActiveQuotation(dealId);
        if (activeQuotationOpt.isEmpty()) {
            return false;
        }
        Optional<BookingEntity> activeBookingOpt = resolveActiveBooking(activeQuotationOpt.get().getQuotationId());
        if (activeBookingOpt.isEmpty()) {
            return false;
        }
        return paymentRepository.existsByBooking_BookingIdAndStatus(activeBookingOpt.get().getBookingId(), PaymentStatus.PAID);
    }

    public int getStageOrder(DealPipelineStage stage) {
        if (stage == null) {
            return 0;
        }
        return switch (stage) {
            case INQUIRY -> 0;
            case QUALIFICATION -> 1;
            case QUOTATION_SENT -> 2;
            case NEGOTIATION -> 3;
            case PENDING_CONFIRMATION -> 4;
            case BOOKING_CONFIRMED -> 5;
            case CLOSED_WON, CLOSED_LOST -> 6;
        };
    }
}

