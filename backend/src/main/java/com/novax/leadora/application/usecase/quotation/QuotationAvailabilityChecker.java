package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.response.RoomAvailabilityResponse;
import com.novax.leadora.application.usecase.booking.CheckRoomAvailabilityUseCase;
import com.novax.leadora.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Shared room-availability gate for quotation flows that promise a room to a customer
 * (Create, Revise, Convert-to-booking) — BR-24, UC-14.1/14.5/14.7 exception "Invalid Room
 * Type / Unavailable Dates".
 */
@Component
@RequiredArgsConstructor
public class QuotationAvailabilityChecker {

    private final CheckRoomAvailabilityUseCase checkRoomAvailabilityUseCase;

    public void assertRoomAvailable(LocalDate checkInDate, LocalDate checkOutDate, String roomType, int quantity) {
        List<RoomAvailabilityResponse> rooms = checkRoomAvailabilityUseCase.execute(checkInDate, checkOutDate, null);

        RoomAvailabilityResponse match = rooms.stream()
                .filter(r -> r.getName() != null && r.getName().equalsIgnoreCase(roomType))
                .findFirst()
                .orElse(null);

        if (match == null) {
            throw new BusinessException("INVALID_ROOM_TYPE",
                    "Room type \"" + roomType + "\" is not a valid, active room product", HttpStatus.BAD_REQUEST);
        }

        if (match.getTotalRooms() == null) {
            throw new BusinessException("ROOM_CAPACITY_UNCONFIGURED",
                    "Capacity is not configured for room type: " + roomType, HttpStatus.BAD_REQUEST);
        }

        if (match.getAvailableRooms() < quantity) {
            throw new BusinessException("ROOM_UNAVAILABLE",
                    "Not enough rooms available for " + roomType + ". Available: " + match.getAvailableRooms() + ", Requested: " + quantity, HttpStatus.BAD_REQUEST);
        }
    }

}
