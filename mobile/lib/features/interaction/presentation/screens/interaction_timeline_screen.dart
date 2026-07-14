import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_filter_chip.dart';
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
  ConsumerState<InteractionTimelineScreen> createState() =>
      _InteractionTimelineScreenState();
}

class _InteractionTimelineScreenState
    extends ConsumerState<InteractionTimelineScreen> {
  InteractionType? _filter;

  InteractionTimelineArg get _arg =>
      (linkedType: widget.linkedType, linkedId: widget.linkedId);

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(interactionTimelineProvider(_arg));

    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.linkedName?.trim().isNotEmpty == true
              ? widget.linkedName!
              : 'Interaction timeline',
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push(
          Routes.logInteractionPath(
            widget.linkedType,
            widget.linkedId,
            name: widget.linkedName,
          ),
        ),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Log interaction'),
      ),
      body: Column(
        children: [
          AppFilterChipBar(
            children: [
              AppFilterChip(
                label: 'All',
                selected: _filter == null,
                onTap: () => setState(() => _filter = null),
              ),
              for (final t in InteractionType.values)
                AppFilterChip(
                  label: t.label,
                  selected: _filter == t,
                  onTap: () => setState(() => _filter = t),
                ),
            ],
          ),
          Expanded(
            child: AsyncValueView<List<InteractionTimelineEntry>>(
              value: async,
              onRetry: () =>
                  ref.invalidate(interactionTimelineProvider(_arg)),
              isEmpty: (items) => _visible(items).isEmpty,
              empty: const EmptyState(
                icon: Icons.forum_outlined,
                title: 'No interactions yet',
                message:
                    'Calls, emails, meetings, and notes will show up here.',
              ),
              data: (items) => RefreshIndicator(
                onRefresh: () async =>
                    ref.invalidate(interactionTimelineProvider(_arg)),
                child: ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.lg,
                    AppSpacing.sm,
                    AppSpacing.lg,
                    AppSpacing.fabClearance,
                  ),
                  itemCount: _visible(items).length,
                  separatorBuilder: (_, _) =>
                      const SizedBox(height: AppSpacing.sm),
                  itemBuilder: (context, index) => _InteractionCard(
                    entry: _visible(items)[index],
                    onTap: () => _openDetail(_visible(items)[index]),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  List<InteractionTimelineEntry> _visible(
    List<InteractionTimelineEntry> items,
  ) => _filter == null ? items : items.where((e) => e.type == _filter).toList();

  Future<void> _openDetail(InteractionTimelineEntry entry) async {
    final changed = await context.pushNamed<bool>(
      RouteNames.interactionDetail,
      pathParameters: {'id': entry.id},
      extra: entry,
    );
    // An edit made from the detail screen returns true — refresh the timeline.
    if (changed == true) ref.invalidate(interactionTimelineProvider(_arg));
  }
}

class _InteractionCard extends StatelessWidget {
  const _InteractionCard({required this.entry, this.onTap});

  final InteractionTimelineEntry entry;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: SectionCard(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                entry.type.icon,
                size: AppIconSize.md,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  entry.type.label,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              Text(
                Formatters.relative(entry.occurredAt ?? entry.createdAt),
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            entry.description,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: AppSpacing.sm),
          Row(
            children: [
              Icon(
                Icons.person_outline_rounded,
                size: AppIconSize.xs,
                color: theme.colorScheme.outline,
              ),
              const SizedBox(width: AppSpacing.xs),
              Expanded(
                child: Text(
                  entry.agentName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
              Icon(
                Icons.chevron_right_rounded,
                size: AppIconSize.md,
                color: theme.colorScheme.outline,
              ),
            ],
          ),
        ],
      ),
      ),
    );
  }
}
