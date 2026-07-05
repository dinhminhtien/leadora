package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.NotificationResponse;
import com.novax.leadora.application.usecase.notification.GetNotificationByIdUseCase;
import com.novax.leadora.application.usecase.notification.GetNotificationsUseCase;
import com.novax.leadora.application.usecase.notification.GetUnreadNotificationCountUseCase;
import com.novax.leadora.application.usecase.notification.MarkAllReadUseCase;
import com.novax.leadora.application.usecase.notification.MarkNotificationReadUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
    private final GetNotificationByIdUseCase getNotificationByIdUseCase;
    private final MarkNotificationReadUseCase markNotificationReadUseCase;
    private final MarkAllReadUseCase markAllReadUseCase;
    private final GetUnreadNotificationCountUseCase getUnreadNotificationCountUseCase;
    private final CurrentUserProvider currentUserProvider;

    /** UC-15.1 — Paginated notification list for the authenticated user */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = currentUserProvider.resolve(null).getUserId();
        Page<NotificationResponse> result =
                getNotificationsUseCase.execute(userId, unreadOnly, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** UC-15.1 — Lightweight unread count for the bell badge */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount() {
        UUID userId = currentUserProvider.resolve(null).getUserId();
        long count = getUnreadNotificationCountUseCase.execute(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
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
