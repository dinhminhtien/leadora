import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/task_models.dart';
import '../../data/task_repository.dart';

/// Quick filters for the task list tab bar.
enum TaskFilter {
  all('All'),
  open('Open'),
  overdue('Overdue'),
  completed('Completed');

  const TaskFilter(this.label);
  final String label;
}

class TaskListState {
  const TaskListState({
    this.items = const [],
    this.filter = TaskFilter.open,
    this.isLoadingMore = false,
    this.hasMore = true,
    this.nextPage = 0,
  });

  final List<Task> items;
  final TaskFilter filter;
  final bool isLoadingMore;
  final bool hasMore;
  final int nextPage;

  TaskListState copyWith({
    List<Task>? items,
    TaskFilter? filter,
    bool? isLoadingMore,
    bool? hasMore,
    int? nextPage,
  }) {
    return TaskListState(
      items: items ?? this.items,
      filter: filter ?? this.filter,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      nextPage: nextPage ?? this.nextPage,
    );
  }
}

class TaskListController extends AutoDisposeAsyncNotifier<TaskListState> {
  static const _pageSize = 20;

  TaskRepository get _repo => ref.read(taskRepositoryProvider);

  @override
  Future<TaskListState> build() => _fetch(const TaskListState());

  ({String? status, bool overdue}) _queryFor(TaskFilter f) => switch (f) {
        TaskFilter.all => (status: null, overdue: false),
        TaskFilter.open => (status: 'OPEN', overdue: false),
        TaskFilter.overdue => (status: null, overdue: true),
        TaskFilter.completed => (status: 'COMPLETED', overdue: false),
      };

  Future<TaskListState> _fetch(TaskListState base) async {
    final q = _queryFor(base.filter);
    final page = await _repo.getTasks(
      status: q.status,
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

  Future<void> setFilter(TaskFilter filter) async {
    final current = state.valueOrNull ?? const TaskListState();
    if (current.filter == filter && state.hasValue) return;
    state = const AsyncLoading<TaskListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(() => _fetch(current.copyWith(filter: filter)));
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    final q = _queryFor(current.filter);
    try {
      final page = await _repo.getTasks(
        status: q.status,
        overdue: q.overdue,
        page: current.nextPage,
        size: _pageSize,
      );
      state = AsyncData(current.copyWith(
        items: [...current.items, ...page.items],
        nextPage: current.nextPage + 1,
        hasMore: page.hasMore,
        isLoadingMore: false,
      ));
    } catch (_) {
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }
}

final taskListControllerProvider =
    AutoDisposeAsyncNotifierProvider<TaskListController, TaskListState>(
        TaskListController.new);

final taskDetailProvider =
    AutoDisposeFutureProvider.family<Task, String>((ref, taskId) {
  return ref.watch(taskRepositoryProvider).getTask(taskId);
});
