package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.NotificationResponse;
import com.novax.leadora.application.usecase.notification.GetNotificationByIdUseCase;
import com.novax.leadora.application.usecase.notification.GetNotificationsUseCase;
import com.novax.leadora.application.usecase.notification.MarkAllReadUseCase;
import com.novax.leadora.application.usecase.notification.MarkNotificationReadUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
    private final GetNotificationByIdUseCase getNotificationByIdUseCase;
    private final MarkNotificationReadUseCase markNotificationReadUseCase;
    private final MarkAllReadUseCase markAllReadUseCase;
    private final CurrentUserProvider currentUserProvider;

    /** UC-15.1 — List notifications for the authenticated user */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly) {
        UUID userId = currentUserProvider.resolve(null).getUserId();
        List<NotificationResponse> list = getNotificationsUseCase.execute(userId, unreadOnly);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /** UC-15.2 — Access a notification (auto marks as read) */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(@PathVariable UUID id) {
        NotificationResponse response = getNotificationByIdUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** UC-15.1 — Toggle read/unread on a single notification */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean read) {
        NotificationResponse response = markNotificationReadUseCase.execute(id, read);
        return ResponseEntity.ok(ApiResponse.success(response, "Notification updated"));
    }

    /** UC-15.1 — Mark all notifications as read for the authenticated user */
    @PatchMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        UUID userId = currentUserProvider.resolve(null).getUserId();
        Map<String, Integer> result = markAllReadUseCase.execute(userId);
        return ResponseEntity.ok(ApiResponse.success(result, "All notifications marked as read"));
    }
}
