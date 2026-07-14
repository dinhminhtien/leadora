package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConvertLeadRequest {

    @NotNull(message = "Customer type is required")
    private CustomerType customerType;

    @NotBlank(message = "Full name is required")
    @Size(max = 255)
    private String fullName;

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

    /**
     * BR-07: a Sales Manager's approval reason for converting a lead that is not
     * yet QUALIFIED (e.g. a walk-in with a confirmed booking). Required only for
     * that override path; ignored for a normal QUALIFIED conversion.
     */
    @Size(max = 500)
    private String reason;
}
