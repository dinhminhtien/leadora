import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../data/task_models.dart';
import '../../data/task_repository.dart';

/// Quick status tabs for the task list — the primary, always-visible filter.
/// Mirrors the web tab set (All / Today / Upcoming / Overdue / Completed);
/// Today and Upcoming are narrowed client-side over the fetched items, exactly
/// like the web screen filters its current page.
enum TaskFilter {
  all('All'),
  today('Today'),
  upcoming('Upcoming'),
  overdue('Overdue'),
  completed('Completed');

  const TaskFilter(this.label);
  final String label;
}

/// The full filter set backing the task list: the quick status tab plus the
/// server-side search string and advanced filters (priority, assignee). Mirrors
/// the web Follow-up Tasks screen, which sends the same query params.
class TaskFilters {
  const TaskFilters({
    this.quick = TaskFilter.all,
    this.search = '',
    this.priority,
    this.assignedUserId,
    this.assignedUserName,
  });

  final TaskFilter quick;
  final String search;
  final TaskPriority? priority;
  final String? assignedUserId;
  final String? assignedUserName;

  /// Count of advanced filters in effect — drives the filter-button badge.
  int get activeAdvancedCount =>
      (priority != null ? 1 : 0) + (assignedUserId != null ? 1 : 0);

  TaskFilters copyWith({
    TaskFilter? quick,
    String? search,
    TaskPriority? priority,
    String? assignedUserId,
    String? assignedUserName,
  }) {
    return TaskFilters(
      quick: quick ?? this.quick,
      search: search ?? this.search,
      priority: priority ?? this.priority,
      assignedUserId: assignedUserId ?? this.assignedUserId,
      assignedUserName: assignedUserName ?? this.assignedUserName,
    );
  }

  /// Reset only the advanced (sheet) filters, keeping the quick tab + search.
  TaskFilters resetAdvanced() => TaskFilters(quick: quick, search: search);

  /// Explicit clearing (copyWith can't set a field back to null).
  TaskFilters withPriority(TaskPriority? p) => TaskFilters(
    quick: quick,
    search: search,
    priority: p,
    assignedUserId: assignedUserId,
    assignedUserName: assignedUserName,
  );

  TaskFilters withAssignee(String? id, String? name) => TaskFilters(
    quick: quick,
    search: search,
    priority: priority,
    assignedUserId: id,
    assignedUserName: name,
  );

  /// The backend `status` + `overdue` params implied by the quick tab.
  /// Today / Upcoming fetch everything and narrow client-side (web parity).
  ({String? status, bool overdue}) get statusQuery => switch (quick) {
    TaskFilter.all ||
    TaskFilter.today ||
    TaskFilter.upcoming => (status: null, overdue: false),
    TaskFilter.overdue => (status: null, overdue: true),
    TaskFilter.completed => (status: 'COMPLETED', overdue: false),
  };

  /// Client-side narrowing for the Today / Upcoming tabs. Grouping is by the
  /// task's `startAt` local date, matching the web `taskDateKey`.
  List<Task> applyQuick(List<Task> items) {
    if (quick != TaskFilter.today && quick != TaskFilter.upcoming) return items;
    final now = DateTime.now();
    final tomorrow = DateTime(
      now.year,
      now.month,
      now.day,
    ).add(const Duration(days: 1));
    bool isActionable(Task t) =>
        t.status != TaskStatus.completed && t.status != TaskStatus.cancelled;
    bool isToday(DateTime d) =>
        d.year == now.year && d.month == now.month && d.day == now.day;
    return [
      for (final t in items)
        if (t.startAt != null && isActionable(t))
          if (quick == TaskFilter.today
              ? isToday(t.startAt!.toLocal())
              : !t.startAt!.toLocal().isBefore(tomorrow) && !t.isOverdue)
            t,
    ];
  }
}

class TaskListState {
  const TaskListState({
    this.items = const [],
    this.filters = const TaskFilters(),
    this.isLoadingMore = false,
    this.hasMore = true,
    this.nextPage = 0,
  });

  final List<Task> items;
  final TaskFilters filters;
  final bool isLoadingMore;
  final bool hasMore;
  final int nextPage;

  TaskListState copyWith({
    List<Task>? items,
    TaskFilters? filters,
    bool? isLoadingMore,
    bool? hasMore,
    int? nextPage,
  }) {
    return TaskListState(
      items: items ?? this.items,
      filters: filters ?? this.filters,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      nextPage: nextPage ?? this.nextPage,
    );
  }
}

class TaskListController extends AutoDisposeAsyncNotifier<TaskListState> {
  static const _pageSize = 20;

  TaskRepository get _repo => ref.read(taskRepositoryProvider);

  /// Current filters, readable synchronously by the screen.
  TaskFilters get filters => state.valueOrNull?.filters ?? const TaskFilters();

  @override
  Future<TaskListState> build() => _fetch(const TaskListState());

  Future<TaskListState> _fetch(TaskListState base) async {
    final f = base.filters;
    final q = f.statusQuery;
    final page = await _repo.getTasks(
      search: f.search,
      status: q.status,
      priority: f.priority?.wire,
      assignedUserId: f.assignedUserId,
      overdue: q.overdue,
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
    final current = state.valueOrNull ?? const TaskListState();
    state = const AsyncLoading<TaskListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(() => _fetch(current));
  }

  Future<void> setQuickFilter(TaskFilter quick) async {
    final current = state.valueOrNull ?? const TaskListState();
    if (current.filters.quick == quick && state.hasValue) return;
    await applyFilters(current.filters.copyWith(quick: quick));
  }

  Future<void> applyFilters(TaskFilters filters) async {
    final current = state.valueOrNull ?? const TaskListState();
    state = const AsyncLoading<TaskListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => _fetch(current.copyWith(filters: filters)),
    );
  }

  /// Quick-complete from the list, with an optimistic flip (web parity: the
  /// row checkbox completes immediately and rolls back on failure). Uses the
  /// dedicated resolve endpoint so SLA tracking and reminders settle too.
  /// Returns null on success, or the error message for the caller's snackbar.
  Future<String?> completeTask(String taskId) async {
    final current = state.valueOrNull;
    if (current == null) return null;
    state = AsyncData(
      current.copyWith(
        items: [
          for (final t in current.items)
            t.taskId == taskId ? t.withStatus(TaskStatus.completed) : t,
        ],
      ),
    );
    try {
      await _repo.resolve(taskId);
      return null;
    } on AppException catch (e) {
      state = AsyncData(current); // roll back the optimistic flip
      return e.message;
    } catch (_) {
      state = AsyncData(current);
      return 'Could not complete the task.';
    }
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    final f = current.filters;
    final q = f.statusQuery;
    try {
      final page = await _repo.getTasks(
        search: f.search,
        status: q.status,
        priority: f.priority?.wire,
        assignedUserId: f.assignedUserId,
        overdue: q.overdue,
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
}

final taskListControllerProvider =
    AutoDisposeAsyncNotifierProvider<TaskListController, TaskListState>(
      TaskListController.new,
    );

final taskDetailProvider = AutoDisposeFutureProvider.family<Task, String>((
  ref,
  taskId,
) {
  return ref.watch(taskRepositoryProvider).getTask(taskId);
});
