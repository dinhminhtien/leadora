import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/task_models.dart';
import '../../data/task_repository.dart';
import '../providers/task_providers.dart';

/// UC-24.17 View Task Detail + UC-24.5 Update Task Progress.
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

    return Scaffold(
      appBar: AppBar(title: const Text('Task detail')),
      body: AsyncValueView<Task>(
        value: async,
        onRetry: () => ref.invalidate(taskDetailProvider(taskId)),
        data: (task) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(taskDetailProvider(taskId)),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(task.title,
                        style: Theme.of(context)
                            .textTheme
                            .titleLarge
                            ?.copyWith(fontWeight: FontWeight.w700)),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  StatusChip(tone: task.status.tone, rawStatus: task.status.wire),
                  StatusChip(
                      tone: task.priority.tone,
                      label: '${Formatters.humanizeEnum(task.priority.wire)} priority'),
                  if (task.isOverdue)
                    StatusChip(tone: StatusTone.danger, label: 'Overdue', icon: Icons.warning_amber_rounded),
                ],
              ),
              const SizedBox(height: 16),
              if (task.description != null && task.description!.trim().isNotEmpty) ...[
                SectionCard(
                  title: 'Description',
                  icon: Icons.description_outlined,
                  child: Text(task.description!),
                ),
                const SizedBox(height: 12),
              ],
              SectionCard(
                title: 'Schedule',
                icon: Icons.event_outlined,
                child: Column(
                  children: [
                    InfoRow(label: 'Start', value: Formatters.dateTime(task.startAt)),
                    InfoRow(label: 'Due', value: Formatters.dateTime(task.endAt)),
                    InfoRow(label: 'Assigned to', value: task.assignedUserName),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SectionCard(
                title: 'Related to',
                icon: Icons.link_rounded,
                child: Column(
                  children: [
                    InfoRow(label: 'Lead', value: task.leadName),
                    InfoRow(label: 'Customer', value: task.customerName),
                    InfoRow(label: 'Deal', value: task.dealName),
                    InfoRow(label: 'Contact', value: task.primaryContactName),
                    InfoRow(label: 'Phone', value: task.primaryContactPhone),
                  ],
                ),
              ),
              if (task.resultNote != null && task.resultNote!.trim().isNotEmpty) ...[
                const SizedBox(height: 12),
                SectionCard(
                  title: 'Result note',
                  icon: Icons.notes_rounded,
                  child: Text(task.resultNote!),
                ),
              ],
              const SizedBox(height: 20),
              if (task.isOpen) ...[
                FilledButton.icon(
                  onPressed: () => _complete(context, ref, task),
                  icon: const Icon(Icons.check_circle_outline_rounded),
                  label: const Text('Mark as complete'),
                  style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(50)),
                ),
                const SizedBox(height: 10),
                OutlinedButton.icon(
                  onPressed: () => _cancel(context, ref, task),
                  icon: const Icon(Icons.cancel_outlined),
                  label: const Text('Cancel task'),
                  style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(50)),
                ),
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
        await ref.read(taskRepositoryProvider).resolve(task.taskId);
      }
      _invalidate(ref);
      messenger.showSnackBar(const SnackBar(content: Text('Task completed')));
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _cancel(BuildContext context, WidgetRef ref, Task task) async {
    final note = await _askResultNote(context, title: 'Cancel task', hint: 'Reason (optional)');
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
  Future<String?> _askResultNote(BuildContext context,
      {required String title, String hint = 'Result note (optional)'}) {
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
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
  }
}
