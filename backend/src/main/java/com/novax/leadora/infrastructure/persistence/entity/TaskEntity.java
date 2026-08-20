package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_tasks_created_at", columnList = "created_at"),
    @Index(name = "idx_tasks_assigned_user_id", columnList = "assigned_user_id"),
    @Index(name = "idx_tasks_start_at", columnList = "start_at"),
    @Index(name = "idx_tasks_end_at", columnList = "end_at"),
    @Index(name = "idx_tasks_assigned_status_end", columnList = "assigned_user_id, status, end_at"),
    @Index(name = "idx_tasks_status_end", columnList = "status, end_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "task_id")
    private UUID taskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id", nullable = false)
    private UserEntity assignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * What kind of work this is (Call / Meeting / …). Was parsed out of {@link #title}
     * until the activity-type migration; it is real data now.
     *
     * <p>Deliberately mapped as nullable even though the migrated column is NOT NULL:
     * a row written before the backfill can still hold NULL, and loading one must not
     * blow up. Writers always set a value, and {@code TaskResponse} never emits null —
     * see {@link ActivityType#orDefault}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", length = 30)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "result_note", columnDefinition = "TEXT")
    private String resultNote;

    @Column(name = "start_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime startAt;

    @Column(name = "end_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime endAt;

    @Column(name = "primary_contact_name", length = 255)
    private String primaryContactName;

    @Column(name = "primary_contact_phone", length = 50)
    private String primaryContactPhone;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private LeadEntity lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private DealEntity deal;

    @Builder.Default
    @Column(name = "overdue_notified", nullable = false)
    private Boolean overdueNotified = false;

    /**
     * When the task was actually finished — the difference between "still running late" and
     * "finished late".
     *
     * <p>Without it, a task completed three days after its deadline was not counted as late at all:
     * it had left the OPEN status, and overdue was only ever measured against tasks still open. A
     * period in which every task was finished late scored 0% overdue, and the figure improved as the
     * team cleared its backlog rather than as it became punctual.
     *
     * <p>Nullable, and it stays null for tasks completed before this column existed. UC-23.2 reports
     * how many completed tasks it cannot place rather than guessing from {@code updated_at}, which
     * moves whenever anyone edits the row afterwards.
     */
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /**
     * Kept in step with the status by the entity itself, not by whichever use case happened to
     * change it.
     *
     * <p>Stamping it at the call site looked sufficient because one service completes tasks
     * explicitly — but {@code UpdateTaskUseCase} also completes them, by resolving a status name out
     * of the request, so the literal never appears in its code and the second path silently produced
     * completed tasks with no completion time. Every one of those would have dropped out of the
     * punctuality figures this column exists to produce. An invariant that two call sites have to
     * remember is an invariant that will be forgotten; this one is maintained here instead.
     */
    public void setStatus(TaskStatus status) {
        this.status = status;
        stampCompletedAt();
    }

    @PrePersist
    @PreUpdate
    void stampCompletedAtOnSave() {
        stampCompletedAt();
    }

    /**
     * Sets the completion timestamp on the way into COMPLETED and clears it on the way back out.
     *
     * <p>Only stamps when empty, so re-saving an already completed task does not move a historical
     * completion date into the current month and reshape a period that has already been reported on.
     * Reopening genuinely un-completes the task: a stale timestamp would keep it counted as work
     * finished in the month it was previously closed in, while it also shows up as open and overdue.
     */
    private void stampCompletedAt() {
        if (this.status == TaskStatus.COMPLETED) {
            if (this.completedAt == null) {
                this.completedAt = OffsetDateTime.now();
            }
        } else {
            this.completedAt = null;
        }
    }
}
