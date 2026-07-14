import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../data/task_models.dart';
import '../../data/task_repository.dart';
import '../providers/task_permissions.dart';
import '../providers/task_providers.dart';
import '../widgets/task_detail_cards.dart';

/// UC-24.17 View Task Detail + UC-24.5 Update Task Progress.
///
/// The page is a stack of one-topic cards — overview, schedule, assignment,
/// related record, contact, description, result — each of which is skipped
/// entirely when the task carries no data for it. The two lifecycle transitions
/// live in a pinned bottom bar so they stay in thumb reach on a long page.
///
/// Actions are gated on [TaskPermissions], which mirrors the server policy:
/// anyone who can open a task may edit it (the list is already scoped to what
/// they own), but only a manager may hand it to someone else (BR-18).
class TaskDetailScreen extends ConsumerWidget {
  const TaskDetailScreen({super.key, required this.taskId});

  final String taskId;

  void _invalidate(WidgetRef ref) {
    ref.invalidate(taskDetailProvider(taskId));
    ref.invalidate(taskListControllerProvider);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(taskDetailProvider(taskId));
    final permissions = ref.watch(taskPermissionsProvider);
    final task = async.valueOrNull;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Task detail'),
        actions: [
          if (task != null)
            _TaskActionsMenu(task: task, permissions: permissions),
        ],
      ),
      bottomNavigationBar: task != null && task.isOpen
          ? _StickyActions(
              onComplete: () => _complete(context, ref, task),
              onCancel: () => _cancel(context, ref, task),
            )
          : null,
      body: AsyncValueView<Task>(
        value: async,
        onRetry: () => ref.invalidate(taskDetailProvider(taskId)),
        loading: const DetailSkeleton(),
        data: (task) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(taskDetailProvider(taskId)),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.lg,
              AppSpacing.lg,
              AppSpacing.lg,
              AppSpacing.xxxl,
            ),
            children: [
              TaskDetailHeader(task: task),
              if (task.isOverdue) ...[
                const SizedBox(height: AppSpacing.lg),
                TaskOverdueBanner(
                  task: task,
                  canReassign: permissions.canReassign,
                ),
              ],
              const SizedBox(height: AppSpacing.lg),
              TaskOverviewCard(task: task),
              if (TaskScheduleCard.shouldShow(task)) ...[
                const SizedBox(height: kTaskCardGap),
                TaskScheduleCard(task: task),
              ],
              const SizedBox(height: kTaskCardGap),
              TaskAssignmentCard(
                task: task,
                isMine: permissions.isAssignee(task),
              ),
              if (TaskRelatedRecordCard.shouldShow(task)) ...[
                const SizedBox(height: kTaskCardGap),
                TaskRelatedRecordCard(task: task),
              ],
              if (TaskContactCard.shouldShow(task)) ...[
                const SizedBox(height: kTaskCardGap),
                TaskContactCard(task: task),
              ],
              if (TaskDescriptionCard.shouldShow(task)) ...[
                const SizedBox(height: kTaskCardGap),
                TaskDescriptionCard(task: task),
              ],
              if (TaskResultNoteCard.shouldShow(task)) ...[
                const SizedBox(height: kTaskCardGap),
                TaskResultNoteCard(task: task),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _complete(BuildContext context, WidgetRef ref, Task task) async {
    final note = await _askResultNote(context, title: 'Complete task');
    if (note == null || !context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      if (note.trim().isNotEmpty) {
        await ref.read(taskRepositoryProvider).updateProgress(
              task.taskId,
              status: TaskStatus.completed,
              resultNote: note,
            );
      } else {
        // The dedicated endpoint also settles SLA tracking and cancels reminders.
        await ref.read(taskRepositoryProvider).resolve(task.taskId);
      }
      _invalidate(ref);
      messenger.showSnackBar(const SnackBar(content: Text('Task completed')));
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _cancel(BuildContext context, WidgetRef ref, Task task) async {
    final note = await _askResultNote(
      context,
      title: 'Cancel task',
      hint: 'Reason (optional)',
    );
    if (note == null || !context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(taskRepositoryProvider).updateProgress(
            task.taskId,
            status: TaskStatus.cancelled,
            resultNote: note,
          );
      _invalidate(ref);
      messenger.showSnackBar(const SnackBar(content: Text('Task cancelled')));
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  /// Returns the entered note, or null if the user dismissed the dialog.
  Future<String?> _askResultNote(
    BuildContext context, {
    required String title,
    String hint = 'Result note (optional)',
  }) {
    final controller = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 3,
          decoration: InputDecoration(hintText: hint),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
  }
}

/// Edit / Reassign. Reassign is a manager-only action (BR-18) and a cancelled
/// task can no longer be handed on, so the menu can end up with a single entry —
/// which is exactly what a staff member should see.
class _TaskActionsMenu extends StatelessWidget {
  const _TaskActionsMenu({required this.task, required this.permissions});

  final Task task;
  final TaskPermissions permissions;

  bool get _canReassign =>
      permissions.canReassign && task.status != TaskStatus.cancelled;

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_vert_rounded),
      tooltip: 'Task actions',
      onSelected: (value) {
        switch (value) {
          case 'edit':
            context.pushNamed(
              RouteNames.taskEdit,
              pathParameters: {'id': task.taskId},
              extra: task,
            );
          case 'resign':
            context.pushNamed(
              RouteNames.taskResign,
              pathParameters: {'id': task.taskId},
              extra: task,
            );
        }
      },
      itemBuilder: (context) => [
        const PopupMenuItem(
          value: 'edit',
          child: ListTile(
            contentPadding: EdgeInsets.zero,
            leading: Icon(Icons.edit_outlined),
            title: Text('Edit task'),
          ),
        ),
        if (_canReassign)
          const PopupMenuItem(
            value: 'resign',
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.swap_horiz_rounded),
              title: Text('Reassign (hand over)'),
            ),
          ),
      ],
    );
  }
}

/// Matches the 52dp height the theme gives every button, keeping the icon-only
/// cancel square and flush with the primary action beside it.
const double _cancelButtonSize = 52;

/// Pinned bottom bar with the two lifecycle transitions for an open task.
class _StickyActions extends StatelessWidget {
  const _StickyActions({required this.onComplete, required this.onCancel});

  final VoidCallback onComplete;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: scheme.surface,
        border: Border(
          top: BorderSide(color: scheme.outlineVariant.withValues(alpha: 0.6)),
        ),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.lg,
            AppSpacing.md,
            AppSpacing.lg,
            AppSpacing.md,
          ),
          child: Row(
            children: [
              // Icon-only: at 320dp the labelled variant left ~92dp for
              // "Cancel task", so the label wrapped to a second line and the
              // two buttons no longer shared a baseline. Destructive action is
              // secondary here anyway, so it drops to an icon with a tooltip.
              Tooltip(
                message: 'Cancel task',
                child: OutlinedButton(
                  onPressed: onCancel,
                  style: OutlinedButton.styleFrom(
                    // The shared theme forces a full-width min size; a square
                    // button must opt out, while staying >= 48dp to tap.
                    minimumSize: const Size.square(_cancelButtonSize),
                    fixedSize: const Size.square(_cancelButtonSize),
                    padding: EdgeInsets.zero,
                    foregroundColor: scheme.error,
                    side: BorderSide(color: scheme.error.withValues(alpha: 0.4)),
                  ),
                  child: const Icon(
                    Icons.cancel_outlined,
                    size: AppIconSize.lg,
                    semanticLabel: 'Cancel task',
                  ),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: FilledButton.icon(
                  onPressed: onComplete,
                  icon: const Icon(
                    Icons.check_circle_outline_rounded,
                    size: AppIconSize.lg,
                  ),
                  label: const Text(
                    'Mark complete',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
