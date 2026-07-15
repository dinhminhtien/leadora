package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CreateReminderRequest;
import com.novax.leadora.api.dto.request.UpdateReminderRequest;
import com.novax.leadora.api.dto.response.ReminderResponse;
import com.novax.leadora.application.usecase.reminder.CreateReminderUseCase;
import com.novax.leadora.application.usecase.reminder.DismissReminderUseCase;
import com.novax.leadora.application.usecase.reminder.EscalateReminderUseCase;
import com.novax.leadora.application.usecase.reminder.GetRemindersUseCase;
import com.novax.leadora.application.usecase.reminder.UpdateReminderUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final CreateReminderUseCase createReminderUseCase;
    private final GetRemindersUseCase getRemindersUseCase;
    private final DismissReminderUseCase dismissReminderUseCase;
    private final UpdateReminderUseCase updateReminderUseCase;
    private final EscalateReminderUseCase escalateReminderUseCase;

    /** UC-16.1 / UC-16.2: List reminders — filter by userId, status, date range; sort by date or priority */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> getAll(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime remindFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime remindTo,
            @RequestParam(required = false) String sortBy) {
        return ResponseEntity.ok(ApiResponse.success(
                getRemindersUseCase.execute(userId, status, remindFrom, remindTo, sortBy)));
    }

    /** UC-16.1: Create a manual reminder (Sales Staff / Manager / Admin) */
    @PostMapping
    @PreAuthorize("hasAnyRole('SALES', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReminderResponse>> create(
            @Valid @RequestBody CreateReminderRequest request) {
        ReminderResponse response = createReminderUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Reminder created successfully"));
    }

    /** UC-16.1: Dismiss (complete) a reminder — caller resolved from JWT */
    @PatchMapping("/{reminderId}/dismiss")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> dismiss(@PathVariable UUID reminderId) {
        dismissReminderUseCase.execute(reminderId);
        return ResponseEntity.ok(ApiResponse.success(null, "Reminder dismissed"));
    }

    /** UC-16.3: Update reminder details, mark done, or extend deadline — caller resolved from JWT */
    @PutMapping("/{reminderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReminderResponse>> update(
            @PathVariable UUID reminderId,
            @Valid @RequestBody UpdateReminderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(updateReminderUseCase.execute(reminderId, request)));
    }

    /** UC-16.4: Escalate overdue reminder to manager — caller resolved from JWT */
    @PostMapping("/{reminderId}/escalate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> escalate(@PathVariable UUID reminderId) {
        escalateReminderUseCase.execute(reminderId);
        return ResponseEntity.ok(ApiResponse.success(null, "Reminder escalated to manager"));
    }
}
