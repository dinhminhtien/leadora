package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable audit record written once OTP verification is completed (success
 * or failure) for a quotation acceptance. Captures the essential evidence
 * required: who, what, when, from where.
 */
@Entity
@Table(name = "quotation_otp_audit_logs", indexes = {
    @Index(name = "idx_qoal_quotation", columnList = "quotation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotationOtpAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "quotation_id", nullable = false)
    private UUID quotationId;

    /** The customer's registered email that received the OTP. */
    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    /** Remote IP of the HTTP request that submitted the OTP. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** Whether OTP verification ultimately succeeded. */
    @Column(name = "otp_verified", nullable = false)
    private boolean otpVerified;

    /** UTC timestamp of the confirmation attempt. */
    @Column(name = "confirmed_at", nullable = false)
    private OffsetDateTime confirmedAt;

    /** Quotation status before the OTP confirmation. */
    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    /** Quotation status after the OTP confirmation (ACCEPTED). */
    @Column(name = "new_status", length = 30)
    private String newStatus;
}
