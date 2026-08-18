import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/notification_models.dart';
import '../../data/notification_repository.dart';

/// Manager/Admin-only "Team activity" toggle — org-wide notifications instead
/// of just the signed-in user's own. Ignored server-side for other roles.
/// [NotificationListController.build] watches this so flipping it refetches.
final notificationViewAllProvider = StateProvider.autoDispose<bool>((_) => false);

/// Which subset of notifications the filter row shows. [all] and [unread] map
/// to the server's `unreadOnly` param; the backend has no "read only" filter,
/// so [read] is narrowed client-side over whatever page(s) are currently
/// loaded (same trade-off `TaskFilters` makes for its Today/Upcoming tabs).
enum NotificationReadFilter { all, unread, read }

/// The full filter set backing the notification list: the quick read-state
/// tab plus every advanced server-side filter `GetNotificationsUseCase`
/// supports (type, priority, created date range, sortBy).
class NotificationFilters {
  const NotificationFilters({
    this.quick = NotificationReadFilter.all,
    this.type,
    this.priority,
    this.createdFrom,
    this.createdTo,
    this.sortByPriority = false,
  });

  final NotificationReadFilter quick;
  final String? type;
  final String? priority;

  /// Inclusive created-date window, picked as *local* calendar days and sent
  /// to the backend as UTC instants (see [utcStartOfLocalDay]/
  /// [utcEndOfLocalDay]) so the window matches the user's timezone.
  final DateTime? createdFrom;
  final DateTime? createdTo;

  /// When true, sends `sortBy=priority` (URGENT→LOW severity, ties
  /// newest-first) instead of the default newest-first ordering.
  final bool sortByPriority;

  bool get unreadOnly => quick == NotificationReadFilter.unread;

  /// Count of advanced (sheet) filters in effect — drives the filter button
  /// badge. The quick tabs are visible inline, so they are not counted here.
  int get activeAdvancedCount =>
      (type != null ? 1 : 0) +
      (priority != null ? 1 : 0) +
      (createdFrom != null || createdTo != null ? 1 : 0) +
      (sortByPriority ? 1 : 0);

  static const _sentinel = Object();

  NotificationFilters copyWith({
    NotificationReadFilter? quick,
    Object? type = _sentinel,
    Object? priority = _sentinel,
    Object? createdFrom = _sentinel,
    Object? createdTo = _sentinel,
    bool? sortByPriority,
  }) {
    return NotificationFilters(
      quick: quick ?? this.quick,
      type: type == _sentinel ? this.type : type as String?,
      priority: priority == _sentinel ? this.priority : priority as String?,
      createdFrom: createdFrom == _sentinel
          ? this.createdFrom
          : createdFrom as DateTime?,
      createdTo: createdTo == _sentinel ? this.createdTo : createdTo as DateTime?,
      sortByPriority: sortByPriority ?? this.sortByPriority,
    );
  }

  /// Reset only the advanced (sheet) filters, keeping the quick tab.
  NotificationFilters resetAdvanced() => NotificationFilters(quick: quick);

  /// Client-side narrowing for the Read tab — see the [NotificationReadFilter]
  /// doc comment for why this can't be a server-side param.
  List<AppNotification> applyQuick(List<AppNotification> items) {
    if (quick != NotificationReadFilter.read) return items;
    return items.where((n) => n.isRead).toList();
  }

  /// Local midnight of [d]'s calendar day, as a UTC instant.
  static DateTime utcStartOfLocalDay(DateTime d) =>
      DateTime(d.year, d.month, d.day).toUtc();

  /// End of [d]'s local calendar day (23:59:59.999), as a UTC instant.
  static DateTime utcEndOfLocalDay(DateTime d) =>
      DateTime(d.year, d.month, d.day, 23, 59, 59, 999).toUtc();

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is NotificationFilters &&
          other.quick == quick &&
          other.type == type &&
          other.priority == priority &&
          other.createdFrom == createdFrom &&
          other.createdTo == createdTo &&
          other.sortByPriority == sortByPriority;

  @override
  int get hashCode => Object.hash(
    quick,
    type,
    priority,
    createdFrom,
    createdTo,
    sortByPriority,
  );
}

class NotificationListState {
  const NotificationListState({
    this.items = const [],
    this.filters = const NotificationFilters(),
    this.isLoadingMore = false,
    this.hasMore = true,
    this.nextPage = 0,
  });

  final List<AppNotification> items;
  final NotificationFilters filters;
  final bool isLoadingMore;
  final bool hasMore;
  final int nextPage;

  NotificationListState copyWith({
    List<AppNotification>? items,
    NotificationFilters? filters,
    bool? isLoadingMore,
    bool? hasMore,
    int? nextPage,
  }) {
    return NotificationListState(
      items: items ?? this.items,
      filters: filters ?? this.filters,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      nextPage: nextPage ?? this.nextPage,
    );
  }
}

/// Loads and mutates the authenticated user's notification list (or, with
/// [notificationViewAllProvider] on, the Manager/Admin org-wide feed) — with
/// real server-side pagination ("load more") and every backend filter param
/// (type/priority/date range/sortBy) reachable via [applyFilters].
class NotificationListController
    extends AutoDisposeAsyncNotifier<NotificationListState> {
  static const _pageSize = 20;

  NotificationRepository get _repo => ref.read(notificationRepositoryProvider);

  /// Current filters, readable synchronously by the screen.
  NotificationFilters get filters =>
      state.valueOrNull?.filters ?? const NotificationFilters();

  @override
  Future<NotificationListState> build() {
    final allUsers = ref.watch(notificationViewAllProvider);
    return _fetch(const NotificationListState(), allUsers: allUsers);
  }

  Future<NotificationListState> _fetch(
    NotificationListState base, {
    required bool allUsers,
  }) async {
    final f = base.filters;
    final page = await _repo.getNotificationsPage(
      unreadOnly: f.unreadOnly,
      allUsers: allUsers,
      type: f.type,
      priority: f.priority,
      createdFrom: f.createdFrom != null
          ? NotificationFilters.utcStartOfLocalDay(f.createdFrom!)
          : null,
      createdTo: f.createdTo != null
          ? NotificationFilters.utcEndOfLocalDay(f.createdTo!)
          : null,
      sortBy: f.sortByPriority ? 'priority' : null,
      page: 0,
      size: _pageSize,
    );
    return base.copyWith(
      items: page.items,
      nextPage: 1,
      hasMore: page.hasMore,
      isLoadingMore: false,
    );
  }

  Future<void> refresh() async {
    final current = state.valueOrNull ?? const NotificationListState();
    state = const AsyncLoading<NotificationListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => _fetch(current, allUsers: ref.read(notificationViewAllProvider)),
    );
  }

  Future<void> setQuickFilter(NotificationReadFilter quick) async {
    final current = state.valueOrNull ?? const NotificationListState();
    if (current.filters.quick == quick && state.hasValue) return;
    await applyFilters(current.filters.copyWith(quick: quick));
  }

  Future<void> applyFilters(NotificationFilters filters) async {
    final current = state.valueOrNull ?? const NotificationListState();
    if (current.filters == filters && state.hasValue) return;
    state = const AsyncLoading<NotificationListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => _fetch(
        current.copyWith(filters: filters),
        allUsers: ref.read(notificationViewAllProvider),
      ),
    );
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    final f = current.filters;
    try {
      final page = await _repo.getNotificationsPage(
        unreadOnly: f.unreadOnly,
        allUsers: ref.read(notificationViewAllProvider),
        type: f.type,
        priority: f.priority,
        createdFrom: f.createdFrom != null
            ? NotificationFilters.utcStartOfLocalDay(f.createdFrom!)
            : null,
        createdTo: f.createdTo != null
            ? NotificationFilters.utcEndOfLocalDay(f.createdTo!)
            : null,
        sortBy: f.sortByPriority ? 'priority' : null,
        page: current.nextPage,
        size: _pageSize,
      );
      state = AsyncData(
        current.copyWith(
          items: [...current.items, ...page.items],
          nextPage: current.nextPage + 1,
          hasMore: page.hasMore,
          isLoadingMore: false,
        ),
      );
    } catch (_) {
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }

  /// UC-15.2 — access a notification via `GET /notifications/{id}`. This
  /// both enforces the ownership/access check server-side (throws an
  /// [AppException] on 403/404 — the caller must not navigate on failure) and,
  /// as a side effect for the caller's own notifications, marks it read. On
  /// success, the local row is refreshed in place so the list reflects the
  /// new read state without a full refetch.
  Future<AppNotification> open(AppNotification n) async {
    final fresh = await _repo.getById(n.id);
    final current = state.valueOrNull;
    if (current != null) {
      state = AsyncData(
        current.copyWith(
          items: [
            for (final item in current.items)
              item.id == fresh.id ? fresh : item,
          ],
        ),
      );
    }
    if (fresh.isRead && !n.isRead) {
      ref.invalidate(unreadNotificationCountProvider);
    }
    return fresh;
  }

  /// Optimistically flip read state, then reconcile with the server. Kept for
  /// any explicit manual "mark read/unread" toggle UI action — tapping a
  /// notification to open it now goes through [open] instead (UC-15.2 access
  /// must gate navigation on the ownership check, which this endpoint does
  /// not perform).
  ///
  /// Team Activity rows (Manager/Admin "view all" feed) belong to someone
  /// else — the backend rejects toggling their read state, so this is a
  /// no-op for them rather than an optimistic update that would just revert.
  Future<void> toggleRead(AppNotification n) async {
    if (!_isOwn(n)) return;
    final current = state.valueOrNull;
    if (current == null) return;
    final target = !n.isRead;
    state = AsyncData(
      current.copyWith(
        items: [
          for (final item in current.items)
            item.id == n.id ? item.copyWith(isRead: target) : item,
        ],
      ),
    );
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
    state = AsyncData(
      current.copyWith(
        items: [
          for (final item in current.items)
            _isOwn(item) ? item.copyWith(isRead: true) : item,
        ],
      ),
    );
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
      NotificationListState
    >(NotificationListController.new);

/// Unread badge count for the bottom-nav icon. Kept independent so it can be
/// watched by the shell without keeping the full list alive. Reads
/// `totalElements` off a single-item page rather than counting a capped list,
/// so it stays accurate past whatever page size the full list screen uses.
final unreadNotificationCountProvider = AutoDisposeFutureProvider<int>((
  ref,
) async {
  final page = await ref
      .watch(notificationRepositoryProvider)
      .getNotificationsPage(unreadOnly: true, size: 1);
  return page.totalElements;
});
