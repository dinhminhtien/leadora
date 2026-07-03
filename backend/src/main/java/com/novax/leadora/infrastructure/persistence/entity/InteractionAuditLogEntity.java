package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "interaction_audit_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteractionAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "interaction_id", nullable = false)
    private UUID interactionId;

    @Column(name = "action", nullable = false, length = 20)
    private String action; // CREATED, UPDATED

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_by_name", nullable = false)
    private String changedByName;

    @Column(name = "changed_by_role", length = 50)
    private String changedByRole;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
