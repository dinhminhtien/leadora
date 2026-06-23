package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerResponse {

    private UUID customerId;
    private CustomerType customerType;
    private String fullName;
    private String email;
    private String phone;
    private String companyName;
    private String taxCode;
    private String address;
    private CustomerStatus status;
    private UUID assignedUserId;
    private String assignedUserName;
    private UUID createdById;
    private String createdByName;
    private UUID leadId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static CustomerResponse from(CustomerEntity entity) {
        return CustomerResponse.builder()
                .customerId(entity.getCustomerId())
                .customerType(entity.getCustomerType())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .companyName(entity.getCompanyName())
                .taxCode(entity.getTaxCode())
                .address(entity.getAddress())
                .status(entity.getStatus())
                .assignedUserId(entity.getAssignedUser() != null ? entity.getAssignedUser().getUserId() : null)
                .assignedUserName(entity.getAssignedUser() != null ? entity.getAssignedUser().getFullName() : null)
                .createdById(entity.getCreatedBy() != null ? entity.getCreatedBy().getUserId() : null)
                .createdByName(entity.getCreatedBy() != null ? entity.getCreatedBy().getFullName() : null)
                .leadId(entity.getLeadId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
