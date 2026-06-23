package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.api.dto.response.BookingDetailResponse;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.specification.BookingSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetBookingListUseCase {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;

    @Transactional(readOnly = true)
    public Page<BookingResponse> execute(String search, String status, String sortBy, String sortDir, int page, int size) {
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        Specification<BookingEntity> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(search)) {
            spec = spec.and(BookingSpecification.search(search));
        }

        if (StringUtils.hasText(status)) {
            try {
                BookingStatus statusParam = BookingStatus.valueOf(status.toUpperCase());
                spec = spec.and(BookingSpecification.hasStatus(statusParam));
            } catch (IllegalArgumentException ignored) {}
        }

        Page<BookingEntity> bookings = bookingRepository.findAll(spec, pageable);

        // Batch load all details for the current page to prevent N+1 Queries
        List<UUID> bookingIds = bookings.getContent().stream()
                .map(BookingEntity::getBookingId)
                .collect(Collectors.toList());

        Map<UUID, List<BookingDetailEntity>> detailsByBookingId = Collections.emptyMap();
        if (!bookingIds.isEmpty()) {
            List<BookingDetailEntity> allDetails = bookingDetailRepository.findByBooking_BookingIdIn(bookingIds);
            detailsByBookingId = allDetails.stream()
                    .collect(Collectors.groupingBy(detail -> detail.getBooking().getBookingId()));
        }

        final Map<UUID, List<BookingDetailEntity>> finalDetailsMap = detailsByBookingId;

        return bookings.map(booking -> {
            List<BookingDetailEntity> details = finalDetailsMap.getOrDefault(booking.getBookingId(), Collections.emptyList());
            List<BookingDetailResponse> detailResponses = details.stream()
                    .map(BookingDetailResponse::from)
                    .collect(Collectors.toList());
            return BookingResponse.from(booking, detailResponses);
        });
    }
}
