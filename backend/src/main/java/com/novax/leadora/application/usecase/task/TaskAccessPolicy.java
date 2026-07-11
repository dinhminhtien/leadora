package com.novax.leadora.application.usecase.task;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Visibility scoping for follow-up tasks (BR-02).
 *
 * <p>MANAGER / ADMIN see every task; SALES (Sales Staff) are restricted to the
 * tasks assigned to them. Ownership is the assignee — a Sales Staff owns a task
 * only when they are its {@code assignedUser}. Mirrors
 * {@code InteractionTimelineAccessPolicy}.
 */
@Component
public class TaskAccessPolicy extends BaseAccessPolicy<TaskEntity> {

    public TaskAccessPolicy(CurrentUserProvider currentUserProvider) {
        super(currentUserProvider);
    }

    @Override
    protected boolean owns(UserEntity user, TaskEntity task) {
        UUID uid = user.getUserId();
        return task.getAssignedUser() != null && uid.equals(task.getAssignedUser().getUserId());
    }
}
