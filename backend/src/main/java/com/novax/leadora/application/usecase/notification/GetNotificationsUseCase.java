package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.api.dto.response.NotificationResponse;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetNotificationsUseCase {

    private final NotificationRepository notificationRepository;

    /**
     * UC-15.1 — paginated, filterable notification list.
     *
     * @param userId whose notifications to list, or {@code null} for the Manager/Admin
     *               aggregate feed across every user (see {@code NotificationController}
     *               for the role check that gates {@code null}).
     * @param sortBy "priority" (URGENT→LOW, ties newest-first) or default newest-first.
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> execute(UUID userId, Boolean unreadOnly, String type, String priority,
                                                OffsetDateTime createdFrom, OffsetDateTime createdTo,
                                                String sortBy, Pageable pageable) {
        Specification<NotificationEntity> spec = Specification
                .where(NotificationSpecifications.userId(userId))
                .and(NotificationSpecifications.unreadOnly(Boolean.TRUE.equals(unreadOnly)))
                .and(NotificationSpecifications.type(type))
                .and(NotificationSpecifications.priority(priority))
                .and(NotificationSpecifications.createdFrom(createdFrom))
                .and(NotificationSpecifications.createdTo(createdTo));

        Pageable effectivePageable;
        if ("priority".equalsIgnoreCase(sortBy)) {
            spec = spec.and(NotificationSpecifications.orderByPrioritySeverityThenCreatedAtDesc());
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        } else {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        return notificationRepository.findAll(spec, effectivePageable).map(NotificationResponse::from);
    }
}
