package com.novax.leadora.application.usecase.inventory;

import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoomAllotmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomAvailabilityDbFetcher {

    private final RoomAllotmentRepository allotmentRepository;
    private final BookingDetailRepository bookingDetailRepository;

    @Cacheable(value = "room-allotment-nights", key = "'allotments:' + #productIds + ':' + #from + ':' + #toExclusive")
    public List<AllotmentNightDto> getPublishedAllotments(
            Collection<UUID> productIds,
            LocalDate from,
            LocalDate toExclusive) {
        return allotmentRepository.findPublished(productIds, from, toExclusive).stream()
                .map(row -> new AllotmentNightDto(
                        row.getProduct().getProductId(),
                        row.getStayDate(),
                        row.getAllottedQty(),
                        row.getClosed(),
                        row.getAsOf()
                ))
                .toList();
    }

    @Cacheable(value = "room-allotment-nights", key = "'spans:' + #statuses + ':' + #productIds + ':' + #from + ':' + #toExclusive")
    public List<CommittedSpanDto> getCommittedSpans(
            Collection<BookingStatus> statuses,
            Collection<UUID> productIds,
            LocalDate from,
            LocalDate toExclusive) {
        return bookingDetailRepository.findCommittedSpans(statuses, productIds, from, toExclusive).stream()
                .map(span -> new CommittedSpanDto(
                        span.getProductId(),
                        span.getCheckInDate(),
                        span.getCheckOutDate(),
                        span.getQuantity()
                ))
                .toList();
    }
}
