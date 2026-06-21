package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarkAllReadUseCase {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Map<String, Integer> execute(UUID userId) {
        List<NotificationEntity> unread =
                notificationRepository.findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        return Map.of("markedCount", unread.size());
    }
}
