package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Generic audit trail shared across modules (Quotation, Reminder, SLA, ...) — BR-37.
 */
@Entity
@Table(name = "system_audit_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
