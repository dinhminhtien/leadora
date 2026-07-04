import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/task_models.dart';
import '../providers/task_providers.dart';

/// UC-24.16 — Follow-up task list with quick filters and infinite scroll.
class TaskListScreen extends ConsumerStatefulWidget {
  const TaskListScreen({super.key});

  @override
  ConsumerState<TaskListScreen> createState() => _TaskListScreenState();
}

class _TaskListScreenState extends ConsumerState<TaskListScreen> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(() {
      if (_scrollController.position.pixels >=
          _scrollController.position.maxScrollExtent - 400) {
        ref.read(taskListControllerProvider.notifier).loadMore();
      }
    });
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(taskListControllerProvider);
    final controller = ref.read(taskListControllerProvider.notifier);
    final activeFilter = async.valueOrNull?.filter ?? TaskFilter.open;

    return Scaffold(
      appBar: AppBar(title: const Text('Follow-up tasks')),
      body: Column(
        children: [
          SizedBox(
            height: 52,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              children: [
                for (final f in TaskFilter.values)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: ChoiceChip(
                      label: Text(f.label),
                      selected: activeFilter == f,
                      onSelected: (_) => controller.setFilter(f),
                    ),
                  ),
              ],
            ),
          ),
          Expanded(
            child: AsyncValueView<TaskListState>(
              value: async,
              onRetry: controller.refresh,
              isEmpty: (s) => s.items.isEmpty,
              empty: const EmptyState(
                icon: Icons.checklist_rounded,
                title: 'All clear',
                message: 'No tasks match this filter.',
              ),
              data: (s) => RefreshIndicator(
                onRefresh: controller.refresh,
                child: ListView.separated(
                  controller: _scrollController,
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
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
