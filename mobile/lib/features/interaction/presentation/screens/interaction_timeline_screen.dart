import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../data/interaction_models.dart';
import '../providers/interaction_providers.dart';

/// View Interaction Timeline on Mobile — full history of calls/emails/
/// meetings/notes logged against one lead, customer, or deal.
class InteractionTimelineScreen extends ConsumerStatefulWidget {
  const InteractionTimelineScreen({
    super.key,
    required this.linkedType,
    required this.linkedId,
    this.linkedName,
  });

  final String linkedType;
  final String linkedId;
  final String? linkedName;

  @override
  ConsumerState<InteractionTimelineScreen> createState() => _InteractionTimelineScreenState();
}

class _InteractionTimelineScreenState extends ConsumerState<InteractionTimelineScreen> {
  InteractionType? _filter;

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(interactionTimelineProvider(widget.linkedId));

    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.linkedName?.trim().isNotEmpty == true ? widget.linkedName! : 'Interaction timeline',
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push(
          Routes.logInteractionPath(widget.linkedType, widget.linkedId, name: widget.linkedName),
        ),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Log interaction'),
      ),
      body: Column(
        children: [
          SizedBox(
            height: 52,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: ChoiceChip(
                    label: const Text('All'),
                    selected: _filter == null,
                    onSelected: (_) => setState(() => _filter = null),
                  ),
                ),
                for (final t in InteractionType.values)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: ChoiceChip(
                      label: Text(t.label),
                      selected: _filter == t,
                      onSelected: (_) => setState(() => _filter = t),
                    ),
                  ),
              ],
            ),
          ),
          Expanded(
            child: AsyncValueView<List<InteractionTimelineEntry>>(
              value: async,
              onRetry: () => ref.invalidate(interactionTimelineProvider(widget.linkedId)),
              isEmpty: (items) => _visible(items).isEmpty,
              empty: const EmptyState(
                icon: Icons.forum_outlined,
                title: 'No interactions yet',
                message: 'Calls, emails, meetings, and notes will show up here.',
              ),
              data: (items) => RefreshIndicator(
                onRefresh: () async => ref.invalidate(interactionTimelineProvider(widget.linkedId)),
                child: ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
                  itemCount: _visible(items).length,
                  separatorBuilder: (_, _) => const SizedBox(height: 10),
                  itemBuilder: (context, index) => _InteractionCard(entry: _visible(items)[index]),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  List<InteractionTimelineEntry> _visible(List<InteractionTimelineEntry> items) =>
      _filter == null ? items : items.where((e) => e.type == _filter).toList();
}

class _InteractionCard extends StatelessWidget {
  const _InteractionCard({required this.entry});

  final InteractionTimelineEntry entry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SectionCard(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(entry.type.icon, size: 18, color: theme.colorScheme.primary),
              const SizedBox(width: 8),
              Expanded(
                child: Text(entry.type.label,
                    style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
              ),
              Text(
                Formatters.relative(entry.occurredAt ?? entry.createdAt),
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(entry.description, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(Icons.person_outline_rounded, size: 14, color: theme.colorScheme.outline),
              const SizedBox(width: 4),
              Text(entry.agentName,
                  style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
            ],
          ),
        ],
      ),
    );
  }
}