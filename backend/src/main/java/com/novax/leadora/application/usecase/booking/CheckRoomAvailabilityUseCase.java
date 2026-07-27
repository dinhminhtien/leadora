package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.api.dto.response.RoomAvailabilityResponse;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UC-18.1 — how much this CRM has already committed per room/service in a date range.
 *
 * <p>Deliberately NOT an availability calculator. This is a Sales CRM, not a PMS: it does
 * not know how many rooms the hotel has, so it cannot say whether a room is free. It
 * reports only what it genuinely owns — the bookings recorded in it — which the
 * Reservation team reconciles against the real PMS.
 *
 * <p>The earlier version invented capacity by pattern-matching product names ("Suite" → 5,
 * "Deluxe" → 10) and derived an {@code isAvailable} flag from it. Those numbers were
 * fiction and could block legitimate quotations, so they are gone. Whether rooms exist is
 * answered by the Reservation team through a room request, and enforced by
 * {@code RoomConfirmationReader}, which is advisory only.
 */
@Service
@RequiredArgsConstructor
public class CheckRoomAvailabilityUseCase {

    /** Bookings that still hold a room. */
    private static final List<BookingStatus> ACTIVE_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    private final ProductServiceRepository productServiceRepository;
    private final BookingDetailRepository bookingDetailRepository;

    @Transactional(readOnly = true)
    public List<RoomAvailabilityResponse> execute(LocalDate checkInDate, LocalDate checkOutDate, UUID productId) {
        List<ProductServiceEntity> products;
        if (productId != null) {
            products = productServiceRepository.findById(productId)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            products = productServiceRepository.findByCategory(ProductCategory.ROOM);
        }

        // Aggregated in SQL: the previous in-memory scan loaded every booking and then
        // issued one detail query per booking.
        Map<UUID, Long> committedByProduct = bookingDetailRepository
                .sumCommittedByProduct(ACTIVE_STATUSES, checkInDate, checkOutDate)
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getProductId(),
                        row -> row.getCommitted() != null ? row.getCommitted() : 0L,
                        (a, b) -> a));

        List<RoomAvailabilityResponse> results = new ArrayList<>();
        for (ProductServiceEntity product : products) {
            if (product.getStatus() != ProductStatus.ACTIVE) {
                continue;
            }
            results.add(RoomAvailabilityResponse.builder()
                    .productId(product.getProductId())
                    .name(product.getName())
                    .category(product.getCategory())
                    .unitPrice(product.getUnitPrice())
                    .unit(product.getUnit())
                    .totalBooked(committedByProduct.getOrDefault(product.getProductId(), 0L).intValue())
                    .build());
        }
        return results;
    }
}
