package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.InteractionTimelineResponse;
import com.novax.leadora.application.usecase.timeline.GetInteractionTimelineDetailUseCase;
import com.novax.leadora.application.usecase.timeline.GetInteractionTimelineListUseCase;
import com.novax.leadora.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interaction-timeline")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','MANAGER')")
public class InteractionTimelineController {

    private final GetInteractionTimelineListUseCase getListUseCase;
    private final GetInteractionTimelineDetailUseCase getDetailUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InteractionTimelineResponse>>> getTimeline(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID agentId) {
        List<InteractionTimelineResponse> timeline = getListUseCase.execute(search, type, agentId);
        return ResponseEntity.ok(ApiResponse.success(timeline));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InteractionTimelineResponse>> getDetail(@PathVariable UUID id) {
        InteractionTimelineResponse detail = getDetailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }
}
