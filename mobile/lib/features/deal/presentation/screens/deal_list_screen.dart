import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_filter_chip.dart';
import '../../../../shared/widgets/app_search_field.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/list_skeleton.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/deal_models.dart';
import '../providers/deal_providers.dart';

/// How many deals to render before the scroll listener extends the window.
/// `GET /deals` has no server paging, so this pages the already-fetched list to
/// keep the first frame cheap on long pipelines.
const _pageSize = 20;

/// Deal list — search, stage tabs, sort, pull-to-refresh and progressive
/// rendering, with a FAB to create.
class DealListScreen extends ConsumerStatefulWidget {
  const DealListScreen({super.key});

  @override
  ConsumerState<DealListScreen> createState() => _DealListScreenState();
}

class _DealListScreenState extends ConsumerState<DealListScreen> {
  final _scrollController = ScrollController();
  int _visibleCount = _pageSize;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      setState(() => _visibleCount += _pageSize);
    }
  }

  void _onSearchChanged(String term) {
    setState(_resetWindow);
    ref.read(dealSearchProvider.notifier).state = term;
  }

  /// Any change to the filter set must scroll the window back to the top —
  /// otherwise a 200-deal window survives into a 3-deal result.
  void _resetWindow() => _visibleCount = _pageSize;

  Future<void> _refresh() async {
    _resetWindow();
    ref.invalidate(dealListProvider);
    await ref.read(dealListProvider.future);
  }

  Future<void> _openSortSheet() async {
    final current = ref.read(dealSortProvider);
    final picked = await showModalBottomSheet<DealSort>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: RadioGroup<DealSort>(
          groupValue: current,
          onChanged: (value) => Navigator.of(sheetContext).pop(value),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final sort in DealSort.values)
                RadioListTile<DealSort>(value: sort, title: Text(sort.label)),
              const SizedBox(height: AppSpacing.sm),
            ],
          ),
        ),
      ),
    );
    if (picked != null && mounted) {
      setState(_resetWindow);
      ref.read(dealSortProvider.notifier).state = picked;
    }
  }

  void _selectStage(DealStage? stage) {
    setState(_resetWindow);
    ref.read(dealStageFilterProvider.notifier).state = stage;
  }

  @override
  Widget build(BuildContext context) {
    final deals = ref.watch(visibleDealsProvider);
    final counts = ref.watch(dealStageCountsProvider);
    final activeStage = ref.watch(dealStageFilterProvider);
    final hasQuery = ref.watch(dealSearchProvider).isNotEmpty;
    final isFiltered = hasQuery || activeStage != null;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Deals'),
        actions: [
          IconButton(
            tooltip: 'Sort',
            onPressed: _openSortSheet,
            icon: const Icon(Icons.swap_vert_rounded),
          ),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.pushNamed(RouteNames.dealCreate),
        icon: const Icon(Icons.add_business_rounded),
        label: const Text('New deal'),
      ),
      body: Column(
        children: [
          AppSearchField(
            hintText: 'Search deal, contact…',
            onChanged: _onSearchChanged,
          ),
          AppFilterChipBar(
            children: [
              AppFilterChip(
                label: 'All',
                selected: activeStage == null,
                onTap: () => _selectStage(null),
              ),
              for (final stage in DealStage.values)
                AppFilterChip(
                  label: stage.label,
                  count: counts[stage] ?? 0,
                  selected: activeStage == stage,
                  onTap: () => _selectStage(stage),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Expanded(
            child: AsyncValueView<List<Deal>>(
              value: deals,
              onRetry: _refresh,
              loading: ListSkeleton(
                itemBuilder: (_) => _DealCard(deal: _skeletonDeal),
              ),
              isEmpty: (items) => items.isEmpty,
              empty: EmptyState(
                icon: Icons.handshake_outlined,
                title: isFiltered ? 'No matching deals' : 'No deals yet',
                message: isFiltered
                    ? 'Try a different search or stage.'
                    : 'Create your first deal to start tracking the pipeline.',
                actionLabel: isFiltered ? null : 'New deal',
                onAction: isFiltered
                    ? null
                    : () => context.pushNamed(RouteNames.dealCreate),
              ),
              data: (items) {
                final windowed = items.take(_visibleCount).toList();
                final hasMore = items.length > windowed.length;

                return RefreshIndicator(
                  onRefresh: _refresh,
                  child: ListView.separated(
                    controller: _scrollController,
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(
                      AppSpacing.lg,
                      AppSpacing.xs,
                      AppSpacing.lg,
                      AppSpacing.fabClearance,
                    ),
                    itemCount: windowed.length + (hasMore ? 1 : 0),
                    separatorBuilder: (_, _) =>
                        const SizedBox(height: AppSpacing.md),
                    itemBuilder: (context, index) {
                      if (index >= windowed.length) {
                        return const Padding(
                          padding: EdgeInsets.all(AppSpacing.lg),
                          child: Center(child: CircularProgressIndicator()),
                        );
                      }
                      return _DealCard(deal: windowed[index]);
                    },
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _DealCard extends StatelessWidget {
  const _DealCard({required this.deal});

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
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      deal.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  StatusChip(
                    tone: deal.stageTone,
                    label: deal.displayStage,
                    dense: true,
                  ),
                ],
              ),
              if (deal.contactName != null) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(
                  deal.contactName!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
              const SizedBox(height: AppSpacing.md),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      Formatters.money(deal.value),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                        color: scheme.primary,
                      ),
                    ),
                  ),
                  if (deal.expectedClose != null) ...[
                    Icon(
                      Icons.event_rounded,
                      size: AppIconSize.xs,
                      color: scheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: AppSpacing.xs),
                    Text(
                      Formatters.shortDate(deal.expectedClose),
                      style: theme.textTheme.labelMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Dummy row the skeleton renders — same widget, same shape, so the list does
/// not jump when real data lands. `expectedClose` is fixed rather than
/// `DateTime.now()` so the placeholder stays const-constructible.
final _skeletonDeal = Deal(
  id: '',
  title: 'Placeholder deal title',
  status: DealStatus.active,
  stage: DealStage.proposal,
  contactName: 'Placeholder contact',
  value: 100000,
  expectedClose: DateTime(2026),
);
