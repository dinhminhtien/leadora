package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.ReconcileAllotmentRequest;
import com.novax.leadora.api.dto.request.UpsertRoomAllotmentRequest;
import com.novax.leadora.api.dto.response.AllotmentImportResponse;
import com.novax.leadora.api.dto.response.AllotmentReconciliationResponse;
import com.novax.leadora.api.dto.response.RoomAvailabilityGridResponse;
import com.novax.leadora.api.dto.response.RoomStayAvailabilityResponse;
import com.novax.leadora.application.usecase.inventory.GetRoomAvailabilityUseCase;
import com.novax.leadora.application.usecase.inventory.ImportAllotmentUseCase;
import com.novax.leadora.application.usecase.inventory.ReconcileAllotmentUseCase;
import com.novax.leadora.application.usecase.inventory.UpsertRoomAllotmentUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.RbacRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Room allotment — how many rooms the hotel has released to this CRM to sell.
 *
 * <p>This is the answer to the round trip the system was built around: Sales used to have to ask
 * the Reservation team, who checked the real PMS and replied, before a customer could be told
 * whether a room was free. Sales can now read the allocation directly and only escalate the cases
 * the allocation cannot settle.
 *
 * <p><b>Reading and publishing are deliberately different jobs.</b> Only the Reservation team may
 * write: they are the only ones with the hotel's real figures, so a number entered by anyone else
 * would be a guess recorded as a fact — the same reasoning that stops managers answering room
 * requests in {@code RoomRequestController}. ADMIN keeps write access for support only.
 *
 * <p>Front Office holds nothing here. The arrival desk works with guests already booked; forward
 * allocation is not part of that job.
 */
@RestController
@RequestMapping("/api/v1/room-availability")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','MANAGER','RESERVATION','ADMIN') and @access.can('INVENTORY_VIEW')")
public class RoomAvailabilityController {

    private final GetRoomAvailabilityUseCase getRoomAvailabilityUseCase;
    private final UpsertRoomAllotmentUseCase upsertRoomAllotmentUseCase;
    private final ReconcileAllotmentUseCase reconcileAllotmentUseCase;
    private final ImportAllotmentUseCase importAllotmentUseCase;

    /**
     * The grid: one row per room type, one cell per night, {@code from}/{@code to} inclusive.
     *
     * <p>Sales and Manager are capped at a forward horizon; the Reservation team, who maintain
     * the quota, are not. Quota is commercially sensitive to the hotel, and a rep has no reason
     * to pull a year of forward allocation in one request.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<RoomAvailabilityGridResponse>> grid(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID productId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                getRoomAvailabilityUseCase.grid(from, to, productId, horizonApplies(authentication))));
    }

    /**
     * The question a sales rep actually has: for this stay and this many rooms, what can I offer?
     * {@code checkOut} is exclusive — the departure morning is not a night.
     */
    @GetMapping("/for-stay")
    public ResponseEntity<ApiResponse<List<RoomStayAvailabilityResponse>>> forStay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(required = false) UUID productId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                getRoomAvailabilityUseCase.forStay(checkIn, checkOut, quantity, productId,
                        horizonApplies(authentication))));
    }

    /**
     * Publishes quota for a date range, one row written per night.
     *
     * <p>Succeeds even when the new figure is below what has already been sold: the hotel is
     * entitled to take an allocation back, and refusing the entry would leave the CRM holding a
     * number the Reservation team has just said is wrong (BR-49). The affected nights come back
     * in the response and the reps holding those bookings are notified.
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('RESERVATION','ADMIN') and @access.can('INVENTORY_WRITE')")
    public ResponseEntity<ApiResponse<List<LocalDate>>> publish(
            @Valid @RequestBody UpsertRoomAllotmentRequest request
    ) {
        List<LocalDate> oversold = upsertRoomAllotmentUseCase.execute(request);
        String message = oversold.isEmpty()
                ? "Room allotment updated"
                : "Room allotment updated, but " + oversold.size()
                        + " night(s) are now below the rooms already sold. The assigned sales staff have been notified.";
        return ResponseEntity.ok(ApiResponse.success(oversold, message));
    }

    /**
     * The daily reconciliation against the hotel's real system.
     *
     * <p>Takes what the desk can see — rooms still free — and reconstructs the block from it,
     * because a PMS reports availability, never "the block you were given was twelve". Doing that
     * subtraction by hand on every row would make the correction less reliable than the drift it
     * is fixing.
     *
     * <p>Reservation only. The whole exercise rests on reading the hotel's system, which is the
     * one thing that distinguishes this desk from everyone else on this screen.
     */
    @PostMapping("/reconcile")
    @PreAuthorize("hasAnyRole('RESERVATION','ADMIN') and @access.can('INVENTORY_WRITE')")
    public ResponseEntity<ApiResponse<AllotmentReconciliationResponse>> reconcile(
            @Valid @RequestBody ReconcileAllotmentRequest request
    ) {
        AllotmentReconciliationResponse result = reconcileAllotmentUseCase.execute(request);
        String message = result.getNightsChanged() == 0
                ? "Reconciled — every night already matched"
                : result.getNightsChanged() + " night(s) corrected";
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    /**
     * Bulk import from the spreadsheet the hotel sends (CSV).
     *
     * <p>Rows are matched to room types by name, because the hotel's file has never heard of our
     * product ids. Anything unmatched or ambiguous is rejected and listed in the response rather
     * than guessed at — quota landing on the wrong room type would misstate what can be sold,
     * which is worse than a row that simply does not land.
     */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('RESERVATION','ADMIN') and @access.can('INVENTORY_WRITE')")
    public ResponseEntity<ApiResponse<AllotmentImportResponse>> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime asOf
    ) {
        AllotmentImportResponse result = importAllotmentUseCase.execute(file, asOf);
        String message = result.getRowsRejected() == 0
                ? result.getNightsImported() + " night(s) imported"
                : result.getNightsImported() + " night(s) imported, "
                        + result.getRowsRejected() + " row(s) rejected";
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    /** The horizon protects the hotel's forward allocation; it does not apply to its custodians. */
    private static boolean horizonApplies(Authentication authentication) {
        if (authentication == null) {
            return true;
        }
        return authentication.getAuthorities().stream()
                .map(granted -> granted.getAuthority())
                .noneMatch(role -> role.equals("ROLE_RESERVATION")
                        || role.equals("ROLE_" + RbacRoles.ADMIN));
    }
}
