package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "quotation_approval_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotationApprovalHistoryEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private QuotationEntity quotation;

    @Column(name = "action", nullable = false, length = 50)
    private String action; // APPROVED | REJECTED | REVISION_REQUESTED

    @Column(name = "decided_by_name", nullable = false, length = 255)
    private String decidedByName;

    @Column(name = "decided_by_role", length = 50)
    private String decidedByRole;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;
}
