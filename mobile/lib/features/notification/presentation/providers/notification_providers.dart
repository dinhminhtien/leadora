import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/notification_models.dart';
import '../../data/notification_repository.dart';

/// Loads and mutates the authenticated user's notification list.
class NotificationListController
    extends AutoDisposeAsyncNotifier<List<AppNotification>> {
  NotificationRepository get _repo => ref.read(notificationRepositoryProvider);

  @override
  Future<List<AppNotification>> build() => _repo.getNotifications();

  Future<void> refresh() async {
    state = const AsyncLoading<List<AppNotification>>().copyWithPrevious(state);
    state = await AsyncValue.guard(_repo.getNotifications);
  }

  /// Optimistically flip read state, then reconcile with the server.
  Future<void> toggleRead(AppNotification n) async {
    final current = state.valueOrNull;
    if (current == null) return;
    final target = !n.isRead;
    state = AsyncData([
      for (final item in current)
        item.id == n.id ? item.copyWith(isRead: target) : item,
    ]);
    try {
      await _repo.setRead(n.id, read: target);
    } catch (_) {
      state = AsyncData(current); // revert on failure
    }
    ref.invalidate(unreadNotificationCountProvider);
  }

  Future<void> markAllRead() async {
    final current = state.valueOrNull;
    if (current == null) return;
    state = AsyncData([
      for (final item in current) item.copyWith(isRead: true),
    ]);
    try {
      await _repo.markAllRead();
    } catch (_) {
      state = AsyncData(current);
    }
    ref.invalidate(unreadNotificationCountProvider);
  }
}

final notificationListControllerProvider =
    AutoDisposeAsyncNotifierProvider<
      NotificationListController,
      List<AppNotification>
    >(NotificationListController.new);

/// Unread badge count for the bottom-nav icon. Kept independent so it can be
/// watched by the shell without keeping the full list alive.
final unreadNotificationCountProvider = AutoDisposeFutureProvider<int>((
  ref,
) async {
  final list = await ref
      .watch(notificationRepositoryProvider)
      .getNotifications(unreadOnly: true);
  return list.length;
});
