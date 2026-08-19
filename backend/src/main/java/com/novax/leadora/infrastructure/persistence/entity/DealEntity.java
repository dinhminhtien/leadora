package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "deals", indexes = {
    @Index(name = "idx_deals_created_at", columnList = "created_at"),
    @Index(name = "idx_deals_closed_at", columnList = "closed_at"),
    @Index(name = "idx_deals_assigned_user_id", columnList = "assigned_user_id"),
    @Index(name = "idx_deals_assigned_status_created", columnList = "assigned_user_id, status, created_at"),
    @Index(name = "idx_deals_customer_id", columnList = "customer_id"),
    @Index(name = "idx_deals_pipeline_stage_status", columnList = "pipeline_stage, status")
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

    /**
     * When the deal reached CLOSED_WON or CLOSED_LOST; null while it is still open.
     *
     * <p>Reporting needs this because outcome metrics belong to the period a deal <em>closed</em>,
     * not the period it was created. Without it, "win rate for July" silently meant "win rate of
     * deals opened in July" — which drops a deal opened in May and won in July, and drags the rate
     * down with July's deals that are still in flight.
     *
     * <p>Maintained by the mutators below rather than by callers, so there is exactly one place it
     * can go wrong and no use case has to remember it.
     */
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    public void setPipelineStage(DealPipelineStage pipelineStage) {
        this.pipelineStage = pipelineStage;
        if (pipelineStage == DealPipelineStage.CLOSED_WON) {
            this.status = DealStatus.WON;
        } else if (pipelineStage == DealPipelineStage.CLOSED_LOST) {
            this.status = DealStatus.LOST;
        } else {
            this.status = DealStatus.OPEN;
        }
        stampClosedAt();
    }

    public void setStatus(DealStatus status) {
        this.status = status;
        if (status == DealStatus.WON) {
            this.pipelineStage = DealPipelineStage.CLOSED_WON;
        } else if (status == DealStatus.LOST) {
            this.pipelineStage = DealPipelineStage.CLOSED_LOST;
        }
        stampClosedAt();
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
        stampClosedAt();
    }

    /**
     * Sets the close timestamp on the way into a terminal stage and clears it on the way back out.
     *
     * <p>Only stamps when the field is empty, so a correction that re-saves a closed deal (BR-44)
     * does not silently move a historical close date into the current month and reshape a period
     * that has already been reported on.
     */
    private void stampClosedAt() {
        boolean closed = this.status == DealStatus.WON || this.status == DealStatus.LOST;
        if (closed) {
            if (this.closedAt == null) {
                this.closedAt = OffsetDateTime.now();
            }
        } else {
            // Reopening genuinely un-closes the deal: leaving a stale timestamp would keep counting
            // it as an outcome of the month it was previously closed in.
            this.closedAt = null;
        }
    }
}
