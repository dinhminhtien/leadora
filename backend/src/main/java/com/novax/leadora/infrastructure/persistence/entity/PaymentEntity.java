package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_status", columnList = "status"),
    @Index(name = "idx_payments_paid_at", columnList = "paid_at"),
    @Index(name = "idx_payments_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id")
    private UUID paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "gateway_provider", length = 100)
    private String gatewayProvider;

    @Column(name = "gateway_transaction_id", length = 255)
    private String gatewayTransactionId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 30)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String verificationNote;

    public String getNotes() {
        return verificationNote;
    }

    public void setNotes(String notes) {
        this.verificationNote = notes;
    }
}
