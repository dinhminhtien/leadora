package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealRequest {

    private UUID customerId;

    /**
     * Maps to {@code deals.deal_name}, which is {@code VARCHAR(50)} — not 255.
     *
     * <p>The looser limit here was the same class of bug as the lead DTOs before
     * {@code LeadFieldLimits}: everything between 51 and 255 characters passed validation and then
     * failed at the database, surfacing as an unexplained 500 rather than a message naming the
     * field. The project runs {@code ddl-auto: validate} with no migration framework, so the column
     * is the fixed side of this contract.
     */
    @NotBlank(message = "Deal name is required")
    @Size(max = 50, message = "Deal title must be at most 50 characters")
    private String title;

    /**
     * Form-only: there is no contact column on {@code deals} — the contact details are read from
     * the linked customer. Kept because the create form collects and displays them.
     */
    @NotBlank(message = "Contact name is required")
    @Size(max = 255)
    private String contactName;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    /**
     * Same rule as the lead DTOs, shared rather than re-typed — and crucially the shared pattern
     * has an empty branch. This copy did not: it demanded a 10-digit number of a field that is
     * optional, so creating a deal for a customer with no phone on file was refused with a
     * validation error about a field the user never filled in. That is the exact path the
     * post-conversion "Create Deal" panel takes, since it forwards whatever the lead had.
     */
    @Pattern(
            regexp = LeadFieldLimits.PHONE_PATTERN,
            message = "Phone number must be a valid Vietnamese 10-digit number (e.g. 0912345678)"
    )
    private String phone;

    @DecimalMin(value = "0", message = "Deal value cannot be negative")
    private BigDecimal value;

    @NotBlank(message = "Stage is required")
    private String stage;

    private String status;

    @NotNull(message = "Expected close date is required")
    private LocalDate expectedClose;

    private String notes;

    private String owner;
}
