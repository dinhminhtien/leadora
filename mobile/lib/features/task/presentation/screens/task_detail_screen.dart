import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/task_models.dart';
import '../../data/task_repository.dart';
import '../providers/task_providers.dart';

/// UC-24.17 View Task Detail + UC-24.5 Update Task Progress.
///
/// Mirrors the web `TaskDetailDrawer` section-for-section — badges, overdue
/// banner, schedule (with duration + active indicator), people, linked-entity
/// contact card with call/email quick actions, primary contact, description,
/// result note and audit timestamps — reorganized as stacked cards with a
/// sticky bottom action bar for the primary transitions.
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
    final task = async.valueOrNull;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Task detail'),
        actions: [
          if (task != null)
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_vert_rounded),
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
              itemBuilder: (context) => const [
                PopupMenuItem(
                  value: 'edit',
                  child: ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.edit_outlined),
                    title: Text('Edit task'),
                  ),
                ),
                PopupMenuItem(
                  value: 'resign',
                  child: ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.swap_horiz_rounded),
                    title: Text('Resign (hand over)'),
                  ),
                ),
              ],
            ),
        ],
      ),
      // Sticky bottom action bar — primary transition always within thumb
      // reach; hidden once the task is closed.
      bottomNavigationBar: task != null && task.isOpen
          ? _StickyActions(
              onComplete: () => _complete(context, ref, task),
              onCancel: () => _cancel(context, ref, task),
            )
          : null,
      body: AsyncValueView<Task>(
        value: async,
        onRetry: () => ref.invalidate(taskDetailProvider(taskId)),
        data: (task) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(taskDetailProvider(taskId)),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(
                AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.xxxl),
            children: [
              Text(task.title,
                  style: Theme.of(context)
                      .textTheme
                      .titleLarge
                      ?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: AppSpacing.md),
              Wrap(
                spacing: AppSpacing.sm,
                runSpacing: AppSpacing.sm,
                children: [
                  StatusChip(tone: task.status.tone, rawStatus: task.status.wire),
                  StatusChip(
                      tone: task.priority.tone,
                      label:
                          '${Formatters.humanizeEnum(task.priority.wire)} priority'),
                  if (task.isOverdue)
                    const StatusChip(
                        tone: StatusTone.danger,
                        label: 'Overdue',
                        icon: Icons.warning_amber_rounded),
                  if (task.dealId != null)
                    const StatusChip(
                        tone: StatusTone.success,
                        label: 'Linked deal',
                        icon: Icons.handshake_outlined),
                ],
              ),
              if (task.isOverdue) ...[
                const SizedBox(height: AppSpacing.lg),
                _OverdueBanner(task: task),
              ],
              const SizedBox(height: AppSpacing.lg),
              _ScheduleCard(task: task),
              const SizedBox(height: AppSpacing.md),
              _PeopleCard(task: task),
              if (task.contactName != null ||
                  task.contactPhone != null ||
                  task.contactEmail != null ||
                  task.contactCompany != null) ...[
                const SizedBox(height: AppSpacing.md),
                _ContactCard(task: task),
              ],
              if (task.primaryContactName != null ||
                  task.primaryContactPhone != null) ...[
                const SizedBox(height: AppSpacing.md),
                _PrimaryContactCard(task: task),
              ],
              if (task.description != null &&
                  task.description!.trim().isNotEmpty) ...[
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  title: 'Description',
                  icon: Icons.description_outlined,
                  child: Text(task.description!),
                ),
              ],
              if (task.resultNote != null &&
                  task.resultNote!.trim().isNotEmpty) ...[
                const SizedBox(height: AppSpacing.md),
                _ResultNoteCard(note: task.resultNote!),
              ],
              const SizedBox(height: AppSpacing.lg),
              _MetaFooter(task: task),
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
    final note = await _askResultNote(context,
        title: 'Cancel task', hint: 'Reason (optional)');
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
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel')),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
  }
}

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
              AppSpacing.lg, AppSpacing.md, AppSpacing.lg, AppSpacing.md),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: onCancel,
                  icon: const Icon(Icons.cancel_outlined, size: 20),
                  label: const Text('Cancel task'),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                flex: 2,
                child: FilledButton.icon(
                  onPressed: onComplete,
                  icon: const Icon(Icons.check_circle_outline_rounded, size: 20),
                  label: const Text('Mark complete'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _OverdueBanner extends StatelessWidget {
  const _OverdueBanner({required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.danger.withValues(alpha: dark ? 0.18 : 0.08),
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: AppColors.danger.withValues(alpha: 0.35)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.error_outline_rounded,
              size: 20, color: AppColors.danger),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('This task is overdue',
                    style: theme.textTheme.titleSmall?.copyWith(
                        color: AppColors.danger, fontWeight: FontWeight.w700)),
                const SizedBox(height: 2),
                Text(
                  'Ended ${Formatters.dateTime(task.endAt)}. Use Resign to reschedule and hand over.',
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Start → End with duration and an "active now" pulse, like the web drawer.
class _ScheduleCard extends StatelessWidget {
  const _ScheduleCard({required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    Duration? duration;
    var isActive = false;
    if (task.startAt != null && task.endAt != null) {
      duration = task.endAt!.difference(task.startAt!);
      final now = DateTime.now();
      isActive = !task.isOverdue &&
          !task.startAt!.isAfter(now) &&
          !task.endAt!.isBefore(now);
    }

    return SectionCard(
      title: 'Schedule',
      icon: Icons.event_outlined,
      child: task.startAt == null && task.endAt == null
          ? Text('No schedule set',
              style: theme.textTheme.bodyMedium
                  ?.copyWith(color: scheme.onSurfaceVariant))
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: _ScheduleSlot(
                          label: 'Start at', value: task.startAt),
                    ),
                    Padding(
                      padding: const EdgeInsets.only(top: AppSpacing.xl),
                      child: Icon(Icons.arrow_forward_rounded,
                          size: 18, color: scheme.outline),
                    ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: _ScheduleSlot(
                        label: 'End at',
                        value: task.endAt,
                        danger: task.isOverdue,
                      ),
                    ),
                  ],
                ),
                if (duration != null) ...[
                  const SizedBox(height: AppSpacing.md),
                  const Divider(),
                  const SizedBox(height: AppSpacing.sm),
                  Row(
                    children: [
                      Text('Duration: ${_formatDuration(duration)}',
                          style: theme.textTheme.bodySmall
                              ?.copyWith(color: scheme.onSurfaceVariant)),
                      if (isActive) ...[
                        const SizedBox(width: AppSpacing.sm),
                        const StatusChip(
                            tone: StatusTone.success,
                            label: 'Active now',
                            icon: Icons.circle,
                            dense: true),
                      ],
                    ],
                  ),
                ],
              ],
            ),
    );
  }

  static String _formatDuration(Duration d) {
    final h = d.inHours;
    final m = d.inMinutes.remainder(60);
    if (h > 0 && m > 0) return '${h}h ${m}m';
    if (h > 0) return '${h}h';
    return '${m}m';
  }
}

class _ScheduleSlot extends StatelessWidget {
  const _ScheduleSlot({required this.label, this.value, this.danger = false});

  final String label;
  final DateTime? value;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final color = danger ? AppColors.danger : scheme.onSurface;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label.toUpperCase(),
            style: theme.textTheme.labelSmall?.copyWith(
                color: scheme.onSurfaceVariant,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.4)),
        const SizedBox(height: AppSpacing.xs),
        Text(Formatters.date(value),
            style: theme.textTheme.titleSmall
                ?.copyWith(fontWeight: FontWeight.w700, color: color)),
        Text(value == null ? '' : Formatters.time(value),
            style: theme.textTheme.bodySmall?.copyWith(
                color: danger ? AppColors.danger : scheme.onSurfaceVariant)),
      ],
    );
  }
}

/// Assigned to / created by / related entity, matching the web info grid.
class _PeopleCard extends StatelessWidget {
  const _PeopleCard({required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final entityType = task.leadId != null
        ? 'Lead'
        : task.customerId != null
            ? 'Customer'
            : task.dealId != null
                ? 'Deal'
                : 'General';
    return SectionCard(
      title: 'People & context',
      icon: Icons.groups_outlined,
      child: Column(
        children: [
          InfoRow(
              label: 'Assigned to',
              value: task.assignedUserName,
              icon: Icons.person_outline_rounded),
          InfoRow(
              label: 'Created by',
              value: task.createdByName,
              icon: Icons.edit_note_rounded),
          InfoRow(
              label: 'Related to',
              value: task.relatedName,
              icon: Icons.link_rounded),
          InfoRow(
              label: 'Entity type',
              value: entityType,
              icon: Icons.category_outlined),
          if (task.dealName != null)
            InfoRow(
                label: 'Deal',
                value: task.dealName,
                icon: Icons.handshake_outlined),
        ],
      ),
    );
  }
}

/// Linked lead/customer contact info with tap-to-call and tap-to-email.
class _ContactCard extends StatelessWidget {
  const _ContactCard({required this.task});

  final Task task;

  Future<void> _launch(BuildContext context, Uri uri) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!ok) throw Exception('unsupported');
    } catch (_) {
      messenger.showSnackBar(
        SnackBar(content: Text('Could not open ${uri.scheme} link')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final isLead = task.leadId != null;
    final phone = task.contactPhone;
    final email = task.contactEmail;

    return SectionCard(
      title: 'Contact',
      icon: Icons.contact_phone_outlined,
      trailing: StatusChip(
        tone: isLead ? StatusTone.info : StatusTone.success,
        label: isLead ? 'Lead' : 'Customer',
        dense: true,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InfoRow(
              label: 'Name',
              value: task.contactName,
              icon: Icons.person_outline_rounded),
          if (phone != null)
            InfoRow(label: 'Phone', value: phone, icon: Icons.phone_outlined),
          if (email != null)
            InfoRow(label: 'Email', value: email, icon: Icons.mail_outline_rounded),
          if (task.contactCompany != null)
            InfoRow(
                label: 'Company',
                value: task.contactCompany,
                icon: Icons.business_outlined),
          if (phone != null || email != null) ...[
            const SizedBox(height: AppSpacing.md),
            Row(
              children: [
                if (phone != null)
                  Expanded(
                    child: FilledButton.tonalIcon(
                      style: FilledButton.styleFrom(
                        minimumSize: const Size.fromHeight(44),
                        backgroundColor: scheme.primaryContainer,
                        foregroundColor: scheme.onPrimaryContainer,
                      ),
                      onPressed: () =>
                          _launch(context, Uri(scheme: 'tel', path: phone)),
                      icon: const Icon(Icons.call_rounded, size: 18),
                      label: const Text('Call'),
                    ),
                  ),
                if (phone != null && email != null)
                  const SizedBox(width: AppSpacing.md),
                if (email != null)
                  Expanded(
                    child: FilledButton.tonalIcon(
                      style: FilledButton.styleFrom(
                        minimumSize: const Size.fromHeight(44),
                        backgroundColor: scheme.secondaryContainer,
                        foregroundColor: scheme.onSecondaryContainer,
                      ),
                      onPressed: () =>
                          _launch(context, Uri(scheme: 'mailto', path: email)),
                      icon: const Icon(Icons.mail_rounded, size: 18),
                      label: const Text('Email'),
                    ),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

/// Manual contact override — the amber card on the web drawer.
class _PrimaryContactCard extends StatelessWidget {
  const _PrimaryContactCard({required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final fg = dark
        ? HSLColor.fromColor(AppColors.warning).withLightness(0.75).toColor()
        : HSLColor.fromColor(AppColors.warning).withLightness(0.28).toColor();
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.warning.withValues(alpha: dark ? 0.16 : 0.08),
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: AppColors.warning.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('PRIMARY CONTACT',
              style: theme.textTheme.labelSmall?.copyWith(
                  color: fg, fontWeight: FontWeight.w700, letterSpacing: 0.5)),
          const SizedBox(height: AppSpacing.sm),
          Wrap(
            spacing: AppSpacing.lg,
            runSpacing: AppSpacing.xs,
            children: [
              if (task.primaryContactName != null)
                _iconText(theme, Icons.person_outline_rounded,
                    task.primaryContactName!, fg),
              if (task.primaryContactPhone != null)
                _iconText(theme, Icons.phone_outlined,
                    task.primaryContactPhone!, fg),
            ],
          ),
        ],
      ),
    );
  }

  Widget _iconText(ThemeData theme, IconData icon, String text, Color fg) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: fg),
        const SizedBox(width: AppSpacing.xs),
        Text(text,
            style: theme.textTheme.bodySmall
                ?.copyWith(color: fg, fontWeight: FontWeight.w600)),
      ],
    );
  }
}

/// Result note — the emerald outcome card on the web drawer.
class _ResultNoteCard extends StatelessWidget {
  const _ResultNoteCard({required this.note});

  final String note;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final fg = dark
        ? HSLColor.fromColor(AppColors.success).withLightness(0.78).toColor()
        : HSLColor.fromColor(AppColors.success).withLightness(0.24).toColor();
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.success.withValues(alpha: dark ? 0.16 : 0.08),
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: AppColors.success.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('RESULT / NOTES',
              style: theme.textTheme.labelSmall?.copyWith(
                  color: fg, fontWeight: FontWeight.w700, letterSpacing: 0.5)),
          const SizedBox(height: AppSpacing.sm),
          Text(note, style: theme.textTheme.bodyMedium?.copyWith(color: fg)),
        ],
      ),
    );
  }
}

/// Created / updated audit line, kept low-emphasis at the end of the page.
class _MetaFooter extends StatelessWidget {
  const _MetaFooter({required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = theme.textTheme.bodySmall
        ?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Divider(),
        const SizedBox(height: AppSpacing.sm),
        Text('Created ${Formatters.dateTime(task.createdAt)}', style: style),
        if (task.updatedAt != null) ...[
          const SizedBox(height: 2),
          Text('Updated ${Formatters.dateTime(task.updatedAt)}', style: style),
        ],
      ],
    );
  }
}
