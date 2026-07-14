import 'package:flutter/material.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/task_models.dart';

/// The task module's chips.
///
/// Each is a thin, semantic wrapper over the shared [StatusChip] — the module
/// names the *meaning* (status / priority / activity / overdue) and the design
/// system owns the pixels, so a chip can never drift between the list, the
/// detail header and the form.

/// Lifecycle: Open / Completed / Cancelled.
class TaskStatusChip extends StatelessWidget {
  const TaskStatusChip({super.key, required this.status, this.dense = false});

  final TaskStatus status;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    return StatusChip(
      tone: status.tone,
      rawStatus: status.wire,
      dense: dense,
    );
  }
}

/// Urgency: Low / Medium / High.
class TaskPriorityChip extends StatelessWidget {
  const TaskPriorityChip({super.key, required this.priority, this.dense = false});

  final TaskPriority priority;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    return StatusChip(
      tone: priority.tone,
      label: '${Formatters.humanizeEnum(priority.wire)} priority',
      dense: dense,
    );
  }
}

/// Category (Call / Email / Meeting / …). Carries the type's own colour, which
/// is categorical rather than semantic — hence [StatusChip.color] over a tone.
class TaskActivityChip extends StatelessWidget {
  const TaskActivityChip({super.key, required this.type, this.dense = false});

  final TaskActivityType type;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    return StatusChip(
      tone: StatusTone.neutral,
      color: type.color,
      icon: type.icon,
      label: type.label,
      dense: dense,
    );
  }
}

/// Past its end time and still open. Computed by the backend (`isOverdue`).
class TaskOverdueChip extends StatelessWidget {
  const TaskOverdueChip({super.key, this.dense = false});

  final bool dense;

  @override
  Widget build(BuildContext context) {
    return StatusChip(
      tone: StatusTone.danger,
      label: 'Overdue',
      icon: Icons.warning_amber_rounded,
      dense: dense,
    );
  }
}

/// The standard chip set for a task, in one consistent order: what kind of work
/// it is, where it stands, how urgent, and whether it has slipped.
class TaskChipRow extends StatelessWidget {
  const TaskChipRow({super.key, required this.task, this.dense = false});

  final Task task;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.sm,
      children: [
        TaskActivityChip(type: task.activityType, dense: dense),
        TaskStatusChip(status: task.status, dense: dense),
        TaskPriorityChip(priority: task.priority, dense: dense),
        if (task.isOverdue) TaskOverdueChip(dense: dense),
      ],
    );
  }
}
