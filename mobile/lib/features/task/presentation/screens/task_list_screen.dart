import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../user/data/user_models.dart';
import '../../../user/data/user_repository.dart';
import '../../data/task_models.dart';
import '../providers/task_providers.dart';
import 'task_calendar_view.dart';

/// UC-10.2 / UC-10.5 — Follow-up task list with server-side search, quick
/// status tabs, advanced filters (priority, assignee), pull-to-refresh,
/// infinite scroll and a month-calendar view. Mirrors the web Follow-up
/// Tasks screen (including its list/calendar toggle).
class TaskListScreen extends ConsumerStatefulWidget {
  const TaskListScreen({super.key});

  @override
  ConsumerState<TaskListScreen> createState() => _TaskListScreenState();
}

enum _TaskViewMode { list, calendar }

class _TaskListScreenState extends ConsumerState<TaskListScreen> {
  final _scrollController = ScrollController();
  final _searchController = TextEditingController();
  Timer? _debounce;
  _TaskViewMode _viewMode = _TaskViewMode.list;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _scrollController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  TaskListController get _controller =>
      ref.read(taskListControllerProvider.notifier);

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      _controller.loadMore();
    }
  }

  void _onSearchChanged(String value) {
    setState(() {}); // toggle the clear (X) suffix
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), () {
      _controller.applyFilters(_controller.filters.copyWith(search: value));
    });
  }

  void _clearSearch() {
    _debounce?.cancel();
    setState(() => _searchController.clear());
    _controller.applyFilters(_controller.filters.copyWith(search: ''));
  }

  Future<void> _openFilterSheet() async {
    final result = await showModalBottomSheet<TaskFilters>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _TaskFilterSheet(initial: _controller.filters),
    );
    if (result != null) _controller.applyFilters(result);
  }

  @override
  Widget build(BuildContext context) {
    final asyncState = ref.watch(taskListControllerProvider);
    final filters = asyncState.valueOrNull?.filters ?? const TaskFilters();
    final advancedCount = filters.activeAdvancedCount;

    final isCalendar = _viewMode == _TaskViewMode.calendar;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Follow-up tasks'),
        actions: [
          IconButton(
            tooltip: isCalendar ? 'List view' : 'Calendar view',
            onPressed: () => setState(() => _viewMode =
                isCalendar ? _TaskViewMode.list : _TaskViewMode.calendar),
            icon: Icon(isCalendar
                ? Icons.view_agenda_outlined
                : Icons.calendar_month_outlined),
          ),
          if (!isCalendar)
            IconButton(
              tooltip: 'Filters',
              onPressed: _openFilterSheet,
              icon: Badge.count(
                count: advancedCount,
                isLabelVisible: advancedCount > 0,
                child: const Icon(Icons.tune_rounded),
              ),
            ),
          const SizedBox(width: 4),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        // Distinct hero tag: the shell's IndexedStack keeps the leads + tasks
        // tabs mounted simultaneously, so a default-tagged FAB would collide
        // with the leads FAB's Hero.
        heroTag: 'tasks-fab',
        onPressed: () => context.pushNamed(RouteNames.taskCreate),
        icon: const Icon(Icons.add_task_rounded),
        label: const Text('New task'),
      ),
      body: isCalendar
          ? const TaskCalendarView()
          : Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              controller: _searchController,
              onChanged: _onSearchChanged,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                hintText: 'Search title, customer, contact…',
                prefixIcon: const Icon(Icons.search_rounded),
                isDense: true,
                suffixIcon: _searchController.text.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.close_rounded),
                        onPressed: _clearSearch,
                      ),
              ),
            ),
          ),
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              children: [
                for (final f in TaskFilter.values)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: ChoiceChip(
                      label: Text(f.label),
                      selected: filters.quick == f,
                      onSelected: (_) => _controller.setQuickFilter(f),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: AsyncValueView<TaskListState>(
              value: asyncState,
              onRetry: _controller.refresh,
              isEmpty: (s) => s.items.isEmpty,
              empty: EmptyState(
                icon: Icons.checklist_rounded,
                title: 'No tasks found',
                message: 'Try clearing filters or create a new task.',
                actionLabel: 'New task',
                onAction: () => context.pushNamed(RouteNames.taskCreate),
              ),
              data: (s) => RefreshIndicator(
                onRefresh: _controller.refresh,
                child: ListView.separated(
                  controller: _scrollController,
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 96),
                  itemCount: s.items.length + (s.hasMore ? 1 : 0),
                  separatorBuilder: (_, _) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    if (index >= s.items.length) {
                      return const Padding(
                        padding: EdgeInsets.all(16),
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    return TaskCard(task: s.items[index]);
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Advanced filter editor: priority + assignee. Edits a local draft of the
/// current [initial] filters and pops with the result on Apply.
class _TaskFilterSheet extends ConsumerStatefulWidget {
  const _TaskFilterSheet({required this.initial});

  final TaskFilters initial;

  @override
  ConsumerState<_TaskFilterSheet> createState() => _TaskFilterSheetState();
}

class _TaskFilterSheetState extends ConsumerState<_TaskFilterSheet> {
  late TaskFilters _draft = widget.initial;

  Future<void> _pickAssignee() async {
    final selected = await showModalBottomSheet<UserSummary>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const TaskAssigneePicker(),
    );
    if (selected != null) {
      setState(() =>
          _draft = _draft.withAssignee(selected.userId, selected.fullName));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Filter tasks', style: theme.textTheme.titleMedium),
            const SizedBox(height: 16),
            Text('Priority', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                ChoiceChip(
                  label: const Text('Any'),
                  selected: _draft.priority == null,
                  onSelected: (_) =>
                      setState(() => _draft = _draft.withPriority(null)),
                ),
                for (final p in TaskPriority.values)
                  ChoiceChip(
                    label: Text(Formatters.humanizeEnum(p.wire)),
                    selected: _draft.priority == p,
                    onSelected: (_) =>
                        setState(() => _draft = _draft.withPriority(p)),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            Text('Assignee', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.person_outline_rounded),
              title: Text(
                _draft.assignedUserName ?? 'Anyone',
                style: theme.textTheme.bodyLarge,
              ),
              trailing: _draft.assignedUserId != null
                  ? IconButton(
                      tooltip: 'Clear assignee',
                      icon: const Icon(Icons.close_rounded, size: 20),
                      onPressed: () =>
                          setState(() => _draft = _draft.withAssignee(null, null)),
                    )
                  : const Icon(Icons.chevron_right_rounded),
              onTap: _pickAssignee,
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () =>
                        Navigator.of(context).pop(_draft.resetAdvanced()),
                    child: const Text('Reset'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: FilledButton(
                    onPressed: () => Navigator.of(context).pop(_draft),
                    child: const Text('Apply filters'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Searchable bottom sheet listing assignable users; pops the chosen user.
/// Shared by the task filter sheet and the task form.
class TaskAssigneePicker extends ConsumerStatefulWidget {
  const TaskAssigneePicker({super.key});

  @override
  ConsumerState<TaskAssigneePicker> createState() => _TaskAssigneePickerState();
}

class _TaskAssigneePickerState extends ConsumerState<TaskAssigneePicker> {
  String _query = '';

  List<UserSummary> _filter(List<UserSummary> users) {
    if (_query.isEmpty) return users;
    return users
        .where((u) =>
            u.fullName.toLowerCase().contains(_query) ||
            (u.email?.toLowerCase().contains(_query) ?? false))
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(assignableUsersProvider);
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          bottom: MediaQuery.of(context).viewInsets.bottom + 16,
        ),
        child: SizedBox(
          height: MediaQuery.of(context).size.height * 0.6,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Align(
                alignment: Alignment.centerLeft,
                child: Text('Assign to',
                    style: Theme.of(context).textTheme.titleMedium),
              ),
              const SizedBox(height: 12),
              TextField(
                decoration: const InputDecoration(
                  hintText: 'Search users',
                  prefixIcon: Icon(Icons.search_rounded),
                  isDense: true,
                ),
                onChanged: (v) =>
                    setState(() => _query = v.trim().toLowerCase()),
              ),
              const SizedBox(height: 8),
              Expanded(
                child: AsyncValueView<List<UserSummary>>(
                  value: async,
                  onRetry: () => ref.invalidate(assignableUsersProvider),
                  isEmpty: (u) => _filter(u).isEmpty,
                  data: (users) {
                    final filtered = _filter(users);
                    return ListView.builder(
                      itemCount: filtered.length,
                      itemBuilder: (context, i) {
                        final u = filtered[i];
                        return ListTile(
                          leading: CircleAvatar(
                            child: Text(Formatters.initials(u.fullName)),
                          ),
                          title: Text(u.fullName),
                          subtitle: u.roleName != null
                              ? Text(Formatters.humanizeEnum(u.roleName))
                              : null,
                          onTap: () => Navigator.of(context).pop(u),
                        );
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Reused on the dashboard "upcoming tasks" section too.
class TaskCard extends StatelessWidget {
  const TaskCard({super.key, required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final overdue = task.isOverdue;
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: () => context.pushNamed(
        RouteNames.taskDetail,
        pathParameters: {'id': task.taskId},
      ),
      child: SectionCard(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  task.status == TaskStatus.completed
                      ? Icons.check_circle_rounded
                      : Icons.radio_button_unchecked_rounded,
                  size: 20,
                  color: task.status == TaskStatus.completed
                      ? theme.colorScheme.primary
                      : theme.colorScheme.outline,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    task.title,
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w600,
                      decoration: task.status == TaskStatus.completed
                          ? TextDecoration.lineThrough
                          : null,
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                StatusChip(tone: task.priority.tone, rawStatus: task.priority.wire, dense: true),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                if (task.relatedName != null) ...[
                  Icon(Icons.link_rounded, size: 14, color: theme.colorScheme.outline),
                  const SizedBox(width: 4),
                  Flexible(
                    child: Text(
                      task.relatedName!,
                      style: theme.textTheme.bodySmall
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 12),
                ],
                Icon(
                  Icons.schedule_rounded,
                  size: 14,
                  color: overdue ? theme.colorScheme.error : theme.colorScheme.outline,
                ),
                const SizedBox(width: 4),
                Text(
                  task.endAt == null ? 'No due date' : Formatters.relative(task.endAt),
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: overdue ? theme.colorScheme.error : theme.colorScheme.onSurfaceVariant,
                    fontWeight: overdue ? FontWeight.w700 : FontWeight.w400,
                  ),
                ),
                if (overdue) ...[
                  const SizedBox(width: 8),
                  StatusChip(tone: StatusTone.danger, label: 'Overdue', dense: true),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}
