package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "sla_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaRuleEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "deadline_hours", nullable = false)
    private int deadlineHours;

    @Column(name = "warning_threshold", nullable = false)
    private int warningThreshold;

    @Column(name = "escalation_threshold", nullable = false)
    private int escalationThreshold;

    @Column(name = "active", nullable = false)
    private boolean active;
}
