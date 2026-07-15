import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/notification_models.dart';
import '../../data/notification_repository.dart';

/// Manager/Admin-only "Team activity" toggle — org-wide notifications instead
/// of just the signed-in user's own. Ignored server-side for other roles.
/// [NotificationListController.build] watches this so flipping it refetches.
final notificationViewAllProvider = StateProvider.autoDispose<bool>((_) => false);

/// Loads and mutates the authenticated user's notification list (or, with
/// [notificationViewAllProvider] on, the Manager/Admin org-wide feed).
class NotificationListController
    extends AutoDisposeAsyncNotifier<List<AppNotification>> {
  NotificationRepository get _repo => ref.read(notificationRepositoryProvider);

  @override
  Future<List<AppNotification>> build() {
    final allUsers = ref.watch(notificationViewAllProvider);
    return _repo.getNotifications(allUsers: allUsers);
  }

  Future<void> refresh() async {
    state = const AsyncLoading<List<AppNotification>>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => _repo.getNotifications(
        allUsers: ref.read(notificationViewAllProvider),
      ),
    );
  }

  /// Optimistically flip read state, then reconcile with the server.
  ///
  /// Team Activity rows (Manager/Admin "view all" feed) belong to someone
  /// else — the backend rejects toggling their read state, so this is a
  /// no-op for them rather than an optimistic update that would just revert.
  Future<void> toggleRead(AppNotification n) async {
    if (!_isOwn(n)) return;
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

  /// Marks the caller's own unread notifications read (`MarkAllReadUseCase`
  /// is always self-scoped server-side, even from the Team Activity feed) —
  /// so only own rows are optimistically flipped; everyone else's keep
  /// showing their real read state.
  Future<void> markAllRead() async {
    final current = state.valueOrNull;
    if (current == null) return;
    state = AsyncData([
      for (final item in current)
        _isOwn(item) ? item.copyWith(isRead: true) : item,
    ]);
    try {
      await _repo.markAllRead();
    } catch (_) {
      state = AsyncData(current);
    }
    ref.invalidate(unreadNotificationCountProvider);
  }

  bool _isOwn(AppNotification n) =>
      n.recipientId == null || n.recipientId == ref.read(currentUserProvider)?.id;
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
