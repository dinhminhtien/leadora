import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../data/interaction_models.dart';
import '../providers/interaction_providers.dart';

/// Drop-in "Interactions" section for a deal/customer/lead detail screen —
/// shows the most recent logged interactions plus quick actions to log a
/// note or meeting summary, and a link to the full timeline.
///
/// Backs Log Customer Interaction Note, Record Customer Meeting Summary, and
/// View Interaction Timeline on Mobile.
class InteractionSummaryCard extends ConsumerWidget {
  const InteractionSummaryCard({
    super.key,
    required this.linkedType,
    required this.linkedId,
    this.linkedName,
  });

  final String linkedType;
  final String linkedId;
  final String? linkedName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(interactionTimelineProvider(linkedId));
    final theme = Theme.of(context);

    return SectionCard(
      title: 'Interactions',
      icon: Icons.forum_outlined,
      trailing: TextButton(
        onPressed: () =>
            context.push(Routes.interactionTimelinePath(linkedType, linkedId, name: linkedName)),
        child: const Text('View all'),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          async.when(
            data: (items) => items.isEmpty
                ? Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Text(
                      'No interactions logged yet.',
                      style: theme.textTheme.bodySmall
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                  )
                : Column(children: [for (final e in items.take(3)) _PreviewTile(entry: e)]),
            loading: () => const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Center(
                child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
              ),
            ),
            error: (_, _) => const SizedBox.shrink(),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => context.push(
                    Routes.logInteractionPath(linkedType, linkedId, name: linkedName, type: 'note'),
                  ),
                  icon: const Icon(Icons.note_add_outlined, size: 18),
                  label: const Text('Log note'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => context.push(
                    Routes.logInteractionPath(linkedType, linkedId, name: linkedName, type: 'meeting'),
                  ),
                  icon: const Icon(Icons.groups_outlined, size: 18),
                  label: const Text('Meeting'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _PreviewTile extends StatelessWidget {
  const _PreviewTile({required this.entry});

  final InteractionTimelineEntry entry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(entry.type.icon, size: 16, color: theme.colorScheme.primary),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              entry.description,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            Formatters.relative(entry.occurredAt ?? entry.createdAt),
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}