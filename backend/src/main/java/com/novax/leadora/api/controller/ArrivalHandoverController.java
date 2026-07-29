package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.UpdateReadinessStatusRequest;
import com.novax.leadora.api.dto.response.ArrivalHandoverResponse;
import com.novax.leadora.api.dto.response.ArrivalHandoverSummaryResponse;
import com.novax.leadora.application.usecase.handover.GetArrivalHandoverDetailUseCase;
import com.novax.leadora.application.usecase.handover.GetArrivalHandoverListUseCase;
import com.novax.leadora.application.usecase.handover.GetArrivalHandoverSummaryUseCase;
import com.novax.leadora.application.usecase.handover.UpdateHandoverReadinessUseCase;
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
 * Arrival Handover — Front Office desk (UC-22.1 / UC-22.2 / UC-22.3).
 *
 * <p>Front Office prepares for guest arrivals from the operational handovers submitted by
 * Sales/Reservation. FO may view the list/detail and update the arrival readiness status, but
 * (BR-27) cannot change booking/quotation/deal data — enforced by exposing readiness only.
 */
@RestController
@RequestMapping("/api/v1/arrival-handovers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FO','FRONT_OFFICE','MANAGER','ADMIN') and @access.can('HANDOVER_VIEW')")
public class ArrivalHandoverController {

    private final GetArrivalHandoverListUseCase getArrivalHandoverListUseCase;
    private final GetArrivalHandoverDetailUseCase getArrivalHandoverDetailUseCase;
    private final GetArrivalHandoverSummaryUseCase getArrivalHandoverSummaryUseCase;
    private final UpdateHandoverReadinessUseCase updateHandoverReadinessUseCase;
    private final CurrentUserProvider currentUserProvider;

    /**
     * UC-22.1 — Front Office desk summary (counts by readiness), on the same scope and filters as
     * the list. Readiness itself is deliberately not a parameter: the cards are how the user
     * applies a readiness filter, so counting them under one would zero the other three.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ArrivalHandoverSummaryResponse>> summary(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String arrivalDate,
            @RequestParam(required = false) String assignedFoUserId,
            @RequestParam(defaultValue = "false") boolean deskWide
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                getArrivalHandoverSummaryUseCase.execute(search, arrivalDate, assignedFoUserId, deskWide)));
    }

    /**
     * UC-22.1 — View Arrival Handover List.
     *
     * @param assignedFoUserId filter by responsible Front Office staff (step 5). Supervisors only;
     *                         silently ignored for Front Office Staff, whose visibility is decided
     *                         by the scope below rather than by a request parameter.
     * @param deskWide         Front Office Staff default to their own queue (plus arrivals nobody
     *                         is assigned to yet). Set this to take over the whole desk — a shift
     *                         rota needs it when the assignee is off duty. No effect for
     *                         Manager/Admin, who already see everything.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ArrivalHandoverResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String readinessStatus,
            @RequestParam(required = false) String arrivalDate,
            @RequestParam(required = false) String assignedFoUserId,
            @RequestParam(defaultValue = "false") boolean deskWide,
            // A front desk works forward from the next guest through the door, so the soonest
            // arrival leads. `createdAt desc` was the generic list default and put whichever
            // handover Sales happened to write last at the top, which is nobody's question.
            @RequestParam(defaultValue = "arrivalDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ArrivalHandoverResponse> result = getArrivalHandoverListUseCase.execute(
                search, readinessStatus, arrivalDate, assignedFoUserId, deskWide,
                sortBy, sortDir, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** UC-22.2 — View Arrival Handover Detail. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArrivalHandoverResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(getArrivalHandoverDetailUseCase.execute(id)));
    }

    /** UC-22.3 — Update Handover Readiness Status. */
    @PutMapping("/{id}/readiness")
    @PreAuthorize("hasAnyRole('FO','FRONT_OFFICE','MANAGER','ADMIN') and @access.can('HANDOVER_WRITE')")
    public ResponseEntity<ApiResponse<ArrivalHandoverResponse>> updateReadiness(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReadinessStatusRequest request
    ) {
        UserEntity actor = currentUserProvider.resolve(userId);
        ArrivalHandoverResponse response = updateHandoverReadinessUseCase.execute(id, request, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Readiness status updated successfully"));
    }
}
