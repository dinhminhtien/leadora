package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>This payload becomes a {@code customers} row, so it is held to the same rules as the lead it
 * came from. It previously was not: {@code phone} carried only a length cap while create and update
 * both required the Vietnamese pattern, which let a conversion write a number the lead itself would
 * have rejected. The lengths were looser than the columns too — {@code phone} allowed 20 into
 * {@code VARCHAR(15)}, {@code taxCode} 50 into {@code VARCHAR(25)}.
 */
@Getter
@Setter
public class ConvertLeadRequest {

    @NotNull(message = "Customer type is required")
    private CustomerType customerType;

    @NotBlank(message = "Full name is required")
    @Size(max = LeadFieldLimits.FULL_NAME, message = "Full name must be at most 40 characters")
    private String fullName;

    @Email(message = "Invalid email format")
    @Size(max = LeadFieldLimits.EMAIL, message = "Email must be at most 40 characters")
    private String email;

    @Pattern(
            regexp = LeadFieldLimits.PHONE_PATTERN,
            message = "Phone number must be a valid Vietnamese 10-digit number (e.g. 0912345678)"
    )
    private String phone;

    @Size(max = LeadFieldLimits.COMPANY_NAME, message = "Company name must be at most 40 characters")
    private String companyName;

    @Size(max = LeadFieldLimits.TAX_CODE, message = "Tax code must be at most 25 characters")
    private String taxCode;

    private String address;

    /**
     * BR-07: a Sales Manager's approval reason for converting a lead that is not
     * yet QUALIFIED (e.g. a walk-in with a confirmed booking). Required only for
     * that override path; ignored for a normal QUALIFIED conversion.
     */
    @Size(max = 500)
    private String reason;
}
