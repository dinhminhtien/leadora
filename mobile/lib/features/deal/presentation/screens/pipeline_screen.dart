import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_search_field.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/deal_models.dart';
import '../providers/deal_providers.dart';

/// Width of one Kanban column. Narrow enough that a second column peeks in on a
/// 320dp phone, which is what tells the user the board scrolls sideways.
const double _columnWidth = 264;

/// Sales pipeline as a Kanban board over the real `/deals` list, grouped by
/// `stageCode`.
///
/// There is no pipeline endpoint: columns are a client-side grouping, and a
/// drag issues `PUT /deals/{id}` with the target stage. The backend's stage
/// gates still apply — dropping a deal into Proposal without a deal value is
/// rejected, and the card springs back with the server's message.
class PipelineScreen extends ConsumerWidget {
  const PipelineScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(dealListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Pipeline')),
      body: Column(
        children: [
          AppSearchField(
            hintText: 'Search deal, contact…',
            onChanged: (term) =>
                ref.read(dealSearchProvider.notifier).state = term,
          ),
          Expanded(
            child: AsyncValueView<List<Deal>>(
              value: async,
              onRetry: () => ref.invalidate(dealListProvider),
              isEmpty: (deals) => deals.isEmpty,
              empty: EmptyState(
                icon: Icons.view_kanban_outlined,
                title: 'Nothing in the pipeline',
                message: 'Create a deal to see it move through the stages.',
                actionLabel: 'New deal',
                onAction: () => context.pushNamed(RouteNames.dealCreate),
              ),
              data: (deals) => _Board(deals: deals),
            ),
          ),
        ],
      ),
    );
  }
}

class _Board extends ConsumerWidget {
  const _Board({required this.deals});

  final List<Deal> deals;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(
      children: [
        _PipelineMetrics(deals: deals),
        Expanded(
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.md,
              AppSpacing.sm,
              AppSpacing.md,
              AppSpacing.lg,
            ),
            itemCount: DealStage.values.length,
            separatorBuilder: (_, _) => const SizedBox(width: AppSpacing.md),
            itemBuilder: (context, index) {
              final stage = DealStage.values[index];
              return _StageColumn(
                stage: stage,
                deals: deals.where((d) => d.stage == stage).toList(),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// Open-pipeline totals. Won/lost deals are excluded from value and weighted
/// value — counting a lost deal's revenue as "pipeline" would flatter the
/// forecast.
class _PipelineMetrics extends StatelessWidget {
  const _PipelineMetrics({required this.deals});

  final List<Deal> deals;

  @override
  Widget build(BuildContext context) {
    final open = deals
        .where((d) => d.stage != null && !d.stage!.isTerminal)
        .toList();
    final won = deals.where((d) => d.stage == DealStage.closedWon).length;
    final lost = deals.where((d) => d.stage == DealStage.closedLost).length;

    final openValue = open.fold<double>(0, (sum, d) => sum + (d.value ?? 0));
    final weighted = open.fold<double>(
      0,
      (sum, d) => sum + (d.value ?? 0) * (d.probability ?? 0) / 100,
    );

    final closed = won + lost;
    final conversion = closed == 0 ? null : won / closed * 100;
    final avgDeal = open.isEmpty ? null : openValue / open.length;

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.lg,
        vertical: AppSpacing.sm,
      ),
      child: Row(
        children: [
          _Metric(label: 'Open', value: '${open.length}'),
          _Metric(label: 'Pipeline', value: Formatters.compact(openValue)),
          _Metric(label: 'Weighted', value: Formatters.compact(weighted)),
          _Metric(
            label: 'Avg deal',
            value: avgDeal == null ? '—' : Formatters.compact(avgDeal),
          ),
          _Metric(
            label: 'Win rate',
            value: conversion == null
                ? '—'
                : '${conversion.toStringAsFixed(0)}%',
          ),
        ],
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(right: AppSpacing.xl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            value,
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w800,
            ),
          ),
          Text(
            label,
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _StageColumn extends ConsumerStatefulWidget {
  const _StageColumn({required this.stage, required this.deals});

  final DealStage stage;
  final List<Deal> deals;

  @override
  ConsumerState<_StageColumn> createState() => _StageColumnState();
}

class _StageColumnState extends ConsumerState<_StageColumn> {
  bool _busy = false;

  Future<void> _onAccept(Deal deal) async {
    if (deal.stage == widget.stage) return;

    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(dealActionsProvider)
          .update(
            deal.id,
            DealPayload(
              title: deal.title,
              contactName: deal.contactName ?? '',
              stage: widget.stage,
            ),
          );
      messenger.showSnackBar(
        SnackBar(content: Text('Moved to ${widget.stage.label}')),
      );
    } on AppException catch (e) {
      // The backend's stage gates (deal value for Proposal, notes for
      // Negotiation, close date for Won) reject the move with a usable message.
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final total = widget.deals.fold<double>(0, (s, d) => s + (d.value ?? 0));

    return DragTarget<Deal>(
      onWillAcceptWithDetails: (details) => details.data.stage != widget.stage,
      onAcceptWithDetails: (details) => _onAccept(details.data),
      builder: (context, candidate, _) {
        final hovering = candidate.isNotEmpty;
        return AnimatedContainer(
          duration: AppDurations.fast,
          curve: AppCurves.standard,
          width: _columnWidth,
          decoration: BoxDecoration(
            color: hovering
                ? scheme.primaryContainer.withValues(alpha: 0.4)
                : scheme.surfaceContainerLow,
            borderRadius: BorderRadius.circular(AppRadii.lg),
            border: Border.all(
              color: hovering
                  ? scheme.primary
                  : scheme.outlineVariant.withValues(alpha: 0.6),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.all(AppSpacing.md),
                child: Row(
                  children: [
                    Expanded(
                      child: StatusChip(
                        tone: widget.stage.tone,
                        label: widget.stage.label,
                        dense: true,
                      ),
                    ),
                    if (_busy)
                      const SizedBox.square(
                        dimension: 14,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    else
                      Text(
                        '${widget.deals.length}',
                        style: theme.textTheme.labelLarge?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.md,
                  0,
                  AppSpacing.md,
                  AppSpacing.sm,
                ),
                child: Text(
                  Formatters.compact(total),
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ),
              Expanded(
                child: widget.deals.isEmpty
                    ? Center(
                        child: Text(
                          'Drop a deal here',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: scheme.onSurfaceVariant,
                          ),
                        ),
                      )
                    : ListView.separated(
                        padding: const EdgeInsets.fromLTRB(
                          AppSpacing.sm,
                          0,
                          AppSpacing.sm,
                          AppSpacing.sm,
                        ),
                        itemCount: widget.deals.length,
                        separatorBuilder: (_, _) =>
                            const SizedBox(height: AppSpacing.sm),
                        itemBuilder: (context, index) =>
                            _DraggableDealCard(deal: widget.deals[index]),
                      ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _DraggableDealCard extends StatelessWidget {
  const _DraggableDealCard({required this.deal});

  final Deal deal;

  @override
  Widget build(BuildContext context) {
    final card = _DealMiniCard(deal: deal);

    return LongPressDraggable<Deal>(
      data: deal,
      // A long press, not a pan: the column list scrolls vertically and the
      // board scrolls horizontally, so an immediate drag would steal both.
      feedback: Material(
        color: Colors.transparent,
        child: SizedBox(
          width: _columnWidth - AppSpacing.lg,
          child: Opacity(opacity: 0.9, child: card),
        ),
      ),
      childWhenDragging: Opacity(opacity: 0.35, child: card),
      child: card,
    );
  }
}

class _DealMiniCard extends StatelessWidget {
  const _DealMiniCard({required this.deal});

  final Deal deal;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.pushNamed(
          RouteNames.dealDetail,
          pathParameters: {'id': deal.id},
        ),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                deal.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              if (deal.contactName != null) ...[
                const SizedBox(height: AppSpacing.xxs),
                Text(
                  deal.contactName!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
              const SizedBox(height: AppSpacing.sm),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      Formatters.compact(deal.value ?? 0),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelLarge?.copyWith(
                        fontWeight: FontWeight.w700,
                        color: scheme.primary,
                      ),
                    ),
                  ),
                  if (deal.probability != null)
                    Text(
                      '${deal.probability}%',
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
