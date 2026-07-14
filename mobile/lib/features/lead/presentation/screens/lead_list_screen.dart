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
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/lead_models.dart';
import '../providers/lead_providers.dart';

/// Dummy row for the loading skeleton — same widget as a real row so the list
/// keeps its shape when data lands.
const _skeletonLead = Lead(
  leadId: '',
  fullName: 'Placeholder lead name',
  status: LeadStatus.neww,
  companyName: 'Placeholder company',
  phone: '+84 000 000 000',
);

/// UC-24.14 / UC-24.15 — assigned lead list with search, status filter,
/// advanced filters (scope, sort, source, type, created-date window),
/// pull-to-refresh and infinite scroll.
class LeadListScreen extends ConsumerStatefulWidget {
  const LeadListScreen({super.key});

  @override
  ConsumerState<LeadListScreen> createState() => _LeadListScreenState();
}

class _LeadListScreenState extends ConsumerState<LeadListScreen> {
  final _scrollController = ScrollController();

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

  LeadListController get _controller =>
      ref.read(leadListControllerProvider.notifier);

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      _controller.loadMore();
    }
  }

  void _onSearchChanged(String term) =>
      _controller.applyFilters(_controller.filters.copyWith(search: term));

  Future<void> _openFilterSheet() async {
    final result = await showModalBottomSheet<LeadFilters>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _LeadFilterSheet(initial: _controller.filters),
    );
    if (result != null) _controller.applyFilters(result);
  }

  @override
  Widget build(BuildContext context) {
    final asyncState = ref.watch(leadListControllerProvider);
    final filters = asyncState.valueOrNull?.filters ?? const LeadFilters();
    final advancedCount = filters.activeAdvancedCount;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Leads'),
        actions: [
          IconButton(
            tooltip: 'Filters',
            onPressed: _openFilterSheet,
            icon: Badge.count(
              count: advancedCount,
              isLabelVisible: advancedCount > 0,
              child: const Icon(Icons.tune_rounded),
            ),
          ),
          const SizedBox(width: 4),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.pushNamed(RouteNames.leadCreate),
        icon: const Icon(Icons.person_add_alt_1_rounded),
        label: const Text('New lead'),
      ),
      body: Column(
        children: [
          AppSearchField(
            hintText: 'Search name, phone, company…',
            initialValue: filters.search,
            onChanged: _onSearchChanged,
          ),
          AppFilterChipBar(
            children: [
              AppFilterChip(
                label: 'All',
                selected: filters.status == null,
                onTap: () =>
                    _controller.applyFilters(filters.copyWith(status: null)),
              ),
              for (final s in LeadStatus.values)
                AppFilterChip(
                  label: Formatters.humanizeEnum(s.wire),
                  selected: filters.status == s,
                  onTap: () =>
                      _controller.applyFilters(filters.copyWith(status: s)),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Expanded(
            child: AsyncValueView<LeadListState>(
              value: asyncState,
              onRetry: _controller.refresh,
              loading: ListSkeleton(
                separatorHeight: AppSpacing.sm,
                itemBuilder: (_) => _LeadCard(lead: _skeletonLead),
              ),
              isEmpty: (s) => s.items.isEmpty,
              empty: const EmptyState(
                icon: Icons.people_outline_rounded,
                title: 'No leads found',
                message: 'Try clearing filters or create a new lead.',
              ),
              data: (s) => RefreshIndicator(
                onRefresh: _controller.refresh,
                child: ListView.separated(
                  controller: _scrollController,
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.lg,
                    AppSpacing.xs,
                    AppSpacing.lg,
                    AppSpacing.fabClearance,
                  ),
                  itemCount: s.items.length + (s.hasMore ? 1 : 0),
                  separatorBuilder: (_, _) =>
                      const SizedBox(height: AppSpacing.sm),
                  itemBuilder: (context, index) {
                    if (index >= s.items.length) {
                      return const Padding(
                        padding: EdgeInsets.all(AppSpacing.lg),
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    return _LeadCard(lead: s.items[index]);
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Advanced filter editor. Edits a local copy of [initial] and pops with the
/// result on Apply (or with the advanced filters reset on Reset).
class _LeadFilterSheet extends StatefulWidget {
  const _LeadFilterSheet({required this.initial});

  final LeadFilters initial;

  @override
  State<_LeadFilterSheet> createState() => _LeadFilterSheetState();
}

class _LeadFilterSheetState extends State<_LeadFilterSheet> {
  late LeadFilters _draft = widget.initial;
  late final _sourceController = TextEditingController(
    text: widget.initial.source ?? '',
  );

  @override
  void dispose() {
    _sourceController.dispose();
    super.dispose();
  }

  Future<void> _pickDateRange() async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(now.year - 3),
      lastDate: now,
      initialDateRange: _draft.dateFrom != null && _draft.dateTo != null
          ? DateTimeRange(start: _draft.dateFrom!, end: _draft.dateTo!)
          : null,
    );
    if (picked != null) {
      setState(
        () => _draft = _draft.copyWith(
          dateFrom: picked.start,
          dateTo: picked.end,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasDateWindow = _draft.dateFrom != null || _draft.dateTo != null;

    return Padding(
      // Keep the sheet above the keyboard while typing a source.
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.xl,
            0,
            AppSpacing.xl,
            AppSpacing.lg,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Filter leads', style: theme.textTheme.titleMedium),
              const SizedBox(height: 16),
              Text('Show', style: theme.textTheme.labelLarge),
              const SizedBox(height: 8),
              SegmentedButton<LeadScope>(
                segments: [
                  for (final s in LeadScope.values)
                    ButtonSegment(value: s, label: Text(s.label)),
                ],
                selected: {_draft.scope},
                onSelectionChanged: (sel) =>
                    setState(() => _draft = _draft.copyWith(scope: sel.first)),
              ),
              const SizedBox(height: 16),
              Text('Lead type', style: theme.textTheme.labelLarge),
              const SizedBox(height: 8),
              SegmentedButton<bool?>(
                segments: const [
                  ButtonSegment(value: null, label: Text('All')),
                  ButtonSegment(value: false, label: Text('Individual')),
                  ButtonSegment(value: true, label: Text('Corporate')),
                ],
                selected: {_draft.isCorporate},
                onSelectionChanged: (sel) => setState(
                  () => _draft = _draft.copyWith(isCorporate: sel.first),
                ),
              ),
              const SizedBox(height: 16),
              Text('Sort by', style: theme.textTheme.labelLarge),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 0,
                children: [
                  for (final s in LeadSort.values)
                    ChoiceChip(
                      label: Text(s.label),
                      selected: _draft.sort == s,
                      onSelected: (_) =>
                          setState(() => _draft = _draft.copyWith(sort: s)),
                    ),
                ],
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _sourceController,
                onChanged: (v) =>
                    setState(() => _draft = _draft.copyWith(source: v)),
                decoration: const InputDecoration(
                  labelText: 'Source',
                  hintText: 'e.g. Referral, Website, Walk-in',
                  prefixIcon: Icon(Icons.campaign_outlined),
                  isDense: true,
                ),
              ),
              const SizedBox(height: 12),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.date_range_rounded),
                title: Text(
                  hasDateWindow
                      ? '${Formatters.date(_draft.dateFrom)} → ${Formatters.date(_draft.dateTo)}'
                      : 'Created date — any time',
                  style: theme.textTheme.bodyMedium,
                ),
                onTap: _pickDateRange,
                trailing: hasDateWindow
                    ? IconButton(
                        tooltip: 'Clear dates',
                        icon: const Icon(Icons.close_rounded, size: 20),
                        onPressed: () => setState(
                          () => _draft = _draft.copyWith(
                            dateFrom: null,
                            dateTo: null,
                          ),
                        ),
                      )
                    : const Icon(Icons.chevron_right_rounded),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () =>
                          Navigator.of(context).pop(_draft.resetAdvanced()),
                      child: const Text('Reset'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton(
                      onPressed: () => Navigator.of(context).pop(_draft),
                      child: const Text('Apply filters'),
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

class _LeadCard extends StatelessWidget {
  const _LeadCard({required this.lead});

  final Lead lead;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(AppRadii.lg),
      onTap: () => context.pushNamed(
        RouteNames.leadDetail,
        pathParameters: {'id': lead.leadId},
      ),
      child: SectionCard(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          children: [
            AppAvatar(name: lead.fullName, radius: 22),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    lead.fullName,
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    [
                      if (lead.companyName != null &&
                          lead.companyName!.isNotEmpty)
                        lead.companyName,
                      lead.phone ?? lead.email ?? 'No contact',
                    ].whereType<String>().join(' · '),
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                StatusChip(
                  tone: lead.status.tone,
                  rawStatus: lead.status.wire,
                  dense: true,
                ),
                const SizedBox(height: 6),
                Text(
                  Formatters.relative(lead.createdAt),
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.outline,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
