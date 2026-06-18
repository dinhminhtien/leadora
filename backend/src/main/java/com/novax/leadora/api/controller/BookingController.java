package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CreateBookingRequest;
import com.novax.leadora.api.dto.request.ProcessBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.api.dto.response.RoomAvailabilityResponse;
import com.novax.leadora.application.usecase.booking.CheckRoomAvailabilityUseCase;
import com.novax.leadora.application.usecase.booking.CreateBookingRequestUseCase;
import com.novax.leadora.application.usecase.booking.GetBookingDetailUseCase;
import com.novax.leadora.application.usecase.booking.GetBookingListUseCase;
import com.novax.leadora.application.usecase.booking.ProcessBookingUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookingController {

    private final CheckRoomAvailabilityUseCase checkRoomAvailabilityUseCase;
    private final CreateBookingRequestUseCase createBookingRequestUseCase;
    private final GetBookingListUseCase getBookingListUseCase;
    private final GetBookingDetailUseCase getBookingDetailUseCase;
    private final ProcessBookingUseCase processBookingUseCase;

    /** UC-18.1 — View Room Availability */
    @GetMapping("/check-availability")
    public ResponseEntity<ApiResponse<List<RoomAvailabilityResponse>>> checkAvailability(
            @RequestParam LocalDate checkInDate,
            @RequestParam LocalDate checkOutDate,
            @RequestParam(required = false) UUID productId
    ) {
        List<RoomAvailabilityResponse> availability = checkRoomAvailabilityUseCase.execute(checkInDate, checkOutDate, productId);
        return ResponseEntity.ok(ApiResponse.success(availability));
    }

    /** UC-18.2 — Submit Booking Request */
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> submitBookingRequest(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingResponse booking = createBookingRequestUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(booking, "Booking request submitted successfully"));
    }

    /** UC-18.3 — View Booking Request List */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getBookings(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<BookingResponse> bookings = getBookingListUseCase.execute(search, status, sortBy, sortDir, page, size);
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }

    /** UC-18.4 — View Booking Request Detail */
    @GetMapping("/{bookingId}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingDetail(
            @PathVariable UUID bookingId
    ) {
        BookingResponse booking = getBookingDetailUseCase.execute(bookingId);
        return ResponseEntity.ok(ApiResponse.success(booking));
    }

    /** UC-18.5 — Process Booking Request (Approve/Reject) */
    @PutMapping("/{bookingId}/process")
    public ResponseEntity<ApiResponse<BookingResponse>> processBooking(
            @PathVariable UUID bookingId,
            @Valid @RequestBody ProcessBookingRequest request
    ) {
        BookingResponse booking = processBookingUseCase.execute(bookingId, request);
        String message = "Booking approved successfully";
        if (request.getStatus().trim().equalsIgnoreCase("REJECTED")) {
            message = "Booking request rejected successfully";
        }
        return ResponseEntity.ok(ApiResponse.success(booking, message));
    }
}
