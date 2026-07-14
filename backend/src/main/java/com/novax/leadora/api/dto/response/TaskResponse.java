package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResponse {

    private UUID taskId;
    private String title;
    private String description;
    private TaskPriority priority;
    private TaskStatus status;
    private String resultNote;

    private UUID assignedUserId;
    private String assignedUserName;

    private UUID createdById;
    private String createdByName;

    private UUID leadId;
    private String leadName;
    private String leadPhone;
    private String leadEmail;
    private String leadCompanyName;

    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerCompanyName;

    private UUID dealId;
    private String dealName;

    // Extended linked-record context — populated by fromDetail() only (the list
    // mapper stays lean to avoid N+1 on associations the list doesn't render).
    private DealPipelineStage dealStage;
    private BigDecimal dealValue;
    private String dealCustomerName;
    private String dealOwnerName;

    private LeadStatus leadStatus;
    private String leadSource;
    private String leadOwnerName;

    private OffsetDateTime startAt;
    private OffsetDateTime endAt;

    private String primaryContactName;
    private String primaryContactPhone;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Computed property: true if status == OPEN && now > endAt */
    private Boolean isOverdue;

    /** Lean mapping for list responses. */
    public static TaskResponse from(TaskEntity entity) {
        return baseBuilder(entity).build();
    }

    /**
     * Detail mapping — adds the linked record's business context (deal
     * stage/value/customer/owner, lead status/source/owner) so Task Detail can
     * render a rich, navigable "Related To" card. Additive: every extra field is
     * {@code NON_NULL}-omitted when there's no linked record of that type.
     */
    public static TaskResponse fromDetail(TaskEntity entity) {
        final var builder = baseBuilder(entity);

        if (entity.getDeal() != null) {
            final var deal = entity.getDeal();
            builder.dealStage(deal.getPipelineStage())
                    .dealValue(deal.getExpectedRevenue())
                    .dealCustomerName(deal.getCustomer() != null ? deal.getCustomer().getFullName() : null)
                    .dealOwnerName(deal.getAssignedUser() != null ? deal.getAssignedUser().getFullName() : null);
        }
        if (entity.getLead() != null) {
            final var lead = entity.getLead();
            builder.leadStatus(lead.getStatus())
                    .leadSource(lead.getSource())
                    .leadOwnerName(lead.getAssignedUser() != null ? lead.getAssignedUser().getFullName() : null);
        }
        return builder.build();
    }

    private static TaskResponseBuilder baseBuilder(TaskEntity entity) {
        final OffsetDateTime now = OffsetDateTime.now();
        final boolean isOverdue = entity.getStatus() == TaskStatus.OPEN
                && entity.getEndAt() != null
                && now.isAfter(entity.getEndAt());

        return TaskResponse.builder()
                .taskId(entity.getTaskId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .resultNote(entity.getResultNote())
                .assignedUserId(entity.getAssignedUser() != null ? entity.getAssignedUser().getUserId() : null)
                .assignedUserName(entity.getAssignedUser() != null ? entity.getAssignedUser().getFullName() : null)
                .createdById(entity.getCreatedBy() != null ? entity.getCreatedBy().getUserId() : null)
                .createdByName(entity.getCreatedBy() != null ? entity.getCreatedBy().getFullName() : null)
                .leadId(entity.getLead() != null ? entity.getLead().getLeadId() : null)
                .leadName(entity.getLead() != null ? entity.getLead().getFullName() : null)
                .leadPhone(entity.getLead() != null ? entity.getLead().getPhone() : null)
                .leadEmail(entity.getLead() != null ? entity.getLead().getEmail() : null)
                .leadCompanyName(entity.getLead() != null ? entity.getLead().getCompanyName() : null)
                .customerId(entity.getCustomer() != null ? entity.getCustomer().getCustomerId() : null)
                .customerName(entity.getCustomer() != null ? entity.getCustomer().getFullName() : null)
                .customerPhone(entity.getCustomer() != null ? entity.getCustomer().getPhone() : null)
                .customerEmail(entity.getCustomer() != null ? entity.getCustomer().getEmail() : null)
                .customerCompanyName(entity.getCustomer() != null ? entity.getCustomer().getCompanyName() : null)
                .dealId(entity.getDeal() != null ? entity.getDeal().getDealId() : null)
                .dealName(entity.getDeal() != null ? entity.getDeal().getDealName() : null)
                .startAt(entity.getStartAt())
                .endAt(entity.getEndAt())
                .primaryContactName(entity.getPrimaryContactName())
                .primaryContactPhone(entity.getPrimaryContactPhone())
                .isOverdue(isOverdue)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());
    }
}
