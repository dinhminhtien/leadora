package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CreateRoomRequestRequest;
import com.novax.leadora.api.dto.request.RespondRoomRequestRequest;
import com.novax.leadora.api.dto.response.RoomRequestResponse;
import com.novax.leadora.application.usecase.roomrequest.CreateRoomRequestUseCase;
import com.novax.leadora.application.usecase.roomrequest.GetRoomRequestsUseCase;
import com.novax.leadora.application.usecase.roomrequest.RespondRoomRequestUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Room availability requests between Sales and the Reservation team.
 *
 * <p>This CRM owns no room inventory: Sales asks, the Reservation team answers from the
 * hotel's real PMS. The answer is advisory — it never blocks a quotation (see
 * {@code RoomConfirmationReader}).
 *
 * <p>Answering is the Reservation team's job alone. Managers and sales staff deliberately
 * cannot respond: neither has access to the PMS, so an approval from them would be a guess
 * recorded as a fact. ADMIN keeps access for support only.
 */
@RestController
@RequestMapping("/api/v1/room-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','RESERVATION','MANAGER','ADMIN') and @access.can('ROOM_REQUEST_VIEW')")
public class RoomRequestController {

    private final CreateRoomRequestUseCase createRoomRequestUseCase;
    private final RespondRoomRequestUseCase respondRoomRequestUseCase;
    private final GetRoomRequestsUseCase getRoomRequestsUseCase;

    /** Sales asks the Reservation team whether a quotation's rooms are available. */
    @PostMapping
    @PreAuthorize("hasAnyRole('SALES','MANAGER','ADMIN') and @access.can('ROOM_REQUEST_VIEW')")
    public ResponseEntity<ApiResponse<RoomRequestResponse>> createRoomRequest(
            @Valid @RequestBody CreateRoomRequestRequest request
    ) {
        RoomRequestResponse created = createRoomRequestUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Room availability request sent to the Reservation team"));
    }

    /**
     * The Reservation inbox — oldest first, filterable by status. Reservation-only: this is
     * their work queue, and Sales reads its own requests through
     * {@link #getByQuotation(UUID)} instead.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RESERVATION','ADMIN') and @access.can('ROOM_REQUEST_VIEW')")
    public ResponseEntity<ApiResponse<Page<RoomRequestResponse>>> getRoomRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<RoomRequestResponse> requests = getRoomRequestsUseCase.execute(status, page, size);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    /** Every request raised for one quotation, newest first — the Sales-side panel. */
    @GetMapping("/by-quotation/{quotationId}")
    public ResponseEntity<ApiResponse<List<RoomRequestResponse>>> getByQuotation(
            @PathVariable UUID quotationId
    ) {
        List<RoomRequestResponse> requests = getRoomRequestsUseCase.executeByQuotation(quotationId);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    /** The Reservation team answers: CONFIRMED (optionally held until) or REJECTED + reason. */
    @PutMapping("/{requestId}/respond")
    @PreAuthorize("hasAnyRole('RESERVATION','ADMIN') and @access.can('ROOM_REQUEST_APPROVE')")
    public ResponseEntity<ApiResponse<RoomRequestResponse>> respond(
            @PathVariable UUID requestId,
            @Valid @RequestBody RespondRoomRequestRequest request
    ) {
        RoomRequestResponse answered = respondRoomRequestUseCase.execute(requestId, request);
        String message = request.getDecision() == RoomRequestStatus.CONFIRMED
                ? "Rooms confirmed for the requesting sales rep"
                : "Room request rejected";
        return ResponseEntity.ok(ApiResponse.success(answered, message));
    }
}
