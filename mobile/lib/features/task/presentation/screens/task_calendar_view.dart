import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/task_models.dart';
import '../../data/task_repository.dart';

/// Calendar batch — mirrors the web calendar query (one large page, grouped
/// client-side; the backend has no date-range filter on `GET /tasks`).
final calendarTasksProvider = AutoDisposeFutureProvider<List<Task>>((
  ref,
) async {
  final page = await ref
      .watch(taskRepositoryProvider)
      .getTasks(page: 0, size: 200);
  return page.items;
});

/// Mobile-first month calendar + day agenda for follow-up tasks (UC-10.5).
///
/// Swipeable month grid (PageView) with per-day event dots colored by task
/// state, a today shortcut, animated selected-day highlight, and a card-based
/// agenda for the selected day underneath. Embedded by [TaskListScreen] when
/// the calendar view mode is active.
class TaskCalendarView extends ConsumerStatefulWidget {
  const TaskCalendarView({super.key});

  @override
  ConsumerState<TaskCalendarView> createState() => _TaskCalendarViewState();
}

class _TaskCalendarViewState extends ConsumerState<TaskCalendarView> {
  /// Months are addressed as an index on an arbitrary fixed epoch so the
  /// PageView can swipe backwards and forwards without bounds.
  static final DateTime _epochMonth = DateTime(2020, 1);
  static int _monthIndex(DateTime m) =>
      (m.year - _epochMonth.year) * 12 + (m.month - _epochMonth.month);
  static DateTime _monthAt(int index) =>
      DateTime(_epochMonth.year, _epochMonth.month + index);

  late final PageController _pageController;
  late DateTime _visibleMonth;
  late DateTime _selectedDay;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _visibleMonth = DateTime(now.year, now.month);
    _selectedDay = DateTime(now.year, now.month, now.day);
    _pageController = PageController(initialPage: _monthIndex(_visibleMonth));
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _goToToday() {
    final now = DateTime.now();
    setState(() => _selectedDay = DateTime(now.year, now.month, now.day));
    _pageController.animateToPage(
      _monthIndex(DateTime(now.year, now.month)),
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeOutCubic,
    );
  }

  void _shiftMonth(int delta) {
    _pageController.animateToPage(
      _monthIndex(_visibleMonth) + delta,
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeOutCubic,
    );
  }

  static DateTime? _dayKey(Task t) {
    final d = t.anchorDate;
    if (d == null) return null;
    final local = d.toLocal();
    return DateTime(local.year, local.month, local.day);
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(calendarTasksProvider);

    return AsyncValueView<List<Task>>(
      value: async,
      onRetry: () => ref.invalidate(calendarTasksProvider),
      data: (tasks) {
        final byDay = <DateTime, List<Task>>{};
        for (final t in tasks) {
          final key = _dayKey(t);
          if (key == null) continue;
          byDay.putIfAbsent(key, () => []).add(t);
        }
        final dayTasks = List<Task>.of(byDay[_selectedDay] ?? const [])
          ..sort((a, b) {
            final at = a.anchorDate, bt = b.anchorDate;
            if (at == null || bt == null) return 0;
            return at.compareTo(bt);
          });

        return RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(calendarTasksProvider);
            await ref.read(calendarTasksProvider.future);
          },
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              // Month toolbar + grid stay pinned while the agenda scrolls.
              SliverToBoxAdapter(
                child: Column(
                  children: [
                    _MonthToolbar(
                      month: _visibleMonth,
                      onPrev: () => _shiftMonth(-1),
                      onNext: () => _shiftMonth(1),
                      onToday: _goToToday,
                    ),
                    _WeekdayHeader(),
                    SizedBox(
                      height: 6 * 46,
                      child: PageView.builder(
                        controller: _pageController,
                        onPageChanged: (index) =>
                            setState(() => _visibleMonth = _monthAt(index)),
                        itemBuilder: (context, index) => _MonthGrid(
                          month: _monthAt(index),
                          selectedDay: _selectedDay,
                          tasksByDay: byDay,
                          onSelect: (day) => setState(() => _selectedDay = day),
                        ),
                      ),
                    ),
                    const Divider(),
                  ],
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.lg,
                  AppSpacing.sm,
                  AppSpacing.lg,
                  96,
                ),
                sliver: dayTasks.isEmpty
                    ? SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.only(top: AppSpacing.xl),
                          child: EmptyState(
                            icon: Icons.event_available_rounded,
                            title: 'Nothing scheduled',
                            message:
                                'No follow-ups on ${Formatters.date(_selectedDay)}.',
                            actionLabel: 'New task',
                            onAction: () =>
                                context.pushNamed(RouteNames.taskCreate),
                          ),
                        ),
                      )
                    : SliverList.separated(
                        itemCount: dayTasks.length + 1,
                        separatorBuilder: (_, _) =>
                            const SizedBox(height: AppSpacing.md),
                        itemBuilder: (context, i) {
                          if (i == 0) {
                            return Padding(
                              padding: const EdgeInsets.only(
                                bottom: AppSpacing.xs,
                              ),
                              child: Text(
                                '${dayTasks.length} follow-up${dayTasks.length == 1 ? '' : 's'} · ${Formatters.date(_selectedDay)}',
                                style: Theme.of(context).textTheme.titleSmall
                                    ?.copyWith(fontWeight: FontWeight.w700),
                              ),
                            );
                          }
                          return _AgendaCard(task: dayTasks[i - 1]);
                        },
                      ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _MonthToolbar extends StatelessWidget {
  const _MonthToolbar({
    required this.month,
    required this.onPrev,
    required this.onNext,
    required this.onToday,
  });

  final DateTime month;
  final VoidCallback onPrev;
  final VoidCallback onNext;
  final VoidCallback onToday;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.xs,
        AppSpacing.sm,
        AppSpacing.xs,
      ),
      child: Row(
        children: [
          Expanded(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 200),
              child: Text(
                Formatters.monthYear(month),
                key: ValueKey(month),
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),
          TextButton(onPressed: onToday, child: const Text('Today')),
          IconButton(
            tooltip: 'Previous month',
            onPressed: onPrev,
            icon: const Icon(Icons.chevron_left_rounded),
          ),
          IconButton(
            tooltip: 'Next month',
            onPressed: onNext,
            icon: const Icon(Icons.chevron_right_rounded),
          ),
        ],
      ),
    );
  }
}

class _WeekdayHeader extends StatelessWidget {
  static const _labels = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm),
      child: Row(
        children: [
          for (final label in _labels)
            Expanded(
              child: Center(
                child: Text(
                  label,
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontWeight: FontWeight.w700,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// A 6-row Monday-first month grid (fixed height so month swipes don't jump).
class _MonthGrid extends StatelessWidget {
  const _MonthGrid({
    required this.month,
    required this.selectedDay,
    required this.tasksByDay,
    required this.onSelect,
  });

  final DateTime month;
  final DateTime selectedDay;
  final Map<DateTime, List<Task>> tasksByDay;
  final ValueChanged<DateTime> onSelect;

  @override
  Widget build(BuildContext context) {
    final first = DateTime(month.year, month.month, 1);
    // Monday-first offset: DateTime.weekday is 1 (Mon) … 7 (Sun).
    final leading = first.weekday - 1;
    final gridStart = first.subtract(Duration(days: leading));

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm),
      child: Column(
        children: [
          for (var week = 0; week < 6; week++)
            Row(
              children: [
                for (var dow = 0; dow < 7; dow++)
                  Expanded(
                    child: _DayCell(
                      day: DateTime(
                        gridStart.year,
                        gridStart.month,
                        gridStart.day + week * 7 + dow,
                      ),
                      month: month,
                      selectedDay: selectedDay,
                      tasks:
                          tasksByDay[DateTime(
                            gridStart.year,
                            gridStart.month,
                            gridStart.day + week * 7 + dow,
                          )] ??
                          const [],
                      onSelect: onSelect,
                    ),
                  ),
              ],
            ),
        ],
      ),
    );
  }
}

class _DayCell extends StatelessWidget {
  const _DayCell({
    required this.day,
    required this.month,
    required this.selectedDay,
    required this.tasks,
    required this.onSelect,
  });

  final DateTime day;
  final DateTime month;
  final DateTime selectedDay;
  final List<Task> tasks;
  final ValueChanged<DateTime> onSelect;

  /// Dot color: overdue → danger, completed → success, cancelled → neutral,
  /// otherwise the priority tone. Matches the agenda card chips.
  static Color _dotColor(Task t) {
    if (t.isOverdue) return AppColors.danger;
    return switch (t.status) {
      TaskStatus.completed => AppColors.success,
      TaskStatus.cancelled => AppColors.neutral,
      TaskStatus.open => switch (t.priority) {
        TaskPriority.high => AppColors.danger,
        TaskPriority.medium => AppColors.info,
        TaskPriority.low => AppColors.neutral,
      },
    };
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final now = DateTime.now();
    final isToday =
        day.year == now.year && day.month == now.month && day.day == now.day;
    final isSelected = day == selectedDay;
    final inMonth = day.month == month.month;

    final textColor = isSelected
        ? scheme.onPrimary
        : !inMonth
        ? scheme.outlineVariant
        : isToday
        ? scheme.primary
        : scheme.onSurface;

    return InkResponse(
      onTap: () => onSelect(day),
      radius: 26,
      child: SizedBox(
        height: 46,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              curve: Curves.easeOutCubic,
              width: 30,
              height: 30,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: isSelected ? scheme.primary : Colors.transparent,
                shape: BoxShape.circle,
                border: isToday && !isSelected
                    ? Border.all(color: scheme.primary, width: 1.4)
                    : null,
              ),
              child: Text(
                '${day.day}',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: textColor,
                  fontWeight: isToday || isSelected
                      ? FontWeight.w800
                      : FontWeight.w500,
                ),
              ),
            ),
            SizedBox(
              height: 6,
              child: tasks.isEmpty
                  ? null
                  : Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        for (final t in tasks.take(3))
                          Container(
                            width: 5,
                            height: 5,
                            // Off the spacing scale on purpose: a 5dp dot needs
                            // a 1dp gutter — AppSpacing.xxs would fuse three
                            // dots into a bar inside a dense month cell.
                            margin: const EdgeInsets.symmetric(horizontal: 1),
                            decoration: BoxDecoration(
                              color: _dotColor(t),
                              shape: BoxShape.circle,
                            ),
                          ),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Modern agenda card: time rail + title, linked customer/lead, assignee and
/// status/priority chips. Tap-through to the task detail.
class _AgendaCard extends StatelessWidget {
  const _AgendaCard({required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final done = task.status == TaskStatus.completed;
    final accent = _DayCell._dotColor(task);

    return Material(
      color: scheme.surfaceContainerLow,
      borderRadius: BorderRadius.circular(AppRadii.lg),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.pushNamed(
          RouteNames.taskDetail,
          pathParameters: {'id': task.taskId},
        ),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppRadii.lg),
            border: Border.all(
              color: scheme.outlineVariant.withValues(alpha: 0.6),
            ),
          ),
          child: IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Colored accent rail keyed to state/priority.
                Container(width: 4, color: accent),
                const SizedBox(width: AppSpacing.md),
                // Time column.
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
                  child: SizedBox(
                    width: 44,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          Formatters.time(task.anchorDate),
                          style: theme.textTheme.labelLarge?.copyWith(
                            fontWeight: FontWeight.w800,
                            color: task.isOverdue ? AppColors.danger : null,
                          ),
                        ),
                        if (task.endAt != null && task.startAt != null)
                          Text(
                            Formatters.time(task.endAt),
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: scheme.onSurfaceVariant,
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(
                      0,
                      AppSpacing.md,
                      AppSpacing.md,
                      AppSpacing.md,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          task.title,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w700,
                            decoration: done
                                ? TextDecoration.lineThrough
                                : null,
                          ),
                        ),
                        if (task.relatedName != null) ...[
                          const SizedBox(height: 2),
                          Row(
                            children: [
                              Icon(
                                Icons.person_outline_rounded,
                                size: 14,
                                color: scheme.outline,
                              ),
                              const SizedBox(width: AppSpacing.xs),
                              Flexible(
                                child: Text(
                                  task.relatedName!,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: theme.textTheme.bodySmall?.copyWith(
                                    color: scheme.onSurfaceVariant,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                        const SizedBox(height: AppSpacing.sm),
                        Wrap(
                          spacing: AppSpacing.xs,
                          runSpacing: AppSpacing.xs,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            StatusChip(
                              tone: task.status.tone,
                              rawStatus: task.status.wire,
                              dense: true,
                            ),
                            StatusChip(
                              tone: task.priority.tone,
                              rawStatus: task.priority.wire,
                              dense: true,
                            ),
                            if (task.isOverdue)
                              const StatusChip(
                                tone: StatusTone.danger,
                                label: 'Overdue',
                                dense: true,
                              ),
                            if (task.assignedUserName != null)
                              Text(
                                task.assignedUserName!,
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                ),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
