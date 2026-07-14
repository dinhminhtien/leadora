import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../data/task_models.dart';
import 'task_chips.dart';

/// The building blocks of the task detail screen, in the order it stacks them:
/// header → overview → schedule → assignment → related record → contact →
/// description → result.
///
/// Every card renders only what the API actually returns. A card with nothing to
/// say is not rendered at all rather than shown empty — see the `shouldShow`
/// guards, which the screen consults before building.

/// Vertical rhythm between detail cards. One constant, so the page reads as an
/// evenly spaced stack instead of a pile of differently-gapped boxes.
const double kTaskCardGap = AppSpacing.md;

/// Title, related record, and the status/priority/activity chips.
class TaskDetailHeader extends StatelessWidget {
  const TaskDetailHeader({super.key, required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final related = task.relatedName;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          task.title,
          style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
        ),
        if (related != null) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            related,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: scheme.onSurfaceVariant,
            ),
          ),
        ],
        const SizedBox(height: AppSpacing.md),
        TaskChipRow(task: task),
      ],
    );
  }
}

/// Overdue call-out. Only ever built when [Task.isOverdue].
class TaskOverdueBanner extends StatelessWidget {
  const TaskOverdueBanner({super.key, required this.task, required this.canReassign});

  final Task task;

  /// Staff can't hand a task over, so they get told to reschedule, not to resign.
  final bool canReassign;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final advice = canReassign
        ? 'Use Reassign to reschedule it or hand it to someone else.'
        : 'Complete it, or ask your manager to reschedule it.';

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
          const Icon(Icons.error_outline_rounded, size: AppIconSize.lg, color: AppColors.danger),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'This task is overdue',
                  style: theme.textTheme.titleSmall?.copyWith(
                    color: AppColors.danger,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: AppSpacing.xxs),
                Text(
                  'Ended ${Formatters.dateTime(task.endAt)}. $advice',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// What kind of work this is and where it stands. The audit timestamps live here
/// too — they are "about the record", which is what an overview is for.
class TaskOverviewCard extends StatelessWidget {
  const TaskOverviewCard({super.key, required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      title: 'Overview',
      icon: Icons.info_outline_rounded,
      child: Column(
        children: [
          InfoRow(
            label: 'Activity type',
            value: task.activityType.label,
            icon: task.activityType.icon,
          ),
          InfoRow(
            label: 'Status',
            value: Formatters.humanizeEnum(task.status.wire),
            icon: Icons.flaky_rounded,
          ),
          InfoRow(
            label: 'Priority',
            value: Formatters.humanizeEnum(task.priority.wire),
            icon: Icons.flag_outlined,
          ),
          InfoRow(
            label: 'Created',
            value: Formatters.dateTime(task.createdAt),
            icon: Icons.schedule_rounded,
          ),
          if (task.updatedAt != null)
            InfoRow(
              label: 'Last updated',
              value: Formatters.dateTime(task.updatedAt),
              icon: Icons.update_rounded,
            ),
        ],
      ),
    );
  }
}

/// Start → end as a small timeline, with duration and an "active now" marker.
class TaskScheduleCard extends StatelessWidget {
  const TaskScheduleCard({super.key, required this.task});

  final Task task;

  /// Nothing to show when the task was never scheduled.
  static bool shouldShow(Task task) => task.startAt != null || task.endAt != null;

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
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _TimelineRow(
            icon: Icons.play_arrow_rounded,
            label: 'Starts',
            value: task.startAt,
            connected: true,
          ),
          _TimelineRow(
            icon: Icons.flag_rounded,
            label: 'Ends',
            value: task.endAt,
            danger: task.isOverdue,
            connected: false,
          ),
          if (duration != null) ...[
            const SizedBox(height: AppSpacing.sm),
            const Divider(height: 1),
            const SizedBox(height: AppSpacing.md),
            Row(
              children: [
                Icon(Icons.timelapse_rounded, size: AppIconSize.sm, color: scheme.onSurfaceVariant),
                const SizedBox(width: AppSpacing.sm),
                Text(
                  _formatDuration(duration),
                  style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                ),
                if (isActive) ...[
                  const SizedBox(width: AppSpacing.sm),
                  const TaskActiveNowChip(),
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

/// "Active now" — the task's window contains the present moment.
class TaskActiveNowChip extends StatelessWidget {
  const TaskActiveNowChip({super.key});

  @override
  Widget build(BuildContext context) {
    return const TaskStatusChipShell(
      color: AppColors.success,
      icon: Icons.circle,
      label: 'Active now',
    );
  }
}

/// A bare pill used where the semantic chips don't apply.
class TaskStatusChipShell extends StatelessWidget {
  const TaskStatusChipShell({
    super.key,
    required this.color,
    required this.icon,
    required this.label,
  });

  final Color color;
  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final fg = dark
        ? HSLColor.fromColor(color).withLightness(0.75).toColor()
        : HSLColor.fromColor(color).withLightness(0.28).toColor();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: dark ? 0.22 : 0.12),
        borderRadius: BorderRadius.circular(AppRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 10, color: fg),
          const SizedBox(width: AppSpacing.xs),
          Text(
            label,
            style: theme.textTheme.labelSmall?.copyWith(
              color: fg,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

/// One node of the start/end timeline: a dot, an optional connector down to the
/// next node, and the date/time.
class _TimelineRow extends StatelessWidget {
  const _TimelineRow({
    required this.icon,
    required this.label,
    required this.value,
    required this.connected,
    this.danger = false,
  });

  final IconData icon;
  final String label;
  final DateTime? value;
  final bool connected;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final accent = danger ? AppColors.danger : scheme.primary;
    final hasValue = value != null;

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Column(
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  color: accent.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(icon, size: AppIconSize.sm, color: accent),
              ),
              if (connected)
                Expanded(
                  child: Container(
                    width: 2,
                    margin: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
                    color: scheme.outlineVariant.withValues(alpha: 0.6),
                  ),
                ),
            ],
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: connected ? AppSpacing.lg : 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label.toUpperCase(),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.4,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.xxs),
                  Text(
                    hasValue ? Formatters.dateTime(value) : 'Not set',
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                      color: !hasValue
                          ? scheme.onSurfaceVariant
                          : (danger ? AppColors.danger : scheme.onSurface),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Who owns the task and who raised it — grouped, because "who" is one question.
///
/// The API gives us names only; a person's role and department are not on
/// `TaskResponse`, so they are not shown rather than guessed at.
class TaskAssignmentCard extends StatelessWidget {
  const TaskAssignmentCard({super.key, required this.task, required this.isMine});

  final Task task;

  /// Marks the assignee as the signed-in user, which is the single most useful
  /// thing this card can tell a staff member.
  final bool isMine;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      title: 'Assignment',
      icon: Icons.groups_outlined,
      child: Column(
        children: [
          _PersonRow(
            role: 'Assigned to',
            name: task.assignedUserName,
            badge: isMine ? 'You' : null,
          ),
          if (task.createdByName != null) ...[
            const SizedBox(height: AppSpacing.md),
            const Divider(height: 1),
            const SizedBox(height: AppSpacing.md),
            _PersonRow(role: 'Created by', name: task.createdByName),
          ],
        ],
      ),
    );
  }
}

class _PersonRow extends StatelessWidget {
  const _PersonRow({required this.role, required this.name, this.badge});

  final String role;
  final String? name;
  final String? badge;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final displayName = (name == null || name!.trim().isEmpty) ? 'Unassigned' : name!;

    return Row(
      children: [
        AppAvatar(name: displayName, radius: 20),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                role.toUpperCase(),
                style: theme.textTheme.labelSmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.4,
                ),
              ),
              const SizedBox(height: AppSpacing.xxs),
              Row(
                children: [
                  Flexible(
                    child: Text(
                      displayName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodyLarge?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  if (badge != null) ...[
                    const SizedBox(width: AppSpacing.sm),
                    TaskStatusChipShell(
                      color: scheme.primary,
                      icon: Icons.person_rounded,
                      label: badge!,
                    ),
                  ],
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// The deal / customer / lead this task serves, with a route into it.
///
/// Task foreign keys only cover those three, so those are the three kinds
/// handled — one card, three configurations, rather than three layouts.
class TaskRelatedRecordCard extends StatelessWidget {
  const TaskRelatedRecordCard({super.key, required this.task});

  final Task task;

  /// Unlinked tasks get no card at all — an empty "Related to" box is noise.
  static bool shouldShow(Task task) => _RelatedConfig.of(task) != null;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final cfg = _RelatedConfig.of(task);
    if (cfg == null) return const SizedBox.shrink();

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'RELATED TO',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                    letterSpacing: 1.2,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(width: AppSpacing.sm),
              TaskStatusChipShell(
                color: cfg.color,
                icon: cfg.icon,
                label: cfg.typeLabel,
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: cfg.color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(AppRadii.md),
                ),
                child: Icon(cfg.icon, color: cfg.color),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Text(
                  cfg.name,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          for (final row in cfg.rows)
            InfoRow(label: row.$1, value: row.$2, icon: row.$3),
          const SizedBox(height: AppSpacing.md),
          SizedBox(
            width: double.infinity,
            child: FilledButton.tonalIcon(
              onPressed: () => context.push(cfg.route),
              icon: const Icon(Icons.arrow_forward_rounded, size: AppIconSize.md),
              label: Text(cfg.openLabel),
            ),
          ),
        ],
      ),
    );
  }
}

/// Per-kind presentation + navigation target for [TaskRelatedRecordCard].
class _RelatedConfig {
  const _RelatedConfig({
    required this.color,
    required this.icon,
    required this.typeLabel,
    required this.name,
    required this.rows,
    required this.openLabel,
    required this.route,
  });

  final Color color;
  final IconData icon;
  final String typeLabel;
  final String name;
  final List<(String, String, IconData)> rows;
  final String openLabel;
  final String route;

  static _RelatedConfig? of(Task task) {
    if (task.dealId != null) {
      return _RelatedConfig(
        color: AppColors.success,
        icon: Icons.handshake_rounded,
        typeLabel: 'Deal',
        name: task.dealName ?? 'Deal',
        rows: [
          ('Stage', Formatters.humanizeEnum(task.dealStage), Icons.timeline_rounded),
          ('Value', Formatters.money(task.dealValue), Icons.payments_outlined),
          ('Customer', task.dealCustomerName ?? '—', Icons.badge_outlined),
          ('Owner', task.dealOwnerName ?? '—', Icons.person_outline_rounded),
        ],
        openLabel: 'Open deal',
        route: Routes.dealDetailPath(task.dealId!),
      );
    }
    if (task.customerId != null) {
      return _RelatedConfig(
        color: AppColors.info,
        icon: Icons.badge_rounded,
        typeLabel: 'Customer',
        name: task.customerName ?? 'Customer',
        rows: [
          ('Company', task.customerCompanyName ?? '—', Icons.business_outlined),
          ('Phone', task.customerPhone ?? '—', Icons.phone_outlined),
          ('Email', task.customerEmail ?? '—', Icons.mail_outline_rounded),
        ],
        openLabel: 'Open customer',
        route: Routes.customerDetailPath(task.customerId!),
      );
    }
    if (task.leadId != null) {
      return _RelatedConfig(
        color: AppColors.accentPurple,
        icon: Icons.person_search_rounded,
        typeLabel: 'Lead',
        name: task.leadName ?? 'Lead',
        rows: [
          ('Company', task.leadCompanyName ?? '—', Icons.business_outlined),
          ('Status', Formatters.humanizeEnum(task.leadStatus), Icons.flag_outlined),
          ('Source', Formatters.humanizeEnum(task.leadSource), Icons.input_rounded),
          ('Owner', task.leadOwnerName ?? '—', Icons.person_outline_rounded),
        ],
        openLabel: 'Open lead',
        route: Routes.leadDetailPath(task.leadId!),
      );
    }
    return null;
  }
}

/// Who to actually call. Merges the linked record's contact details with the
/// task's own primary-contact override into a single card, so a rep looking for
/// a phone number has exactly one place to look.
class TaskContactCard extends StatelessWidget {
  const TaskContactCard({super.key, required this.task});

  final Task task;

  static bool shouldShow(Task task) =>
      task.contactName != null ||
      task.contactPhone != null ||
      task.contactEmail != null ||
      task.primaryContactName != null ||
      task.primaryContactPhone != null;

  /// The task's own override wins over the linked record's details — it is the
  /// more specific answer to "who do I ring about this task".
  String? get _name => task.primaryContactName ?? task.contactName;
  String? get _phone => task.primaryContactPhone ?? task.contactPhone;
  String? get _email => task.contactEmail;

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
    final scheme = Theme.of(context).colorScheme;
    final phone = _phone;
    final email = _email;
    // Flag the override so it's clear the number isn't the record's default.
    final isOverride = task.primaryContactName != null || task.primaryContactPhone != null;

    return SectionCard(
      title: 'Contact information',
      icon: Icons.contact_phone_outlined,
      trailing: isOverride
          ? const TaskStatusChipShell(
              color: AppColors.warning,
              icon: Icons.push_pin_rounded,
              label: 'Primary',
            )
          : null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InfoRow(label: 'Name', value: _name, icon: Icons.person_outline_rounded),
          if (phone != null)
            InfoRow(label: 'Phone', value: phone, icon: Icons.phone_outlined),
          if (email != null)
            InfoRow(label: 'Email', value: email, icon: Icons.mail_outline_rounded),
          if (task.contactCompany != null)
            InfoRow(
              label: 'Company',
              value: task.contactCompany,
              icon: Icons.business_outlined,
            ),
          if (phone != null || email != null) ...[
            const SizedBox(height: AppSpacing.md),
            Row(
              children: [
                if (phone != null)
                  Expanded(
                    child: FilledButton.tonalIcon(
                      style: FilledButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                        backgroundColor: scheme.primaryContainer,
                        foregroundColor: scheme.onPrimaryContainer,
                      ),
                      onPressed: () => _launch(context, Uri(scheme: 'tel', path: phone)),
                      icon: const Icon(Icons.call_rounded, size: AppIconSize.md),
                      label: const Text('Call'),
                    ),
                  ),
                if (phone != null && email != null) const SizedBox(width: AppSpacing.md),
                if (email != null)
                  Expanded(
                    child: FilledButton.tonalIcon(
                      style: FilledButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                        backgroundColor: scheme.secondaryContainer,
                        foregroundColor: scheme.onSecondaryContainer,
                      ),
                      onPressed: () => _launch(context, Uri(scheme: 'mailto', path: email)),
                      icon: const Icon(Icons.mail_rounded, size: AppIconSize.md),
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

/// The brief. Full width, unconstrained height — it is prose, so let it breathe.
class TaskDescriptionCard extends StatelessWidget {
  const TaskDescriptionCard({super.key, required this.task});

  final Task task;

  static bool shouldShow(Task task) =>
      task.description != null && task.description!.trim().isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SectionCard(
      title: 'Description',
      icon: Icons.description_outlined,
      child: Text(
        task.description!.trim(),
        style: theme.textTheme.bodyMedium?.copyWith(height: 1.5),
      ),
    );
  }
}

/// What happened. Tinted success-green because it is the outcome of the work.
class TaskResultNoteCard extends StatelessWidget {
  const TaskResultNoteCard({super.key, required this.task});

  final Task task;

  static bool shouldShow(Task task) =>
      task.resultNote != null && task.resultNote!.trim().isNotEmpty;

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
          Row(
            children: [
              Icon(Icons.fact_check_outlined, size: AppIconSize.sm, color: fg),
              const SizedBox(width: AppSpacing.sm),
              Text(
                'RESULT / NOTES',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: fg,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            task.resultNote!.trim(),
            style: theme.textTheme.bodyMedium?.copyWith(color: fg, height: 1.5),
          ),
        ],
      ),
    );
  }
}
