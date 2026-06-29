package com.novax.leadora.application.usecase.reminder;

import com.novax.leadora.api.dto.request.CreateReminderRequest;
import com.novax.leadora.api.dto.response.ReminderResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReminderUseCase {

    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public ReminderResponse execute(CreateReminderRequest request) {
        // E4: due date must be in the future
        if (!request.getRemindAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException("INVALID_DUE_DATE",
                    "Due date/time must be in the future", HttpStatus.BAD_REQUEST);
        }

        // Resolve creator from JWT — never trust client-supplied userId
        UserEntity createdBy = currentUserProvider.resolve(null);

        // Assigned user defaults to creator if not specified
        UserEntity assignedUser = request.getAssignedUserId() != null
                ? userRepository.findById(request.getAssignedUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssignedUserId()))
                : createdBy;

        ReminderPriority priority = ReminderPriority.MEDIUM;
        if (StringUtils.hasText(request.getPriority())) {
            try {
                priority = ReminderPriority.valueOf(request.getPriority().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        ReminderEntity reminder = ReminderEntity.builder()
                .user(assignedUser)
                .createdBy(createdBy)
                .title(request.getTitle())
                .description(request.getDescription())
                .remindAt(request.getRemindAt())
                .priority(priority)
                .status(ReminderStatus.PENDING)
                .relatedEntity(request.getRelatedEntity())
                .relatedId(request.getRelatedId())
                .build();

        ReminderEntity saved = reminderRepository.save(reminder);

        // Notify assigned user when different from creator (BR-34)
        if (!assignedUser.getUserId().equals(createdBy.getUserId())) {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(assignedUser)
                    .title("New Reminder Assigned")
                    .message(createdBy.getFullName() + " assigned you a reminder: \"" + saved.getTitle() + "\"")
                    .type("REMINDER")
                    .relatedEntity("REMINDER")
                    .relatedId(saved.getReminderId())
                    .build();
            notificationRepository.save(notification);
            log.info("Reminder notification sent to user={}", assignedUser.getUserId());
        }

        log.info("Reminder created: id={}, assignedUser={}, remindAt={}",
                saved.getReminderId(), assignedUser.getUserId(), saved.getRemindAt());
        return ReminderResponse.from(saved);
    }
}
