package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per pipeline-stage transition of a deal — append-only, never updated or deleted.
 *
 * <p>{@code deals.pipeline_stage} answers "where is this deal now". It is overwritten in place, so
 * the moment a deal leaves NEGOTIATION the fact that it sat there for eighteen days is gone. This
 * table answers "where has it been, and how long did each leg take", which is what turns UC-23.4
 * from a snapshot of the funnel into a measure of flow through it.
 *
 * <p>Written inside the same transaction as the stage change itself. That is the difference from
 * {@code activity_log}, which records the same transitions but is appended after commit in a
 * separate transaction ({@code ActivityLogListener}, {@code AFTER_COMMIT} +
 * {@code REQUIRES_NEW}) and is swallowed on failure at several call sites — fine for an audit
 * trail, not sound enough to compute metrics from.
 *
 * <p>History before this table existed was backfilled from {@code activity_log} on a best-effort
 * basis; see {@code db/deal_stage_history.sql}. Rows carrying {@code backfilled = true} are
 * reconstructed and may be incomplete.
 */
@Entity
@Table(name = "deal_stage_history", indexes = {
    // The report walks a deal's transitions in order; the schedulers and detail screens look up a
    // single deal's history.
    @Index(name = "idx_deal_stage_history_deal_changed", columnList = "deal_id, changed_at"),
    @Index(name = "idx_deal_stage_history_changed_at", columnList = "changed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealStageHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    /** Stage being left. Null on the first row, i.e. the deal entering the pipeline. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 30)
    private DealPipelineStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false, length = 30)
    private DealPipelineStage toStage;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    /** Null when the move was made by a background sync rather than a person. */
    @Column(name = "changed_by")
    private UUID changedBy;

    /** Free-text origin of the move, e.g. {@code MANUAL}, {@code WORKFLOW_SYNC}, {@code AUTO_WIN}. */
    @Column(name = "source", length = 30)
    private String source;

    /** True for rows reconstructed from activity_log rather than recorded live. */
    @Column(name = "backfilled", nullable = false)
    private boolean backfilled;
}
