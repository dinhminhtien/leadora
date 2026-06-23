package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CustomerResponse {
    private UUID id;
    private String name;
    private String email;

    public static CustomerResponse from(CustomerEntity entity) {
        return CustomerResponse.builder()
                .id(entity.getCustomerId())
                .name(entity.getFullName())
                .email(entity.getEmail())
                .build();
    }
}
