package com.novax.leadora.application.usecase.reminder;

import com.novax.leadora.api.dto.response.ReminderResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetRemindersUseCase {

    private static final Set<String> SCOPED_ROLES = Set.of("SALES", "SALES_STAFF");

    private final ReminderRepository reminderRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<ReminderResponse> execute(UUID userId, String statusFilter,
                                          OffsetDateTime remindFrom, OffsetDateTime remindTo,
                                          String sortBy) {
        // A Sales Staff may only ever see their own reminders — override whatever
        // userId the client sent (or omitted) rather than trusting it. Manager/Admin
        // (and any other role) keep the caller-supplied filter, including "all staff"
        // when userId is omitted.
        UserEntity currentUser = currentUserProvider.resolve(null);
        String role = currentUser.getRole() != null && currentUser.getRole().getRoleName() != null
                ? currentUser.getRole().getRoleName().trim().toUpperCase() : "";
        if (SCOPED_ROLES.contains(role)) {
            userId = currentUser.getUserId();
        }

        List<ReminderEntity> records;

        if (StringUtils.hasText(statusFilter)) {
            ReminderStatus status;
            try {
                status = ReminderStatus.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_STATUS",
                        "Invalid status filter: " + statusFilter, HttpStatus.BAD_REQUEST);
            }

            records = userId != null
                    ? reminderRepository.findByUser_UserIdAndStatusOrderByRemindAtAsc(userId, status)
                    : reminderRepository.findByStatusOrderByRemindAtAsc(status);
        } else if (userId != null) {
            // Exclude CANCELLED by default for per-user queries
            records = reminderRepository.findByUser_UserIdAndStatusNotOrderByRemindAtAsc(
                    userId, ReminderStatus.CANCELLED);
        } else {
            // Manager "all staff" view — exclude CANCELLED by default
            records = reminderRepository.findByStatusNotOrderByRemindAtAsc(ReminderStatus.CANCELLED);
        }

        // UC-16.2: optional date-range filter
        if (remindFrom != null) {
            records = records.stream()
                    .filter(r -> !r.getRemindAt().isBefore(remindFrom))
                    .toList();
        }
        if (remindTo != null) {
            records = records.stream()
                    .filter(r -> !r.getRemindAt().isAfter(remindTo))
                    .toList();
        }

        // UC-16.2: optional sort by priority (HIGH > MEDIUM > LOW), secondary sort by remindAt
        if ("priority".equalsIgnoreCase(sortBy)) {
            records = records.stream()
                    .sorted(Comparator
                            .comparingInt((ReminderEntity r) -> priorityOrder(r.getPriority()))
                            .thenComparing(r -> r.getRemindAt()))
                    .toList();
        }

        return records.stream().map(ReminderResponse::from).toList();
    }

    private static int priorityOrder(ReminderPriority priority) {
        if (priority == null) return 3;
        return switch (priority) {
            case HIGH   -> 0;
            case MEDIUM -> 1;
            case LOW    -> 2;
        };
    }
}
