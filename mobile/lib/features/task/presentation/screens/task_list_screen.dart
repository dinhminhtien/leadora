import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_filter_chip.dart';
import '../../../../shared/widgets/app_search_field.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/list_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../user/data/user_models.dart';
import '../../../user/data/user_repository.dart';
import '../../data/task_models.dart';
import '../providers/task_permissions.dart';
import '../providers/task_providers.dart';
import 'task_calendar_view.dart';

/// Dummy row for the loading skeleton — same widget as a real row so the list
/// keeps its shape when data lands.
const _skeletonTask = Task(
  taskId: '',
  title: 'Call: Placeholder follow-up task',
  status: TaskStatus.open,
  priority: TaskPriority.high,
);

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
  _TaskViewMode _viewMode = _TaskViewMode.list;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
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

  void _onSearchChanged(String term) =>
      _controller.applyFilters(_controller.filters.copyWith(search: term));

  Future<void> _completeTask(Task task) async {
    final messenger = ScaffoldMessenger.of(context);
    final error = await _controller.completeTask(task.taskId);
    if (!mounted) return;
    messenger.showSnackBar(SnackBar(content: Text(error ?? 'Task completed')));
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
            onPressed: () => setState(
              () => _viewMode = isCalendar
                  ? _TaskViewMode.list
                  : _TaskViewMode.calendar,
            ),
            icon: Icon(
              isCalendar
                  ? Icons.view_agenda_outlined
                  : Icons.calendar_month_outlined,
            ),
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
                AppSearchField(
                  hintText: 'Search title, customer, contact…',
                  initialValue: filters.search,
                  onChanged: _onSearchChanged,
                ),
                AppFilterChipBar(
                  children: [
                    for (final f in TaskFilter.values)
                      AppFilterChip(
                        label: f.label,
                        selected: filters.quick == f,
                        onTap: () => _controller.setQuickFilter(f),
                      ),
                  ],
                ),
                const SizedBox(height: AppSpacing.xs),
                Expanded(
                  child: AsyncValueView<TaskListState>(
                    value: asyncState,
                    onRetry: _controller.refresh,
                    loading: ListSkeleton(
                      separatorHeight: AppSpacing.sm,
                      itemBuilder: (_) => const TaskCard(task: _skeletonTask),
                    ),
                    isEmpty: (s) => s.filters.applyQuick(s.items).isEmpty,
                    empty: EmptyState(
                      icon: Icons.checklist_rounded,
                      title: 'No tasks found',
                      message: 'Try clearing filters or create a new task.',
                      actionLabel: 'New task',
                      onAction: () => context.pushNamed(RouteNames.taskCreate),
                    ),
                    data: (s) {
                      final visible = s.filters.applyQuick(s.items);
                      return RefreshIndicator(
                        onRefresh: _controller.refresh,
                        child: ListView.separated(
                          controller: _scrollController,
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.fromLTRB(
                            AppSpacing.lg,
                            AppSpacing.xs,
                            AppSpacing.lg,
                            AppSpacing.fabClearance,
                          ),
                          itemCount: visible.length + (s.hasMore ? 1 : 0),
                          separatorBuilder: (_, _) =>
                              const SizedBox(height: AppSpacing.sm),
                          itemBuilder: (context, index) {
                            if (index >= visible.length) {
                              return const Padding(
                                padding: EdgeInsets.all(AppSpacing.lg),
                                child: Center(
                                  child: CircularProgressIndicator(),
                                ),
                              );
                            }
                            final task = visible[index];
                            return TaskCard(
                              task: task,
                              onComplete: task.isOpen
                                  ? () => _completeTask(task)
                                  : null,
                            );
                          },
                        ),
                      );
                    },
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
      setState(
        () => _draft = _draft.withAssignee(selected.userId, selected.fullName),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.xl,
          0,
          AppSpacing.xl,
          AppSpacing.lg,
        ),
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
            // Staff are hard-scoped to their own tasks server-side
            // (`listScopeOwnerId`), which means the assignee param is ignored for
            // them — offering the filter would just be a control that does
            // nothing.
            if (ref.watch(taskPermissionsProvider).canFilterByAssignee) ...[
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
                        onPressed: () => setState(
                          () => _draft = _draft.withAssignee(null, null),
                        ),
                      )
                    : const Icon(Icons.chevron_right_rounded),
                onTap: _pickAssignee,
              ),
            ],
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
/// Shared by the task filter sheet and the task form. [excludeUserId] hides a
/// user from the list — the resign flow must hand over to somebody else.
class TaskAssigneePicker extends ConsumerStatefulWidget {
  const TaskAssigneePicker({super.key, this.excludeUserId});

  final String? excludeUserId;

  @override
  ConsumerState<TaskAssigneePicker> createState() => _TaskAssigneePickerState();
}

class _TaskAssigneePickerState extends ConsumerState<TaskAssigneePicker> {
  String _query = '';

  List<UserSummary> _filter(List<UserSummary> users) {
    return users
        .where((u) => u.userId != widget.excludeUserId)
        .where(
          (u) =>
              _query.isEmpty ||
              u.fullName.toLowerCase().contains(_query) ||
              (u.email?.toLowerCase().contains(_query) ?? false),
        )
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
                child: Text(
                  'Assign to',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
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

/// Reused on the dashboard "upcoming tasks" section too. [onComplete], when
/// set, makes the leading circle a one-tap "mark complete" toggle — the mobile
/// twin of the web table's Done checkbox.
class TaskCard extends StatelessWidget {
  const TaskCard({super.key, required this.task, this.onComplete});

  final Task task;
  final VoidCallback? onComplete;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final overdue = task.isOverdue;
    final done = task.status == TaskStatus.completed;
    final cancelled = task.status == TaskStatus.cancelled;
    final type = task.activityType;
    // The title is shown exactly as stored. We no longer strip a "Call: " prefix,
    // because that was the same title-parsing the activity type used to rely on —
    // and a task legitimately titled "Call: …" would have lost its first word.
    // Tasks created before the activity-type migration still carry the old prefix
    // in their title; that is their real title, and it fades out as they're edited.
    final displayTitle = task.title;

    return InkWell(
      borderRadius: BorderRadius.circular(AppRadii.lg),
      onTap: () => context.pushNamed(
        RouteNames.taskDetail,
        pathParameters: {'id': task.taskId},
      ),
      child: Opacity(
        opacity: cancelled ? 0.55 : 1,
        child: SectionCard(
          // Tighter on the left: the leading checkbox carries its own tap
          // padding, so a full inset would double it.
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.sm,
            AppSpacing.md,
            AppSpacing.lg,
            AppSpacing.md,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Done toggle — 44dp tap target.
              SizedBox(
                width: 44,
                height: 44,
                child: IconButton(
                  tooltip: done
                      ? 'Completed'
                      : onComplete == null
                      ? null
                      : 'Mark complete',
                  onPressed: onComplete,
                  icon: Icon(
                    done
                        ? Icons.check_circle_rounded
                        : cancelled
                        ? Icons.cancel_rounded
                        : Icons.radio_button_unchecked_rounded,
                    size: 22,
                    color: done
                        ? theme.colorScheme.primary
                        : overdue
                        ? theme.colorScheme.error
                        : theme.colorScheme.outline,
                  ),
                ),
              ),
              const SizedBox(width: 2),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Text(
                            displayTitle.trim().isEmpty
                                ? task.title
                                : displayTitle,
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.w600,
                              decoration: done
                                  ? TextDecoration.lineThrough
                                  : null,
                            ),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        StatusChip(
                          tone: task.priority.tone,
                          rawStatus: task.priority.wire,
                          dense: true,
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        StatusChip(
                          tone: StatusTone.neutral,
                          color: type.color,
                          icon: type.icon,
                          label: type.label,
                          dense: true,
                        ),
                        if (task.relatedName != null) ...[
                          const SizedBox(width: 8),
                          Icon(
                            Icons.link_rounded,
                            size: 14,
                            color: scheme.outline,
                          ),
                          const SizedBox(width: 3),
                          Flexible(
                            child: Text(
                              task.relatedName!,
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: scheme.onSurfaceVariant,
                                fontWeight: FontWeight.w500,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Icon(
                          Icons.schedule_rounded,
                          size: 14,
                          color: overdue ? scheme.error : scheme.outline,
                        ),
                        const SizedBox(width: 4),
                        Flexible(
                          child: Text(
                            _scheduleLabel(),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: overdue
                                  ? scheme.error
                                  : scheme.onSurfaceVariant,
                              fontWeight: overdue
                                  ? FontWeight.w700
                                  : FontWeight.w400,
                            ),
                          ),
                        ),
                        if (overdue) ...[
                          const SizedBox(width: 8),
                          const StatusChip(
                            tone: StatusTone.danger,
                            label: 'Overdue',
                            dense: true,
                          ),
                        ] else if (cancelled) ...[
                          const SizedBox(width: 8),
                          const StatusChip(
                            tone: StatusTone.neutral,
                            label: 'Cancelled',
                            dense: true,
                          ),
                        ],
                        if (task.assignedUserName != null) ...[
                          const Spacer(),
                          const SizedBox(width: 8),
                          Icon(
                            Icons.person_outline_rounded,
                            size: 14,
                            color: scheme.outline,
                          ),
                          const SizedBox(width: 3),
                          // Flexible (not a fixed box) so tight widths squeeze
                          // the name instead of overflowing the row.
                          Flexible(
                            child: Text(
                              task.assignedUserName!,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: scheme.onSurfaceVariant,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// "24 Jun · 09:00–10:00" when scheduled (year only when not this year),
  /// else the relative due phrasing.
  String _scheduleLabel() {
    if (task.startAt != null) {
      final local = task.startAt!.toLocal();
      final date = local.year == DateTime.now().year
          ? Formatters.shortDate(local)
          : Formatters.date(local);
      final start = Formatters.time(task.startAt);
      final end = task.endAt != null ? '–${Formatters.time(task.endAt)}' : '';
      return '$date · $start$end';
    }
    if (task.endAt != null) return 'Due ${Formatters.relative(task.endAt)}';
    return 'No schedule';
  }
}
