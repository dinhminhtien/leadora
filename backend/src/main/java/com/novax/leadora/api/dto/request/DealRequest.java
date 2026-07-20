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

    @NotBlank(message = "Deal name is required")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "Contact name is required")
    @Size(max = 255)
    private String contactName;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @Pattern(
            regexp = "^(0[35789])\\d{8}$",
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
