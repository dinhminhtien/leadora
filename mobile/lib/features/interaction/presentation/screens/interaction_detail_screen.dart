import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/interaction_models.dart';
import '../providers/interaction_providers.dart';

/// View Interaction Detail on Mobile — the full record for one logged
/// interaction plus its audit history, with an entry point to Edit.
///
/// Reached by tapping a card in [InteractionTimelineScreen]. The pushing
/// screen passes the already-loaded [InteractionTimelineEntry] as `extra` so
/// the header renders instantly; the provider then refreshes it and is the
/// source of truth for edits.
class InteractionDetailScreen extends ConsumerWidget {
  const InteractionDetailScreen({super.key, required this.id, this.initial});

  final String id;
  final InteractionTimelineEntry? initial;

  void _openEdit(BuildContext context, WidgetRef ref, InteractionTimelineEntry entry) async {
    final changed = await context.pushNamed<bool>(
      RouteNames.editInteraction,
      pathParameters: {'id': entry.id},
      extra: entry,
    );
    if (changed == true) {
      ref.invalidate(interactionDetailProvider(id));
      ref.invalidate(interactionAuditLogsProvider(id));
      final lt = entry.linkedType;
      if (lt != null && entry.linkedId != null && lt != 'N/A') {
        ref.invalidate(
          interactionTimelineProvider((linkedType: lt, linkedId: entry.linkedId!)),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(interactionDetailProvider(id));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Interaction'),
        actions: [
          // Only offer Edit once we have an entry to hand to the editor.
          async.maybeWhen(
            data: (entry) => IconButton(
              tooltip: 'Edit',
              icon: const Icon(Icons.edit_outlined),
              onPressed: () => _openEdit(context, ref, entry),
            ),
            orElse: () => const SizedBox.shrink(),
          ),
        ],
      ),
      body: AsyncValueView<InteractionTimelineEntry>(
        value: initial != null && async.isLoading
            ? AsyncValue.data(initial!)
            : async,
        onRetry: () => ref.invalidate(interactionDetailProvider(id)),
        data: (entry) => RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(interactionDetailProvider(id));
            ref.invalidate(interactionAuditLogsProvider(id));
          },
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.lg,
              AppSpacing.md,
              AppSpacing.lg,
              AppSpacing.xxxl,
            ),
            children: [
              _DetailHeaderCard(entry: entry),
              const SizedBox(height: AppSpacing.lg),
              _AuditLogCard(interactionId: id),
            ],
          ),
        ),
      ),
    );
  }
}

class _DetailHeaderCard extends StatelessWidget {
  const _DetailHeaderCard({required this.entry});

  final InteractionTimelineEntry entry;

  static IconData _linkedIcon(String? type) => switch (type) {
    'lead' => Icons.person_search_outlined,
    'customer' => Icons.badge_outlined,
    'deal' => Icons.handshake_outlined,
    _ => Icons.link_off_rounded,
  };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(AppSpacing.md),
                decoration: BoxDecoration(
                  color: scheme.primary.withValues(alpha: 0.10),
                  borderRadius: BorderRadius.circular(AppRadii.md),
                ),
                child: Icon(entry.type.icon, color: scheme.primary),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    StatusChip(
                      tone: entry.type.tone,
                      label: entry.type.label,
                      icon: entry.type.icon,
                    ),
                    const SizedBox(height: AppSpacing.xs),
                    Text(
                      Formatters.relative(entry.occurredAt ?? entry.createdAt),
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          Text(
            entry.description,
            style: theme.textTheme.bodyLarge?.copyWith(height: 1.4),
          ),
          const Divider(height: AppSpacing.xxl),
          InfoRow(
            label: 'Logged by',
            value: entry.agentName,
            icon: Icons.person_outline_rounded,
          ),
          InfoRow(
            label: 'Related to',
            value: (entry.linkedName == null || entry.linkedName == 'N/A')
                ? '—'
                : '${entry.linkedName} · ${Formatters.humanizeEnum(entry.linkedType)}',
            icon: _linkedIcon(entry.linkedType),
          ),
          InfoRow(
            label: 'Occurred',
            value: Formatters.dateTime(entry.occurredAt),
            icon: Icons.event_outlined,
          ),
          InfoRow(
            label: 'Logged at',
            value: Formatters.dateTime(entry.createdAt),
            icon: Icons.schedule_outlined,
          ),
        ],
      ),
    );
  }
}

/// Interaction History / audit trail — CREATED + per-field UPDATED entries.
class _AuditLogCard extends ConsumerWidget {
  const _AuditLogCard({required this.interactionId});

  final String interactionId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final async = ref.watch(interactionAuditLogsProvider(interactionId));

    return SectionCard(
      title: 'History',
      icon: Icons.history_rounded,
      child: async.when(
        loading: () => const Padding(
          padding: EdgeInsets.symmetric(vertical: AppSpacing.md),
          child: Center(
            child: SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          ),
        ),
        error: (_, _) => Text(
          'Could not load history.',
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        data: (logs) => logs.isEmpty
            ? Text(
                'No changes recorded yet.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              )
            : Column(
                children: [
                  for (var i = 0; i < logs.length; i++) ...[
                    _AuditRow(log: logs[i]),
                    if (i != logs.length - 1)
                      const Divider(height: AppSpacing.xl),
                  ],
                ],
              ),
      ),
    );
  }
}

class _AuditRow extends StatelessWidget {
  const _AuditRow({required this.log});

  final InteractionAuditLog log;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final isCreate = log.action.toUpperCase() == 'CREATED';
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          isCreate ? Icons.add_circle_outline_rounded : Icons.edit_outlined,
          size: AppIconSize.sm,
          color: isCreate ? scheme.primary : scheme.tertiary,
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      isCreate
                          ? 'Created'
                          : 'Updated ${Formatters.humanizeEnum(log.fieldName)}',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Text(
                    Formatters.relative(log.timestamp),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
              if (!isCreate && (log.oldValue != null || log.newValue != null))
                Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Text(
                    '${log.oldValue ?? '—'}  →  ${log.newValue ?? '—'}',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ),
              if (log.changedByName != null)
                Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Text(
                    'by ${log.changedByName}',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
