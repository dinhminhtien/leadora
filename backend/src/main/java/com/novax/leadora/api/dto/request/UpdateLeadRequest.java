package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateLeadRequest {

    @Size(max = 255)
    private String fullName;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @Pattern(
            regexp = "^(0[35789])\\d{8}$",
            message = "Phone number must be a valid Vietnamese 10-digit number (e.g. 0912345678)"
    )
    private String phone;

    @Size(max = 255)
    private String companyName;

    private String address;

    /** false = individual, true = corporate / organization. */
    private Boolean isCorporate;

    @Size(max = 100)
    private String source;

    /** BR-05: hotel service/product the lead is interested in. */
    @Size(max = 100)
    private String interestedService;

    private LeadStatus status;

    private String notes;

    private UUID assignedUserId;
}
