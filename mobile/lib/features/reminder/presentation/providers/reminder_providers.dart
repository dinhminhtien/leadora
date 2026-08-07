import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../auth/data/dto/auth_user.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/reminder_models.dart';
import '../../data/reminder_repository.dart';

/// What the signed-in user may do inside the Reminder module.
///
/// Dart mirror of the server rules: `GetRemindersUseCase` scopes Sales Staff
/// to their own reminders no matter what the client asks for, while Manager/
/// Admin may see "all staff" by omitting `userId`; `UpdateReminderUseCase`
/// only lets the assignee or a Manager/Admin edit a reminder. Gating the UI on
/// this doesn't *grant* anything — the backend re-checks every call. It only
/// keeps us from offering a control that would come back 403.
class ReminderPermissions {
  const ReminderPermissions({required this.userId, required this.isManager});

  /// Everything a signed-out user may do: nothing.
  static const ReminderPermissions none = ReminderPermissions(
    userId: null,
    isManager: false,
  );

  final String? userId;

  /// MANAGER / ADMIN.
  final bool isManager;

  factory ReminderPermissions.of(AuthUser? user) {
    if (user == null) return none;
    return ReminderPermissions(userId: user.id, isManager: user.hasFullAccess);
  }

  /// Only a Manager/Admin may switch the list to "All staff" — Sales Staff is
  /// hard-scoped to their own reminders server-side, so the toggle would be a
  /// no-op control for them.
  bool get canViewAllStaff => isManager;

  /// `UpdateReminderUseCase`: the assignee or a Manager/Admin.
  bool canEdit(Reminder reminder) =>
      isManager || (userId != null && reminder.assignedUserId == userId);
}

/// The current user's reminder permissions. Rebuilds when the session changes.
final reminderPermissionsProvider = Provider<ReminderPermissions>((ref) {
  return ReminderPermissions.of(ref.watch(currentUserProvider));
});

/// Accumulated, filterable state for the reminder list: the items last loaded
/// plus the filters that produced them (so the search box / chips / sort
/// toggle can read back what's active).
class ReminderListState {
  const ReminderListState({
    this.items = const [],
    this.filters = const ReminderFilters(),
  });

  final List<Reminder> items;
  final ReminderFilters filters;

  ReminderListState copyWith({
    List<Reminder>? items,
    ReminderFilters? filters,
  }) {
    return ReminderListState(
      items: items ?? this.items,
      filters: filters ?? this.filters,
    );
  }
}

/// Loads, searches/filters and mutates the reminder list.
///
/// Defaults to "my reminders" (UC-16.2). When [ReminderFilters.allStaff] is
/// set by a Manager/Admin, `userId` is omitted from the request so the server
/// returns every staff member's reminders instead — Sales Staff never reaches
/// this path since [ReminderPermissions.canViewAllStaff] hides the toggle, and
/// the server would re-scope them anyway.
class ReminderListController extends AutoDisposeAsyncNotifier<ReminderListState> {
  ReminderRepository get _repo => ref.read(reminderRepositoryProvider);

  Future<ReminderListState> _fetch(ReminderFilters filters) async {
    final me = ref.read(currentUserProvider);
    final permissions = ReminderPermissions.of(me);
    final viewAllStaff = filters.allStaff && permissions.canViewAllStaff;
    final items = await _repo.getReminders(
      userId: viewAllStaff ? null : me?.id,
      status: filters.status?.wire,
      remindFrom: filters.remindFrom,
      remindTo: filters.remindTo,
      sortBy: filters.sortBy.wire,
      search: filters.search,
    );
    return ReminderListState(items: items, filters: filters);
  }

  @override
  Future<ReminderListState> build() => _fetch(const ReminderFilters());

  Future<void> refresh() async {
    final filters = state.valueOrNull?.filters ?? const ReminderFilters();
    state = const AsyncLoading<ReminderListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(() => _fetch(filters));
  }

  /// Replace the filter set (search/status/date range/sort/scope) and reload.
  /// The requested filters seed the loading state so the UI updates
  /// immediately and a retry re-runs the selection rather than reverting to
  /// defaults.
  Future<void> applyFilters(ReminderFilters filters) async {
    final seeded = (state.valueOrNull ?? const ReminderListState()).copyWith(
      filters: filters,
    );
    state = const AsyncLoading<ReminderListState>().copyWithPrevious(
      AsyncData(seeded),
      isRefresh: true,
    );
    state = await AsyncValue.guard(() => _fetch(filters));
  }

  ReminderFilters get filters =>
      state.valueOrNull?.filters ?? const ReminderFilters();

  /// Optimistically mark [reminder] done, then reconcile with the server.
  Future<void> dismiss(Reminder reminder) async {
    final current = state.valueOrNull;
    if (current == null) return;
    state = AsyncData(
      current.copyWith(
        items: [
          for (final item in current.items)
            if (item.reminderId != reminder.reminderId) item,
        ],
      ),
    );
    try {
      await _repo.dismiss(reminder.reminderId);
    } catch (_) {
      state = AsyncData(current); // revert on failure
    }
  }
}

final reminderListControllerProvider =
    AutoDisposeAsyncNotifierProvider<ReminderListController, ReminderListState>(
      ReminderListController.new,
    );
