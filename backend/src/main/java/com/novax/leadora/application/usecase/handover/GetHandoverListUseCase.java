package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import com.novax.leadora.infrastructure.persistence.specification.OpHandoverSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UC-20.2 — View Operational Handover List (Sales/Reservation).
 */
@Service
@RequiredArgsConstructor
public class GetHandoverListUseCase {

    private final OpHandoverRepository opHandoverRepository;
    private final BookingDetailRepository bookingDetailRepository;

    @Transactional(readOnly = true)
    public Page<ArrivalHandoverResponse> execute(String search, String status, String arrivalDate,
                                                 String sortBy, String sortDir, int page, int size) {
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        HandoverStatus statusFilter = parseStatus(status);
        LocalDate arrivalFilter = parseDate(arrivalDate);
        Specification<OpHandoverEntity> spec =
                OpHandoverSpecification.forOperations(search, statusFilter, arrivalFilter);

        Page<OpHandoverEntity> handovers = opHandoverRepository.findAll(spec, pageable);

        // Batch-load room/service lines to build the summary without N+1.
        List<UUID> bookingIds = handovers.getContent().stream()
                .map(h -> h.getBooking() != null ? h.getBooking().getBookingId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        final Map<UUID, List<BookingDetailEntity>> detailsByBooking = bookingIds.isEmpty()
                ? Collections.emptyMap()
                : bookingDetailRepository.findByBooking_BookingIdIn(bookingIds).stream()
                        .collect(Collectors.groupingBy(d -> d.getBooking().getBookingId()));

        return handovers.map(h -> {
            UUID bookingId = h.getBooking() != null ? h.getBooking().getBookingId() : null;
            List<BookingDetailEntity> details = detailsByBooking.getOrDefault(bookingId, Collections.emptyList());
            return ArrivalHandoverResponse.fromList(h, details);
        });
    }

    private HandoverStatus parseStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return HandoverStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
