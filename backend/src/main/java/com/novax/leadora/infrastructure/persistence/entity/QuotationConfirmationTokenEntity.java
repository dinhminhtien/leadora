package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stores the one-time secure link token issued when a quotation is sent to
 * the customer via EMAIL.  The token is hashed (SHA-256) before persisting so
 * that even a DB dump cannot be used to forge a customer portal link.
 *
 * One active token per quotation — older tokens are deleted before a new one
 * is saved (see {@code SendQuotationUseCase}).
 */
@Entity
@Table(name = "quotation_confirmation_tokens", indexes = {
    @Index(name = "idx_qct_quotation", columnList = "quotation_id"),
    @Index(name = "idx_qct_hash",      columnList = "token_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotationConfirmationTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "quotation_id", nullable = false)
    private UUID quotationId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
