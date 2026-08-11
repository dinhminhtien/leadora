package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.BillingMethod;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.entity.enums.PdfStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contracts", indexes = {
    @Index(name = "idx_contracts_quotation", columnList = "quotation_id"),
    @Index(name = "idx_contracts_deal", columnList = "deal_id"),
    @Index(name = "idx_contracts_customer", columnList = "customer_id"),
    @Index(name = "idx_contracts_status", columnList = "status"),
    @Index(name = "idx_contracts_created_at", columnList = "created_at"),
    @Index(name = "idx_contracts_parent", columnList = "parent_contract_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "contract_code", unique = true, nullable = false, length = 20)
    private String contractCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id", nullable = false)
    private DealEntity deal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private QuotationEntity quotation;

    @Column(name = "quotation_version", nullable = false)
    private Integer quotationVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private ContactEntity contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type_snapshot", nullable = false, length = 20)
    private CustomerType customerTypeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_method", nullable = false, length = 30)
    @Builder.Default
    private BillingMethod billingMethod = BillingMethod.INDIVIDUAL_GUEST_PAYS;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "parent_contract_id")
    private UUID parentContractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ContractStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "commercial_snapshot", nullable = false, columnDefinition = "jsonb")
    private String commercialSnapshot;

    @Column(name = "total_contract_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalContractValue;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "effective_date")
    private OffsetDateTime effectiveDate;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "sent_to", columnDefinition = "TEXT")
    private String sentTo;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "cancellation_days_before_arrival")
    private Integer cancellationDaysBeforeArrival;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "pdf_status", nullable = false, length = 20)
    @Builder.Default
    private PdfStatus pdfStatus = PdfStatus.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @Column(name = "created_by_source", nullable = false, length = 20)
    @Builder.Default
    private String createdBySource = "USER";
}
