package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reminders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reminder_id")
    private UUID reminderId;

    /** User who is assigned to act on this reminder */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /** User who created this reminder (may differ from assigned user) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private UserEntity createdBy;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "remind_at", nullable = false)
    private OffsetDateTime remindAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    @Builder.Default
    private ReminderPriority priority = ReminderPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReminderStatus status;

    @Column(name = "related_entity", length = 50)
    private String relatedEntity;

    @Column(name = "related_id")
    private UUID relatedId;
}
