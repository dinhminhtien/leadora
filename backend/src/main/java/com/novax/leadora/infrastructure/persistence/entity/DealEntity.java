package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "deals", indexes = {
    @Index(name = "idx_deals_created_at", columnList = "created_at"),
    @Index(name = "idx_deals_assigned_user_id", columnList = "assigned_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "deal_id")
    private UUID dealId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private UserEntity assignedUser;

    @Column(name = "deal_name", nullable = false, length = 50)
    private String dealName;

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_stage", nullable = false, length = 30)
    private DealPipelineStage pipelineStage;

    @Column(name = "expected_revenue", precision = 15, scale = 2)
    private BigDecimal expectedRevenue;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private DealStatus status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    public void setPipelineStage(DealPipelineStage pipelineStage) {
        this.pipelineStage = pipelineStage;
        if (pipelineStage == DealPipelineStage.CLOSED_WON) {
            this.status = DealStatus.WON;
        } else if (pipelineStage == DealPipelineStage.CLOSED_LOST) {
            this.status = DealStatus.LOST;
        } else {
            this.status = DealStatus.OPEN;
        }
    }

    public void setStatus(DealStatus status) {
        this.status = status;
        if (status == DealStatus.WON) {
            this.pipelineStage = DealPipelineStage.CLOSED_WON;
        } else if (status == DealStatus.LOST) {
            this.pipelineStage = DealPipelineStage.CLOSED_LOST;
        }
    }

    @PrePersist
    @PreUpdate
    public void syncStatusWithPipelineStage() {
        if (this.pipelineStage == DealPipelineStage.CLOSED_WON) {
            this.status = DealStatus.WON;
        } else if (this.pipelineStage == DealPipelineStage.CLOSED_LOST) {
            this.status = DealStatus.LOST;
        } else {
            this.status = DealStatus.OPEN;
        }
    }
}
