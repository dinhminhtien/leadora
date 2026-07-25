package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.api.dto.response.PipelineDealCardResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.specification.DealSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetPipelineDealsUseCase {

    private final DealRepository dealRepository;
    private final QuotationRepository quotationRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final DealMapper dealMapper;
    private final DealAccessPolicy dealAccessPolicy;

    private static final Set<QuotationStatus> ACTIVE_QUOTATION_STATUSES = Set.of(
            QuotationStatus.DRAFT,
            QuotationStatus.SENT,
            QuotationStatus.APPROVED
    );

    private static final Set<BookingStatus> ACTIVE_BOOKING_STATUSES = Set.of(
            BookingStatus.PENDING,
            BookingStatus.CONFIRMED,
            BookingStatus.CHECKED_IN,
            BookingStatus.CHECKED_OUT
    );

    @Transactional(readOnly = true)
    public List<PipelineDealCardResponse> execute(String search, UUID ownerId) {
        UserEntity currentUser = dealAccessPolicy.currentUser();
        UUID scopedUserId = dealAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (scopedUserId == null);

        var spec = DealSpecification.filter(
                search,
                ownerId,
                unscoped,
                scopedUserId
        );

        List<DealEntity> deals = dealRepository.findAll(spec);
        if (deals.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> dealIds = deals.stream().map(d -> d.getDealId()).collect(Collectors.toList());

        // 1. Fetch Quotations in batch
        List<QuotationEntity> quotations = quotationRepository.findByDeal_DealIdIn(dealIds);
        Map<UUID, List<QuotationEntity>> quotationsByDealId = quotations.stream()
                .filter(q -> q.getDeal() != null)
                .collect(Collectors.groupingBy(q -> q.getDeal().getDealId()));

        // Resolve active quotation for each deal
        Map<UUID, QuotationEntity> activeQuotationByDealId = new HashMap<>();
        for (DealEntity deal : deals) {
            List<QuotationEntity> dealQuots = quotationsByDealId.getOrDefault(deal.getDealId(), Collections.emptyList());
            Optional<QuotationEntity> activeQuot = dealQuots.stream()
                    .filter(q -> q.getStatus() != null && ACTIVE_QUOTATION_STATUSES.contains(q.getStatus()))
                    .max(Comparator.comparing(q -> q.getCreatedAt()));
            activeQuot.ifPresent(quotationEntity -> activeQuotationByDealId.put(deal.getDealId(), quotationEntity));
        }

        // 2. Fetch Bookings in batch for active quotations
        List<UUID> activeQuotationIds = activeQuotationByDealId.values().stream()
                .map(q -> q.getQuotationId())
                .collect(Collectors.toList());

        List<BookingEntity> bookings = activeQuotationIds.isEmpty() ? Collections.emptyList()
                : bookingRepository.findByQuotation_QuotationIdIn(activeQuotationIds);

        Map<UUID, List<BookingEntity>> bookingsByQuotationId = bookings.stream()
                .filter(b -> b.getQuotation() != null)
                .collect(Collectors.groupingBy(b -> b.getQuotation().getQuotationId()));

        // Resolve active booking for each active quotation
        Map<UUID, BookingEntity> activeBookingByQuotationId = new HashMap<>();
        for (QuotationEntity quot : activeQuotationByDealId.values()) {
            List<BookingEntity> quotBookings = bookingsByQuotationId.getOrDefault(quot.getQuotationId(), Collections.emptyList());
            Optional<BookingEntity> activeBooking = quotBookings.stream()
                    .filter(b -> b.getStatus() != null && ACTIVE_BOOKING_STATUSES.contains(b.getStatus()))
                    .max(Comparator.comparing(b -> b.getCreatedAt()));
            activeBooking.ifPresent(bookingEntity -> activeBookingByQuotationId.put(quot.getQuotationId(), bookingEntity));
        }

        // 3. Fetch Payments in batch for active bookings
        List<UUID> activeBookingIds = activeBookingByQuotationId.values().stream()
                .map(b -> b.getBookingId())
                .collect(Collectors.toList());

        List<PaymentEntity> payments = activeBookingIds.isEmpty() ? Collections.emptyList()
                : paymentRepository.findByBooking_BookingIdIn(activeBookingIds);

        Map<UUID, List<PaymentEntity>> paymentsByBookingId = payments.stream()
                .filter(p -> p.getBooking() != null)
                .collect(Collectors.groupingBy(p -> p.getBooking().getBookingId()));

        // Map responses
        List<PipelineDealCardResponse> cardResponses = new ArrayList<>();
        for (DealEntity deal : deals) {
            DealResponse dealResp = dealMapper.mapToResponse(deal);

            QuotationEntity activeQuot = activeQuotationByDealId.get(deal.getDealId());
            boolean hasActiveQuot = (activeQuot != null);
            String activeQuotStatus = (activeQuot != null && activeQuot.getStatus() != null) ? activeQuot.getStatus().name() : null;

            BookingEntity activeBooking = null;
            if (activeQuot != null) {
                activeBooking = activeBookingByQuotationId.get(activeQuot.getQuotationId());
            }
            boolean hasActiveBooking = (activeBooking != null);
            String activeBookingStatus = (activeBooking != null && activeBooking.getStatus() != null) ? activeBooking.getStatus().name() : null;

            String currentPaymentStatus = null;
            if (activeBooking != null) {
                List<PaymentEntity> bookingPayments = paymentsByBookingId.getOrDefault(activeBooking.getBookingId(), Collections.emptyList());
                if (!bookingPayments.isEmpty()) {
                    if (bookingPayments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PAID)) {
                        currentPaymentStatus = "PAID";
                    } else if (bookingPayments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PENDING)) {
                        currentPaymentStatus = "PENDING";
                    } else {
                        currentPaymentStatus = bookingPayments.stream()
                                .max(Comparator.comparing(p -> p.getCreatedAt()))
                                .map(p -> p.getStatus() != null ? p.getStatus().name() : null)
                                .orElse(null);
                    }
                }
            }

            cardResponses.add(PipelineDealCardResponse.builder()
                    .deal(dealResp)
                    .hasActiveQuotation(hasActiveQuot)
                    .activeQuotationStatus(activeQuotStatus)
                    .hasActiveBooking(hasActiveBooking)
                    .activeBookingStatus(activeBookingStatus)
                    .paymentStatus(currentPaymentStatus)
                    .build());
        }

        return cardResponses;
    }
}
