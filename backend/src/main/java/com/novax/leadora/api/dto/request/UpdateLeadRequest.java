package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * <p>Limits come from {@link LeadFieldLimits} so this DTO cannot drift away from the create form or
 * from the columns behind it. A blank {@code phone} is accepted here and means "erase it" — see
 * {@link LeadFieldLimits#PHONE_PATTERN} for why that branch has to exist.
 */
@Getter
@Setter
public class UpdateLeadRequest {

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

    private String address;

    /** false = individual, true = corporate / organization. */
    private Boolean isCorporate;

    @Size(max = LeadFieldLimits.SOURCE, message = "Source must be at most 40 characters")
    private String source;

    /** BR-05: hotel service/product the lead is interested in. */
    @Size(max = LeadFieldLimits.INTERESTED_SERVICE,
            message = "Interested service must be at most 100 characters")
    private String interestedService;

    private LeadStatus status;

    private String notes;

    private UUID assignedUserId;
}
