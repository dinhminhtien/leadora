package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateCustomerRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255)
    private String fullName;

    private CustomerType customerType;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @Size(max = 20)
    private String phone;

    @Size(max = 255)
    private String companyName;

    @Size(max = 50)
    private String taxCode;

    private String address;

    private CustomerStatus status;

    private UUID assignedUserId;
}
