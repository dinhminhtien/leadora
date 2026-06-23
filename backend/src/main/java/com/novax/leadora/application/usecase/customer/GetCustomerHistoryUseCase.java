package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.api.dto.response.CustomerHistoryItem;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetCustomerHistoryUseCase {

    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final BookingRepository bookingRepository;
    private final QuotationRepository quotationRepository;

    @Transactional(readOnly = true)
    public List<CustomerHistoryItem> execute(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }

        List<CustomerHistoryItem> history = new ArrayList<>();

        // ── Deals ──────────────────────────────────────────────────────────────
        for (DealEntity deal : dealRepository.findByCustomer_CustomerId(customerId)) {
            String stage = deal.getPipelineStage() != null ? deal.getPipelineStage().name() : null;
            String status = deal.getStatus() != null ? deal.getStatus().name() : null;
            history.add(new CustomerHistoryItem(
                    "DEAL",
                    deal.getDealId().toString(),
                    deal.getDealName(),
                    status,
                    stage,
                    deal.getExpectedRevenue(),
                    null,
                    null,
                    deal.getExpectedCloseDate() != null ? deal.getExpectedCloseDate().toString() : null,
                    deal.getCreatedAt() != null ? deal.getCreatedAt().toString() : null,
                    deal.getNotes()
            ));
        }

        // ── Bookings ───────────────────────────────────────────────────────────
        for (BookingEntity booking : bookingRepository.findByCustomer_CustomerId(customerId)) {
            String title = booking.getBookingCode() != null
                    ? "Booking #" + booking.getBookingCode()
                    : "Booking";
            history.add(new CustomerHistoryItem(
                    "BOOKING",
                    booking.getBookingId().toString(),
                    title,
                    booking.getStatus() != null ? booking.getStatus().name() : null,
                    null,
                    booking.getTotalAmount(),
                    booking.getCheckInDate() != null ? booking.getCheckInDate().toString() : null,
                    booking.getCheckOutDate() != null ? booking.getCheckOutDate().toString() : null,
                    null,
                    booking.getCreatedAt() != null ? booking.getCreatedAt().toString() : null,
                    booking.getSpecialRequests()
            ));
        }

        // ── Quotations ─────────────────────────────────────────────────────────
        for (QuotationEntity quotation : quotationRepository.findByCustomer_CustomerId(customerId)) {
            String title = "Quotation v" + (quotation.getVersion() != null ? quotation.getVersion() : "1")
                    + (quotation.getRoomType() != null ? " – " + quotation.getRoomType() : "");
            history.add(new CustomerHistoryItem(
                    "QUOTATION",
                    quotation.getQuotationId().toString(),
                    title,
                    quotation.getStatus() != null ? quotation.getStatus().name() : null,
                    null,
                    quotation.getTotalAmount(),
                    quotation.getCheckInDate() != null ? quotation.getCheckInDate().toString() : null,
                    quotation.getCheckOutDate() != null ? quotation.getCheckOutDate().toString() : null,
                    null,
                    quotation.getCreatedAt() != null ? quotation.getCreatedAt().toString() : null,
                    quotation.getNotes()
            ));
        }

        // Sort newest first
        history.sort(Comparator.comparing(
                CustomerHistoryItem::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        return history;
    }
}
