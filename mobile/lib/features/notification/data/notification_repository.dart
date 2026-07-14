import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'notification_models.dart';

/// Notification API calls. The backend returns a plain list (not paged).
class NotificationRepository {
  NotificationRepository(this._client);

  final ApiClient _client;

  /// UC-24.24 — list notifications for the authenticated user.
  ///
  /// The backend returns a Spring `Page<NotificationResponse>` (an object with
  /// a `content` array), not a bare JSON array — [size] is large enough that a
  /// single page covers the list since this screen has no "load more" yet.
  Future<List<AppNotification>> getNotifications({
    bool unreadOnly = false,
    int size = 50,
  }) {
    return _client.get<List<AppNotification>>(
      ApiPaths.notifications,
      query: {'unreadOnly': unreadOnly, 'size': size},
      decode: (data) {
        final list = data is Map && data['content'] is List
            ? data['content'] as List
            : data as List;
        return list
            .map((e) => AppNotification.fromJson(e as Map<String, dynamic>))
            .toList();
      },
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
