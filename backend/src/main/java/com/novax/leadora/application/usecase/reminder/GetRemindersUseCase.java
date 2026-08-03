package com.novax.leadora.application.usecase.reminder;

import com.novax.leadora.api.dto.response.ReminderResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReminderStatus;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import com.novax.leadora.infrastructure.persistence.specification.ReminderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
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

        ReminderStatus status = null;
        boolean excludeCancelled = false;

        if (StringUtils.hasText(statusFilter)) {
            try {
                status = ReminderStatus.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_STATUS",
                        "Invalid status filter: " + statusFilter, HttpStatus.BAD_REQUEST);
            }
        } else {
            excludeCancelled = true;
        }

        Specification<ReminderEntity> spec = ReminderSpecification.filter(
                userId, status, excludeCancelled, remindFrom, remindTo, sortBy
        );

        List<ReminderEntity> records = reminderRepository.findAll(spec);

        return records.stream().map(ReminderResponse::from).toList();
    }
}
