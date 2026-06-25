package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.OffsetDateTime;

@Entity
@Table(name = "permissions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Integer permissionId;

    @Column(name = "permission_code", nullable = false, unique = true, length = 100)
    private String permissionCode;

    @Column(name = "description", length = 100)
    private String description;

    /** Functional area the permission gates (LEAD, DEAL, …) — used to group + map to a screen. */
    @Column(name = "module", length = 50)
    private String module;

    /** VIEW | WRITE | APPROVE. WRITE/APPROVE require the module's VIEW (see {@link #dependsOnId}). */
    @Column(name = "action", length = 20)
    private String action;

    /** Human-readable label shown in the role configuration UI. */
    @Column(name = "label", length = 120)
    private String label;

    /** Prerequisite permission id (e.g. LEAD_WRITE depends on LEAD_VIEW). Null for VIEW. */
    @Column(name = "depends_on_id")
    private Integer dependsOnId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
