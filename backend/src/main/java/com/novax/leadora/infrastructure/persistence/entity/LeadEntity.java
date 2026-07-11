package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "leads", indexes = {
    @Index(name = "idx_leads_created_at", columnList = "created_at"),
    @Index(name = "idx_leads_assigned_user_id", columnList = "assigned_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "lead_id")
    private UUID leadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private UserEntity assignedUser;

    @Column(name = "full_name", nullable = false, length = 40)
    private String fullName;

    @Column(name = "email", length = 40)
    private String email;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "company_name", length = 40)
    private String companyName;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    /** false = individual customer, true = corporate / organization. */
    @Builder.Default
    @Column(name = "is_corporate", nullable = false, columnDefinition = "boolean default false")
    private Boolean isCorporate = false;

    @Column(name = "source", length = 40)
    private String source;

    /** BR-05: the hotel service/product the lead is interested in (free text — no
     *  fixed service catalog exists yet). Required before a lead enters active
     *  follow-up; nullable so an unassigned NEW draft can be stored without it. */
    @Column(name = "interested_service", length = 100)
    private String interestedService;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LeadStatus status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "converted_at")
    private OffsetDateTime convertedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;
}
