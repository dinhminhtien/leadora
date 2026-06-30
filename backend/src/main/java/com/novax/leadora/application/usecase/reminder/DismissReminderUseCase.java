package com.novax.leadora.application.usecase.reminder;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DismissReminderUseCase {

    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public void execute(UUID reminderId) {
        ReminderEntity reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", reminderId));

        if (reminder.getStatus() == ReminderStatus.DONE) {
            throw new BusinessException("REMINDER_ALREADY_DONE", "Reminder already completed", HttpStatus.CONFLICT);
        }

        // Access control: only assignee or MANAGER can dismiss
        UUID callerId = currentUserProvider.resolve(null).getUserId();
        UserEntity caller = userRepository.findWithRoleByUserId(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", callerId));

        boolean isAssignee = reminder.getUser() != null &&
                reminder.getUser().getUserId().equals(caller.getUserId());
        boolean isManager = caller.getRole() != null &&
                "MANAGER".equals(caller.getRole().getRoleName());

        if (!isAssignee && !isManager) {
            throw new BusinessException("UNAUTHORIZED_DISMISS", "Access Denied", HttpStatus.FORBIDDEN);
        }

        reminder.setStatus(ReminderStatus.DONE);
        reminderRepository.save(reminder);
        log.info("Reminder dismissed: id={} by userId={}", reminderId, callerId);
    }
}
