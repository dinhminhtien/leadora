package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.ActivityLogResponse;
import com.novax.leadora.application.usecase.activitylog.GetActivityLogUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activity-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ActivityLogController {

        private final GetActivityLogUseCase getActivityLogUseCase;
        private final ActivityLogRepository activityLogRepository;

        @GetMapping
        public ResponseEntity<ApiResponse<Page<ActivityLogResponse>>> getActivityLogs(
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) ActorType actorType,
                        @RequestParam(required = false) UUID actorUserId,
                        @RequestParam(required = false) String actorRoleSnapshot,
                        @RequestParam(required = false) ActivityLogType activityType,
                        @RequestParam(required = false) EntityType entityType,
                        @RequestParam(required = false) UUID entityId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
                        @RequestParam(required = false) String category,
                        @RequestParam(defaultValue = "EFFECTIVE") String view,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                GetActivityLogUseCase.FilterQuery query = GetActivityLogUseCase.FilterQuery.builder()
                                .keyword(keyword)
                                .actorType(actorType)
                                .actorUserId(actorUserId)
                                .actorRoleSnapshot(actorRoleSnapshot)
                                .activityType(activityType)
                                .entityType(entityType)
                                .entityId(entityId)
                                .startDate(startDate)
                                .endDate(endDate)
                                .view(view)
                                .category(category)
                                .build();

                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
                Page<ActivityLogEntity> result = getActivityLogUseCase.execute(query, pageable);

                // Map to response DTO without payload to optimize network payload
                Page<ActivityLogResponse> dtoPage = result.map(entity -> mapToResponse(entity, false));
                return ResponseEntity.ok(ApiResponse.success(dtoPage));
        }

        @GetMapping("/{id}")
        @Transactional(readOnly = true)
        public ResponseEntity<ApiResponse<ActivityLogResponse>> getActivityLogById(@PathVariable UUID id) {
                ActivityLogEntity entity = activityLogRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Activity log not found"));

                // Map to response DTO with payload for detail view
                return ResponseEntity.ok(ApiResponse.success(mapToResponse(entity, true)));
        }

        private ActivityLogResponse mapToResponse(ActivityLogEntity entity, boolean includePayload) {
                ActivityLogResponse.ActorDto actorDto;
                if (entity.getActorType() == ActorType.USER && entity.getActorUser() != null) {
                        actorDto = new ActivityLogResponse.ActorDto(
                                        entity.getActorType(),
                                        entity.getActorUser().getUserId(),
                                        entity.getActorUser().getFullName(),
                                        entity.getActorRoleSnapshot(),
                                        entity.getActorUser().getEmail());
                } else {
                        actorDto = new ActivityLogResponse.ActorDto(
                                        entity.getActorType(),
                                        null,
                                        "SYSTEM",
                                        null,
                                        null);
                }

                ActivityLogResponse.EntityDto entityDto = new ActivityLogResponse.EntityDto(
                                entity.getEntityType().name(),
                                entity.getEntityId());

                return new ActivityLogResponse(
                                entity.getId(),
                                actorDto,
                                entity.getActivityType().name(),
                                entityDto,
                                entity.getSummary(),
                                entity.getReason(),
                                entity.getCorrelationId(),
                                entity.getRecordOperation(),
                                entity.getRefActivityId(),
                                entity.getCreatedAt(),
                                includePayload ? entity.getPayload() : null);
        }
}
