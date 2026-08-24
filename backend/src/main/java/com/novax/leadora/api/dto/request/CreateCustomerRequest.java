package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateCustomerRequest {

    public static final String NAME_PATTERN = "^[\\p{L}\\s'.-]{2,40}$";
    public static final String NAME_MESSAGE = "Full name must be 2-40 characters and contain only letters and standard punctuation";
    public static final String EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    public static final String PHONE_STRICT_PATTERN = "^(0|\\+84|84)?[0-9]{9,10}$|^\\d{10,11}$";

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = LeadFieldLimits.FULL_NAME, message = "Full name must be between 2 and 40 characters")
    @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
    private String fullName;

    @NotNull(message = "Customer type is required")
    private CustomerType customerType;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Pattern(regexp = EMAIL_PATTERN, message = "Invalid email format")
    @Size(max = LeadFieldLimits.EMAIL, message = "Email cannot exceed 40 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = PHONE_STRICT_PATTERN, message = "Phone number must be 10 or 11 digits")
    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phone;

    @Size(max = LeadFieldLimits.COMPANY_NAME, message = "Company name cannot exceed 40 characters")
    private String companyName;

    @Size(max = LeadFieldLimits.TAX_CODE, message = "Tax code cannot exceed 25 characters")
    private String taxCode;

    private String address;

    private UUID assignedUserId;
}
