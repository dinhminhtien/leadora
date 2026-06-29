package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "customer_id")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    @Column(name = "full_name", nullable = false, length = 40)
    private String fullName;

    @Column(name = "email", length = 40)
    private String email;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "company_name", length = 40)
    private String companyName;

    @Column(name = "tax_code", length = 25)
    private String taxCode;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private UserEntity assignedUser;

    @Column(name = "lead_id")
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;
}
