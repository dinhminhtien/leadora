package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * UC-14.x — Customer Acceptance Portal audit record.
 *
 * Written once per customer decision on a quotation sent via secure link:
 * <ul>
 *   <li>ACCEPTED — customer completed OTP verification; quotation moves to
 *       {@code RESERVATION_PENDING}.</li>
 *   <li>REJECTED — customer declined via portal; quotation moves to
 *       {@code REJECTED}; Sales is notified to follow up.</li>
 * </ul>
 *
 * For the REJECTED case, {@code customerNote} satisfies the UC-14.6
 * lost-reason requirement for the portal path.  UC-14.6 (Sales-logged
 * verbal/offline response) writes to {@code quotation_customer_responses}
 * and coexists with this table — the portal record is the authoritative
 * digital record; UC-14.6 covers verbal/offline responses.
 *
 * <p><strong>RLS note</strong>: this table is written by
 * {@code CustomerAcceptQuotationUseCase} and {@code CustomerRejectQuotationUseCase},
 * both called from anonymous public endpoints.  The JDBC connection runs as the
 * {@code postgres} superuser and bypasses Supabase RLS by design.
 * See {@code customer_acceptance_portal.sql} for the full architecture decision.</p>
 */
@Entity
@Table(name = "quotation_acceptance_logs", indexes = {
    @Index(name = "idx_qal_quotation_id", columnList = "quotation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotationAcceptanceLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    /** The quotation this log entry belongs to. */
    @Column(name = "quotation_id", nullable = false)
    private UUID quotationId;

    /**
     * The customer's decision: {@code "ACCEPTED"} or {@code "REJECTED"}.
     * Mirrors the CHECK constraint in the migration.
     */
    @Column(name = "action", nullable = false, length = 10)
    private String action;

    /** UTC timestamp of the customer action. */
    @Column(name = "accepted_at", nullable = false)
    @Builder.Default
    private OffsetDateTime acceptedAt = OffsetDateTime.now();

    /** Remote IP of the HTTP request (IPv4 or IPv6, max 45 chars). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** {@code User-Agent} header of the HTTP request. */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Free-text note supplied by the customer.
     * For REJECTED actions this carries the rejection reason and satisfies
     * the UC-14.6 lost-reason requirement for the portal path.
     */
    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;
}
