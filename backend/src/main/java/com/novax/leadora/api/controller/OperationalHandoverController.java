package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CreateHandoverRequest;
import com.novax.leadora.api.dto.request.UpdateHandoverRequest;
import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.application.usecase.handover.CreateHandoverUseCase;
import com.novax.leadora.application.usecase.handover.GetHandoverDetailUseCase;
import com.novax.leadora.application.usecase.handover.GetHandoverListUseCase;
import com.novax.leadora.application.usecase.handover.UpdateHandoverUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Operational Handover Management — Module 20 (UC-20.1 / UC-20.2 / UC-20.3 / UC-20.4).
 * Handles the creation, retrieval, and updating of handovers by Sales/Reservation staff.
 */
@RestController
@RequestMapping("/api/v1/operational-handovers")
@RequiredArgsConstructor
public class OperationalHandoverController {

    private final CreateHandoverUseCase createHandoverUseCase;
    private final GetHandoverListUseCase getHandoverListUseCase;
    private final GetHandoverDetailUseCase getHandoverDetailUseCase;
    private final UpdateHandoverUseCase updateHandoverUseCase;
    private final CurrentUserProvider currentUserProvider;

    /** UC-20.1 — Create Operational Handover. */
    @PostMapping
    @PreAuthorize("hasAnyRole('SALES', 'RESERVATION', 'ADMIN')")
    public ResponseEntity<ApiResponse<ArrivalHandoverResponse>> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody CreateHandoverRequest request
    ) {
        UserEntity actor = currentUserProvider.resolve(userId);
        ArrivalHandoverResponse response = createHandoverUseCase.execute(request, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Operational handover created successfully"));
    }

    /** UC-20.4 — Update Operational Handover. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES', 'RESERVATION', 'ADMIN')")
    public ResponseEntity<ApiResponse<ArrivalHandoverResponse>> update(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHandoverRequest request
    ) {
        UserEntity actor = currentUserProvider.resolve(userId);
        ArrivalHandoverResponse response = updateHandoverUseCase.execute(id, request, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Operational handover updated successfully"));
    }

    /** UC-20.3 — View Handover Detail. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES', 'RESERVATION', 'FO', 'FRONT_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<ArrivalHandoverResponse>> detail(@PathVariable UUID id) {
        ArrivalHandoverResponse response = getHandoverDetailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** UC-20.2 — View Handover List. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SALES', 'RESERVATION', 'FO', 'FRONT_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<ArrivalHandoverResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String arrivalDate,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ArrivalHandoverResponse> result =
                getHandoverListUseCase.execute(search, status, arrivalDate, sortBy, sortDir, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
