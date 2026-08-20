package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.ProcessBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.application.usecase.booking.GetBookingDetailUseCase;
import com.novax.leadora.application.usecase.booking.GetBookingListUseCase;
import com.novax.leadora.application.usecase.booking.ProcessBookingUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Bookings, read by Sales and worked by the Reservation team.
 *
 * <p><b>Bookings are not created here.</b> The only way one comes into existence is
 * {@code POST /api/v1/quotations/{id}/convert}, which checks the contract the customer
 * acknowledged, verifies the rooms against allotment or the Reservation team's answer, and hands
 * the quotation's allotment hold over to the booking in the same transaction.
 *
 * <p>A second creation endpoint used to live on this controller and did none of that: it took
 * any ACCEPTED quotation, skipped the contract and the room check outright, and left the
 * quotation's hold in place while the new booking also counted against allotment — deducting the
 * same rooms twice. Its booking code carried a random suffix, so two clicks produced two
 * bookings for one quotation instead of colliding. It is gone; see
 * {@code ConvertToBookingUseCase} for the one path that remains.
 *
 * <p>Room availability is not answered here either. What the CRM has committed is part of the
 * allotment picture on {@code /api/v1/room-availability}, whose figures come from the
 * Reservation team; anything that allocation cannot settle goes to them as a room request.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','RESERVATION','MANAGER','ADMIN') and @access.can('BOOKING_VIEW')")
public class BookingController {

    private final GetBookingListUseCase getBookingListUseCase;
    private final GetBookingDetailUseCase getBookingDetailUseCase;
    private final ProcessBookingUseCase processBookingUseCase;

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

    /**
     * UC-18.4 — View Booking Request Detail.
     *
     * <p>Front Office is named here and nowhere else on this controller. The arrival desk never
     * triages the booking queue, but {@code DepositPaymentScreen}'s "Print receipt" resolves the
     * booking behind a payment the desk is settling, and that call 403'd for FO — the receipt
     * failed silently at the one counter that prints receipts. Detail only: the list stays with the
     * desks that own the queue.
     */
    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAnyRole('SALES','RESERVATION','MANAGER','ADMIN','FO','FRONT_OFFICE') "
            + "and @access.can('BOOKING_VIEW')")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingDetail(
            @PathVariable UUID bookingId
    ) {
        BookingResponse booking = getBookingDetailUseCase.execute(bookingId);
        return ResponseEntity.ok(ApiResponse.success(booking));
    }

    /** UC-18.5 — Process Booking Request (Approve/Reject) */
    @PutMapping("/{bookingId}/process")
    @PreAuthorize("hasAnyRole('RESERVATION', 'MANAGER', 'ADMIN') and @access.can('BOOKING_WRITE')")
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
