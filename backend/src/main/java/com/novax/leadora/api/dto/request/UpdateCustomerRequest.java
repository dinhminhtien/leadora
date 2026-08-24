package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateCustomerRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = LeadFieldLimits.FULL_NAME, message = "Full name must be between 2 and 40 characters")
    @Pattern(regexp = CreateCustomerRequest.NAME_PATTERN, message = CreateCustomerRequest.NAME_MESSAGE)
    private String fullName;

    private CustomerType customerType;

    @Email(message = "Invalid email format")
    @Pattern(regexp = "^$|" + CreateCustomerRequest.EMAIL_PATTERN, message = "Invalid email format")
    @Size(max = LeadFieldLimits.EMAIL, message = "Email cannot exceed 40 characters")
    private String email;

    @Pattern(
            regexp = LeadFieldLimits.PHONE_PATTERN,
            message = LeadFieldLimits.PHONE_MESSAGE
    )
    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phone;

    @Size(max = LeadFieldLimits.COMPANY_NAME, message = "Company name cannot exceed 40 characters")
    private String companyName;

    @Size(max = LeadFieldLimits.TAX_CODE, message = "Tax code cannot exceed 25 characters")
    private String taxCode;

    private String address;

    private CustomerStatus status;

    private UUID assignedUserId;
}
