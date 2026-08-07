import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../../core/network/pagination_response.dart';
import 'notification_models.dart';

/// Notification API calls. The backend returns a Spring `Page<NotificationResponse>`.
class NotificationRepository {
  NotificationRepository(this._client);

  final ApiClient _client;

  /// UC-24.24 — unpaged convenience wrapper for callers that just want the
  /// first [size] items and never need filters or "load more" (e.g. the
  /// dashboard's "recent notifications" preview).
  Future<List<AppNotification>> getNotifications({
    bool unreadOnly = false,
    bool allUsers = false,
    int size = 50,
  }) {
    return getNotificationsPage(
      unreadOnly: unreadOnly,
      allUsers: allUsers,
      size: size,
    ).then((page) => page.items);
  }

  /// UC-15.1 — paginated, filterable notification list backing the full list
  /// screen's infinite scroll + filter sheet. Every backend-supported query
  /// param is reachable here: [type] / [priority] / [createdFrom] / [createdTo]
  /// (see `NotificationSpecifications`), [sortBy] ("priority" for URGENT→LOW
  /// severity order, else newest-first) and [page]/[size].
  ///
  /// [allUsers] requests the org-wide "Team activity" feed instead of just the
  /// caller's own notifications; the backend silently ignores it for anyone
  /// but Manager/Admin, so it's safe to pass through unconditionally.
  Future<PaginationResponse<AppNotification>> getNotificationsPage({
    bool unreadOnly = false,
    bool allUsers = false,
    String? type,
    String? priority,
    DateTime? createdFrom,
    DateTime? createdTo,
    String? sortBy,
    int page = 0,
    int size = 20,
  }) {
    return _client.getPaged<AppNotification>(
      ApiPaths.notifications,
      query: {
        'unreadOnly': unreadOnly,
        'allUsers': allUsers,
        'type': ?type,
        'priority': ?priority,
        if (createdFrom != null)
          'createdFrom': createdFrom.toUtc().toIso8601String(),
        if (createdTo != null) 'createdTo': createdTo.toUtc().toIso8601String(),
        'sortBy': ?sortBy,
        'page': page,
        'size': size,
      },
      decodeItem: (e) => AppNotification.fromJson(e as Map<String, dynamic>),
    );
  }

  /// UC-15.2 — access a single notification. Enforces the ownership/access
  /// check server-side (403 for someone else's notification unless the
  /// caller is Manager/Admin, 404 if it doesn't exist) and, as a side effect,
  /// marks it read when it belongs to the caller (`GetNotificationByIdUseCase`).
  Future<AppNotification> getById(String id) {
    return _client.get<AppNotification>(
      ApiPaths.notificationById(id),
      decode: (data) => AppNotification.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-24.25 — mark a single notification read/unread.
  Future<AppNotification> setRead(String id, {required bool read}) {
    return _client.patch<AppNotification>(
      ApiPaths.notificationRead(id),
      query: {'read': read},
      decode: (data) => AppNotification.fromJson(data as Map<String, dynamic>),
    );
  }

  /// Mark all notifications read; returns the number updated.
  Future<int> markAllRead() {
    return _client.patch<int>(
      ApiPaths.notificationsMarkAllRead,
      decode: (data) {
        if (data is Map && data['updated'] is int) {
          return data['updated'] as int;
        }
        if (data is Map && data.values.isNotEmpty && data.values.first is int) {
          return data.values.first as int;
        }
        return 0;
      },
    );
  }
}

final notificationRepositoryProvider = Provider<NotificationRepository>((ref) {
  return NotificationRepository(ref.watch(apiClientProvider));
});
