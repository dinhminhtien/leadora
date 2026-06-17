package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.api.dto.response.RoomAvailabilityResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckRoomAvailabilityUseCase {

    private final ProductServiceRepository productServiceRepository;
    private final BookingRepository bookingRepository;
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

        List<BookingStatus> activeStatuses = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);
        
        List<BookingEntity> allBookings = bookingRepository.findAll();
        List<BookingEntity> overlappingBookings = new ArrayList<>();
        
        for (BookingEntity booking : allBookings) {
            if (activeStatuses.contains(booking.getStatus())) {
                if (booking.getCheckInDate().isBefore(checkOutDate) && booking.getCheckOutDate().isAfter(checkInDate)) {
                    overlappingBookings.add(booking);
                }
            }
        }

        List<RoomAvailabilityResponse> results = new ArrayList<>();

        for (ProductServiceEntity product : products) {
            if (product.getStatus() != ProductStatus.ACTIVE) {
                continue;
            }

            int totalBooked = 0;
            for (BookingEntity booking : overlappingBookings) {
                List<BookingDetailEntity> details = bookingDetailRepository.findByBooking_BookingId(booking.getBookingId());
                for (BookingDetailEntity detail : details) {
                    if (detail.getProductService() != null && detail.getProductService().getProductId().equals(product.getProductId())) {
                        totalBooked += detail.getQuantity();
                    }
                }
            }

            String name = product.getName();
            int capacity = 10;
            if (name != null) {
                if (name.contains("Suite")) {
                    capacity = 5;
                } else if (name.contains("Deluxe")) {
                    capacity = 10;
                } else if (name.contains("Standard")) {
                    capacity = 15;
                }
            }

            boolean isAvailable = (capacity - totalBooked) > 0;

            results.add(RoomAvailabilityResponse.builder()
                    .productId(product.getProductId())
                    .name(product.getName())
                    .category(product.getCategory())
                    .unitPrice(product.getUnitPrice())
                    .unit(product.getUnit())
                    .totalBooked(totalBooked)
                    .isAvailable(isAvailable)
                    .build());
        }

        return results;
    }
}
