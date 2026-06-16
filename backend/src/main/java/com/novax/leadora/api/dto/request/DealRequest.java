package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealRequest {

    @NotBlank(message = "Deal name is required")
    private String title;

    @NotBlank(message = "Contact name is required")
    private String contactName;

    private String email;
    private String phone;

    private BigDecimal value;

    private String stage;

    private String status;

    private LocalDate expectedClose;

    private String notes;

    private String owner;
}
