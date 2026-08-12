package com.novax.leadora.application.usecase.inventory;

import com.novax.leadora.api.dto.response.RoomAvailabilityGridResponse;
import com.novax.leadora.api.dto.response.RoomStayAvailabilityResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The read side of room allotment: the grid the Reservation team maintains, and the single answer
 * a sales rep needs for one enquiry.
 *
 * <p>What this replaces is a conversation. Until now "is a Deluxe free on the 20th?" could only be
 * answered by messaging the Reservation team, who opened the real PMS and replied — minutes or
 * hours later, for a question that recurs dozens of times a day.
 */
@Service
@RequiredArgsConstructor
public class GetRoomAvailabilityUseCase {

    private final ProductServiceRepository productServiceRepository;
    private final RoomAvailabilityService roomAvailabilityService;

    /** BR-46. Bounds one request to roughly a quarter's worth of nights per room type. */
    private static final long MAX_GRID_DAYS = 90;

    /**
     * How far ahead Sales may look. Quota is commercially sensitive to the hotel, so a
     * compromised sales account should not be able to pull a year of forward allocation in one
     * request. The Reservation team, who maintain it, are not limited.
     */
    @Value("${leadora.room-allotment.sales-horizon-days:180}")
    private long salesHorizonDays;

    /**
     * @param to inclusive last night, matching how a calendar range reads on screen; converted to
     *           the half-open form the rest of the code uses
     */
    @Transactional(readOnly = true)
    public RoomAvailabilityGridResponse grid(LocalDate from, LocalDate to, UUID productId, boolean limitHorizon) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException("INVALID_DATE_RANGE",
                    "The end date must not be before the start date.", HttpStatus.BAD_REQUEST);
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_GRID_DAYS) {
            throw new BusinessException("DATE_RANGE_TOO_WIDE",
                    "Please request at most " + MAX_GRID_DAYS + " days at a time.", HttpStatus.BAD_REQUEST);
        }
        assertWithinHorizon(to, limitHorizon);

        List<ProductServiceEntity> products = sellableRooms(productId);
        Map<UUID, List<NightAvailability>> nights =
                roomAvailabilityService.nights(products, from, to.plusDays(1), null);

        return RoomAvailabilityGridResponse.builder()
                .from(from)
                .to(to)
                .rooms(products.stream()
                        .map(product -> RoomAvailabilityGridResponse.RoomRow.of(
                                product, nights.getOrDefault(product.getProductId(), List.of())))
                        .toList())
                .build();
    }

    /** One answer per room type for a stay — {@code checkOut} is exclusive, as a stay always is. */
    @Transactional(readOnly = true)
    public List<RoomStayAvailabilityResponse> forStay(
            LocalDate checkIn, LocalDate checkOut, int quantity, UUID productId, boolean limitHorizon) {

        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new BusinessException("INVALID_DATE_RANGE",
                    "Check-out date must be after check-in date.", HttpStatus.BAD_REQUEST);
        }
        assertWithinHorizon(checkOut, limitHorizon);

        List<ProductServiceEntity> products = sellableRooms(productId);
        Map<UUID, StayAvailability> stays =
                roomAvailabilityService.stays(products, checkIn, checkOut, null);

        return products.stream()
                .map(product -> RoomStayAvailabilityResponse.of(
                        product, stays.get(product.getProductId()), Math.max(quantity, 1)))
                .toList();
    }

    private List<ProductServiceEntity> sellableRooms(UUID productId) {
        List<ProductServiceEntity> rooms = productServiceRepository.findByCategory(ProductCategory.ROOM)
                .stream()
                .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                .filter(product -> productId == null || productId.equals(product.getProductId()))
                .toList();

        if (productId != null && rooms.isEmpty()) {
            throw new BusinessException("INVALID_ROOM_TYPE",
                    "That room type does not exist or is not currently on sale.", HttpStatus.BAD_REQUEST);
        }
        return rooms;
    }

    private void assertWithinHorizon(LocalDate latestDate, boolean limitHorizon) {
        if (limitHorizon && latestDate.isAfter(LocalDate.now().plusDays(salesHorizonDays))) {
            throw new BusinessException("DATE_BEYOND_HORIZON",
                    "Room availability can only be viewed up to " + salesHorizonDays
                            + " days ahead. Ask the Reservation team about later dates.",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
