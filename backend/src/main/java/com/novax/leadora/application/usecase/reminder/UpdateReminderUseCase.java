package com.novax.leadora.application.usecase.reminder;

import com.novax.leadora.api.dto.request.UpdateReminderRequest;
import com.novax.leadora.api.dto.response.ReminderResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateReminderUseCase {

    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public ReminderResponse execute(UUID reminderId, UpdateReminderRequest request) {
        ReminderEntity reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", reminderId));

        // Resolve caller from JWT; reload with role to check MANAGER status
        UUID callerId = currentUserProvider.resolve(null).getUserId();
        UserEntity updater = userRepository.findWithRoleByUserId(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", callerId));

        boolean isAssignee = reminder.getUser() != null &&
                reminder.getUser().getUserId().equals(updater.getUserId());
        boolean isManager = updater.getRole() != null &&
                "MANAGER".equals(updater.getRole().getRoleName());

        if (!isAssignee && !isManager) {
            throw new BusinessException("UNAUTHORIZED_UPDATE", "Access Denied", HttpStatus.FORBIDDEN);
        }

        if (reminder.getStatus() == ReminderStatus.DONE && !request.isForceIfDone()) {
            throw new BusinessException(
                    "REMINDER_ALREADY_DONE",
                    "Reminder already completed. Set forceIfDone=true to override.",
                    HttpStatus.CONFLICT
            );
        }

        if (request.getRemindAt() != null && !request.getRemindAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException("INVALID_DEADLINE", "Due date must be in the future", HttpStatus.BAD_REQUEST);
        }

        String oldValue = "status=" + reminder.getStatus() + ", remindAt=" + reminder.getRemindAt();

        if (request.getTitle() != null) {
            reminder.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            reminder.setDescription(request.getDescription());
        }
        if (request.getRemindAt() != null) {
            // Extending deadline resets an overdue reminder back to pending
            if (reminder.getStatus() == ReminderStatus.OVERDUE) {
                reminder.setStatus(ReminderStatus.PENDING);
            }
            reminder.setRemindAt(request.getRemindAt());
        }
        if (request.getPriority() != null) {
            try {
                reminder.setPriority(ReminderPriority.valueOf(request.getPriority().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_PRIORITY",
                        "Invalid priority value: " + request.getPriority(), HttpStatus.BAD_REQUEST);
            }
        }
        if (Boolean.TRUE.equals(request.getMarkAsDone())) {
            reminder.setStatus(ReminderStatus.DONE);
        }

        ReminderEntity saved = reminderRepository.save(reminder);

        String newValue = "status=" + saved.getStatus() + ", remindAt=" + saved.getRemindAt();
        systemAuditLogService.log("REMINDER", "REMINDER", reminderId, "UPDATED", updater, oldValue, newValue, null);

        log.info("Reminder updated: id={} by userId={}", reminderId, callerId);
        return ReminderResponse.from(saved);
    }
}
