package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.SlaRuleRequest;
import com.novax.leadora.api.dto.response.SlaMonitoringResponse;
import com.novax.leadora.api.dto.response.SlaReportResponse;
import com.novax.leadora.api.dto.response.SlaRuleResponse;
import com.novax.leadora.application.usecase.sla.CreateSlaRuleUseCase;
import com.novax.leadora.application.usecase.sla.DeleteSlaRuleUseCase;
import com.novax.leadora.application.usecase.sla.GetSlaMonitoringUseCase;
import com.novax.leadora.application.usecase.sla.GetSlaReportUseCase;
import com.novax.leadora.application.usecase.sla.GetSlaRuleByIdUseCase;
import com.novax.leadora.application.usecase.sla.GetSlaRulesUseCase;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.application.usecase.sla.UpdateSlaRuleUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sla")
@RequiredArgsConstructor
public class SlaController {

    private final GetSlaRulesUseCase getSlaRulesUseCase;
    private final GetSlaRuleByIdUseCase getSlaRuleByIdUseCase;
    private final CreateSlaRuleUseCase createSlaRuleUseCase;
    private final UpdateSlaRuleUseCase updateSlaRuleUseCase;
    private final DeleteSlaRuleUseCase deleteSlaRuleUseCase;
    private final GetSlaMonitoringUseCase getSlaMonitoringUseCase;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    private final GetSlaReportUseCase getSlaReportUseCase;

    /** UC-17.3 — Monitor SLA status across all entities */
    @GetMapping("/monitoring")
    public ResponseEntity<ApiResponse<List<SlaMonitoringResponse>>> getMonitoring(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String displayStatus) {
        return ResponseEntity.ok(ApiResponse.success(
                getSlaMonitoringUseCase.execute(entityType, displayStatus)));
    }

    /** UC-17.1 — List all SLA rules */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SlaRuleResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(getSlaRulesUseCase.execute()));
    }

    /** UC-17.1 — Get single SLA rule */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SlaRuleResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(getSlaRuleByIdUseCase.execute(id)));
    }

    /** UC-17.1 — Create new SLA rule (Admin/Manager only) */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<SlaRuleResponse>> create(@Valid @RequestBody SlaRuleRequest request) {
        SlaRuleResponse response = createSlaRuleUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "SLA rule created"));
    }

    /** UC-17.1 — Update existing SLA rule (Admin/Manager only) */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<SlaRuleResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SlaRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(updateSlaRuleUseCase.execute(id, request), "SLA rule updated"));
    }

    /** UC-17.1 — Delete SLA rule (Admin/Manager only) */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        deleteSlaRuleUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(null, "SLA rule deleted"));
    }

    /** UC-17.4 — Resolve a breached SLA tracking record (E4: authenticated users only) */
    @PatchMapping("/tracking/{trackingId}/resolve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> resolve(@PathVariable UUID trackingId) {
        resolveSlaBreachUseCase.execute(trackingId);
        return ResponseEntity.ok(ApiResponse.success(null, "SLA breach resolved"));
    }

    /** UC-17.6 — View SLA performance report (Admin, Manager, Reservation Staff, Front Office) */
    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'RESERVATION_STAFF', 'FRONT_OFFICE')")
    public ResponseEntity<ApiResponse<SlaReportResponse>> getReport(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().minusDays(30)}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now()}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String entityType) {
        return ResponseEntity.ok(ApiResponse.success(
                getSlaReportUseCase.execute(from, to, activityType, entityType)));
    }
}
