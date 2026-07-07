import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/reminder_models.dart';
import '../providers/reminder_providers.dart';

/// Maps a reminder's `relatedEntity`/`relatedId` to a screen this app can
/// show — mirrors `NotificationListScreen._relatedRoute`.
String? _relatedRoute(Reminder r) {
  final id = r.relatedId;
  if (id == null || id.isEmpty) return null;
  switch (r.relatedEntity?.toUpperCase()) {
    case 'LEAD':
      return Routes.leadDetailPath(id);
    case 'TASK':
      return Routes.taskDetailPath(id);
    case 'QUOTATION':
      return Routes.quotationDetailPath(id);
    case 'DEAL':
      return Routes.dealDetailPath(id);
    default:
      return null;
  }
}

/// UC-16.1 / UC-16.2 — My reminders on Mobile. View + dismiss; creating,
/// editing, and escalating a reminder stay web-only.
class ReminderListScreen extends ConsumerWidget {
  const ReminderListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(reminderListControllerProvider);
    final controller = ref.read(reminderListControllerProvider.notifier);

    return Scaffold(
      appBar: AppBar(title: const Text('Reminders')),
      body: AsyncValueView<List<Reminder>>(
        value: async,
        onRetry: controller.refresh,
        isEmpty: (list) => list.isEmpty,
        empty: const EmptyState(
          icon: Icons.alarm_outlined,
          title: 'No reminders',
          message: "You're all caught up.",
        ),
        data: (list) => RefreshIndicator(
          onRefresh: controller.refresh,
          child: ListView.separated(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            itemCount: list.length,
            separatorBuilder: (_, _) => const SizedBox(height: 10),
            itemBuilder: (context, index) => _ReminderCard(
              reminder: list[index],
              onDismiss: () => controller.dismiss(list[index]),
            ),
          ),
        ),
      ),
    );
  }
}

class _ReminderCard extends StatelessWidget {
  const _ReminderCard({required this.reminder, required this.onDismiss});

  final Reminder reminder;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final route = _relatedRoute(reminder);

    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: route == null ? null : () => context.push(route),
      child: SectionCard(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          reminder.title,
                          style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      StatusChip(
                        tone: reminder.priority.tone,
                        rawStatus: reminder.priority.wire,
                        dense: true,
                      ),
                    ],
                  ),
                  if (reminder.description != null && reminder.description!.trim().isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      reminder.description!,
                      style: theme.textTheme.bodySmall
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Icon(Icons.schedule_rounded, size: 14, color: theme.colorScheme.outline),
                      const SizedBox(width: 4),
                      Text(
                        Formatters.relative(reminder.remindAt),
                        style: theme.textTheme.labelSmall
                            ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                      const SizedBox(width: 10),
                      StatusChip(tone: reminder.status.tone, rawStatus: reminder.status.wire, dense: true),
                    ],
                  ),
                ],
              ),
            ),
            if (reminder.isActionable) ...[
              const SizedBox(width: 8),
              IconButton(
                tooltip: 'Dismiss',
                icon: const Icon(Icons.check_circle_outline_rounded),
                onPressed: onDismiss,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
