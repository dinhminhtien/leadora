package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
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

    /**
     * Human-readable funnel label ("Inquiry", "Site Visit", … "Confirmed").
     * Lossy — CLOSED_WON and CLOSED_LOST both render as "Confirmed" — so it
     * cannot identify a stage. Retained because the web deal list binds to these
     * exact strings. Use {@link #stageCode} for anything logic-bearing.
     */
    private String stage;

    /** Authoritative pipeline stage; serialized as the enum wire value. */
    private DealPipelineStage stageCode;

    private String owner;
    private String status;
    private LocalDate expectedClose;
    private LocalDate createdAt;
    private String notes;
}
