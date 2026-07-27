package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * <p><b>Every {@code @Size} here mirrors its database column exactly.</b> They used to be 255 while
 * {@code leads.full_name} and friends are {@code VARCHAR(40)}, so anything between 41 and 255
 * characters passed validation cleanly, reached Postgres, and came back as an HTTP 500 with no clue
 * which field was at fault. A limit that is looser than the column it guards is not a limit — it
 * just moves the rejection to the layer least able to explain it. See {@link LeadFieldLimits}.
 */
@Getter
@Setter
public class CreateLeadRequest {

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

    private String address;

    /** false = individual, true = corporate / organization. Defaults to individual when omitted. */
    private Boolean isCorporate;

    @Size(max = LeadFieldLimits.SOURCE, message = "Source must be at most 40 characters")
    private String source;

    /** BR-05: hotel service/product the lead is interested in. Required (server-side)
     *  before the lead can enter active follow-up; optional at creation. */
    @Size(max = LeadFieldLimits.INTERESTED_SERVICE,
            message = "Interested service must be at most 100 characters")
    private String interestedService;

    private String notes;

    private UUID assignedUserId;
}
