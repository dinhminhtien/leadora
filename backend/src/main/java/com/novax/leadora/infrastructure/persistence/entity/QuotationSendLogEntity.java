package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "quotation_send_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotationSendLogEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private QuotationEntity quotation;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "send_method", length = 20)
    private String sendMethod; // EMAIL | WHATSAPP | PDF

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 50)
    private String recipientPhone;

    @Column(name = "sent_by_name", length = 255)
    private String sentByName;

    @Column(name = "sent_by_role", length = 50)
    private String sentByRole;

    @Column(name = "personal_message", columnDefinition = "TEXT")
    private String personalMessage;
}
