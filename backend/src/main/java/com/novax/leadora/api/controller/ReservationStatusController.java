package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CancelReservationRequest;
import com.novax.leadora.api.dto.request.UpdateStatusRequest;
import com.novax.leadora.api.dto.response.ReservationResponse;
import com.novax.leadora.application.usecase.reservation.CancelReservationUseCase;
import com.novax.leadora.application.usecase.reservation.GetReservationDetailUseCase;
import com.novax.leadora.application.usecase.reservation.GetReservationListUseCase;
import com.novax.leadora.application.usecase.reservation.UpdateReservationStatusUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservation-status")
@RequiredArgsConstructor
public class ReservationStatusController {

    private final GetReservationListUseCase getReservationListUseCase;
    private final GetReservationDetailUseCase getReservationDetailUseCase;
    private final UpdateReservationStatusUseCase updateReservationStatusUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;

    /** UC-19.1 — View Reservation List */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ReservationResponse>>> getReservations(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ReservationResponse> reservations = getReservationListUseCase.execute(search, status, sortBy, sortDir, page, size);
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    /** UC-19.2 — View Reservation Detail */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationDetail(
            @PathVariable UUID id
    ) {
        ReservationResponse reservation = getReservationDetailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(reservation));
    }

    /** UC-19.3 — Update Reservation Status */
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ReservationResponse>> updateReservationStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        ReservationResponse response = updateReservationStatusUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Reservation status updated successfully"));
    }

    /** UC-19.4 — Cancel Reservation */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservation(
            @PathVariable UUID id,
            @Valid @RequestBody CancelReservationRequest request
    ) {
        ReservationResponse response = cancelReservationUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Reservation cancelled successfully"));
    }
}
