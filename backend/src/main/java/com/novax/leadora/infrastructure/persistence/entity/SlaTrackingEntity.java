package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sla_tracking", indexes = {
    // UC-23.3 filters and orders by started_at; the schedulers probe status + a deadline column.
    @Index(name = "idx_sla_tracking_started_at", columnList = "started_at"),
    @Index(name = "idx_sla_tracking_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaTrackingEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tracking_id")
    private UUID trackingId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "deadline_at", nullable = false)
    private OffsetDateTime deadlineAt;

    @Column(name = "warning_at", nullable = false)
    private OffsetDateTime warningAt;

    @Column(name = "escalation_at", nullable = false)
    private OffsetDateTime escalationAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SlaStatus status;

    @Column(name = "warning_notified", nullable = false)
    @Builder.Default
    private boolean warningNotified = false;

    @Column(name = "escalation_notified", nullable = false)
    @Builder.Default
    private boolean escalationNotified = false;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}

