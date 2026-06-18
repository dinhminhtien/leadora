package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SaveReportLogRequest {

    @NotBlank(message = "generatedByName is required")
    private String generatedByName;

    private String generatedByRole;

    private LocalDate filterDateFrom;

    private LocalDate filterDateTo;

    private String filterRoomType;

    @NotNull(message = "discountThreshold is required")
    private BigDecimal filterDiscountThreshold;

    @NotNull(message = "resultCount is required")
    @Min(value = 0)
    private Integer resultCount;

    // BR-37: action, result, reason for complete audit trail
    private String action;   // e.g. "GENERATE_DISCOUNT_REPORT"
    private String result;   // e.g. "SUCCESS" | "NO_DATA"
    private String reason;
}
