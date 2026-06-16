package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadResponse {

    private UUID leadId;
    private String fullName;
    private String email;
    private String phone;
    private String companyName;
    private String source;
    private LeadStatus status;
    private String notes;
    private OffsetDateTime convertedAt;

    private UUID assignedUserId;
    private String assignedUserName;

    private UUID createdById;
    private String createdByName;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static LeadResponse from(LeadEntity entity) {
        return LeadResponse.builder()
                .leadId(entity.getLeadId())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .companyName(entity.getCompanyName())
                .source(entity.getSource())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .convertedAt(entity.getConvertedAt())
                .assignedUserId(entity.getAssignedUser() != null ? entity.getAssignedUser().getUserId() : null)
                .assignedUserName(entity.getAssignedUser() != null ? entity.getAssignedUser().getFullName() : null)
                .createdById(entity.getCreatedBy() != null ? entity.getCreatedBy().getUserId() : null)
                .createdByName(entity.getCreatedBy() != null ? entity.getCreatedBy().getFullName() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
