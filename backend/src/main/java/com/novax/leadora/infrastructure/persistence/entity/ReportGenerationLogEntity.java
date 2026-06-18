package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "report_generation_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportGenerationLogEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "generated_by_name", nullable = false, length = 255)
    private String generatedByName;

    @Column(name = "generated_by_role", length = 50)
    private String generatedByRole;

    @Column(name = "filter_date_from")
    private LocalDate filterDateFrom;

    @Column(name = "filter_date_to")
    private LocalDate filterDateTo;

    @Column(name = "filter_room_type", length = 255)
    private String filterRoomType;

    @Column(name = "filter_discount_threshold", precision = 5, scale = 2)
    private BigDecimal filterDiscountThreshold;

    @Column(name = "result_count", nullable = false)
    private Integer resultCount;

    // BR-37: action, result, reason required for complete audit trail
    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "result", length = 50)
    private String result;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
}
