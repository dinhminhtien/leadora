package com.novax.leadora.api.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealResponse {
    private UUID id;
    private String title;
    private String contactName;
    private String email;
    private String phone;
    private BigDecimal value;
    private int probability;
    private String stage;
    private String owner;
    private String status;
    private LocalDate expectedClose;
    private LocalDate createdAt;
    private String notes;
}
