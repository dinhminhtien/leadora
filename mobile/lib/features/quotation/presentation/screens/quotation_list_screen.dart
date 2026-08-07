import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_filter_chip.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/list_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../auth/data/dto/auth_user.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/quotation_models.dart';
import '../providers/quotation_providers.dart';
import '../widgets/quotation_action_sheets.dart';

/// Dummy row for the loading skeleton — same widget as a real row so the list
/// keeps its shape when data lands.
const _skeletonQuotation = Quotation(
  id: '',
  quoteNo: 'Q-0000-0000',
  status: QuotationStatus.sent,
  dealName: 'Placeholder deal name',
  contactName: 'Placeholder contact',
  totalAmount: 100000,
);

/// "In Progress" — everything still live. "rejected" stays here, not under
/// Completed: Revise is still the primary action on it, so filing it next to
/// truly terminal statuses would hide a quotation staff still need to act on.
/// Mirrors web `ACTIVE_STATUSES` (`QuotationListScreen.tsx`) exactly.
const _activeStatuses = <QuotationStatus>[
  QuotationStatus.draft,
  QuotationStatus.pendingApproval,
  QuotationStatus.approved,
  QuotationStatus.sent,
  QuotationStatus.accepted,
  QuotationStatus.interested,
  QuotationStatus.pendingRevision,
  QuotationStatus.rejected,
];

/// "Completed" — mirrors web `DONE_STATUSES` exactly.
const _doneStatuses = <QuotationStatus>[
  QuotationStatus.converted,
  QuotationStatus.closed,
  QuotationStatus.expired,
];

/// View Quotation Status on Mobile — browsable entry point onto
/// [QuotationDetailScreen]. The backend already owner-scopes the list
/// (SALES sees only quotations they created; MANAGER/ADMIN see all) — the
/// tabs/chips/search below just narrow that client-side.
class QuotationListScreen extends ConsumerStatefulWidget {
  const QuotationListScreen({super.key});

  @override
  ConsumerState<QuotationListScreen> createState() =>
      _QuotationListScreenState();
}

class _QuotationListScreenState extends ConsumerState<QuotationListScreen> {
  /// 0 = In Progress, 1 = Completed — mirrors web's `activeTab` state.
  int _tab = 0;
  QuotationStatus? _filter;
  final _searchController = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<QuotationStatus> get _tabStatuses => _tab == 0 ? _activeStatuses : _doneStatuses;

  List<Quotation> _visible(List<Quotation> items) {
    final query = _query.trim().toLowerCase();
    return items.where((q) {
      if (!_tabStatuses.contains(q.status)) return false;
      if (_filter != null && q.status != _filter) return false;
      if (query.isEmpty) return true;
      return q.quoteNo.toLowerCase().contains(query) ||
          (q.contactName ?? '').toLowerCase().contains(query) ||
          (q.dealName ?? '').toLowerCase().contains(query);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(quotationListProvider);
    final allItems = async.valueOrNull ?? const <Quotation>[];
    final activeCount = allItems.where((q) => _activeStatuses.contains(q.status)).length;
    final doneCount = allItems.where((q) => _doneStatuses.contains(q.status)).length;
    final isManager = ref.watch(currentUserProvider)?.hasAnyRole([AppRoles.manager]) ?? false;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Quotations'),
        actions: [
          IconButton(
            tooltip: 'Generate discount report',
            icon: const Icon(Icons.summarize_outlined),
            onPressed: () => _openDiscountReport(context, ref, allItems),
          ),
          // UC-14.3 Processing Quotations — MANAGER only server-side
          // (`QuotationController.getPendingApprovals`/`processApproval`), so the
          // entry point is gated the same way rather than offering a control that
          // would just come back 403.
          if (isManager) _PendingApprovalsAction(),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.pushNamed(RouteNames.quotationCreate),
        icon: const Icon(Icons.add_rounded),
        label: const Text('New quotation'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.lg,
              AppSpacing.md,
              AppSpacing.lg,
              AppSpacing.sm,
            ),
            child: SegmentedButton<int>(
              segments: [
                ButtonSegment(value: 0, label: Text('In Progress ($activeCount)')),
                ButtonSegment(value: 1, label: Text('Completed ($doneCount)')),
              ],
              selected: {_tab},
              onSelectionChanged: (selection) => setState(() {
                _tab = selection.first;
                // Matches web `handleTabChange`: switching tabs clears the status
                // filter, since the previous selection may not exist in the new tab.
                _filter = null;
              }),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.xs),
            child: TextField(
              controller: _searchController,
              onChanged: (v) => setState(() => _query = v),
              decoration: InputDecoration(
                hintText: 'Search quote #, contact, deal',
                prefixIcon: const Icon(Icons.search_rounded),
                isDense: true,
                suffixIcon: _query.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.close_rounded),
                        onPressed: () => setState(() {
                          _searchController.clear();
                          _query = '';
                        }),
                      ),
              ),
            ),
          ),
          AppFilterChipBar(
            children: [
              AppFilterChip(
                label: 'All',
                selected: _filter == null,
                onTap: () => setState(() => _filter = null),
              ),
              for (final s in _tabStatuses)
                AppFilterChip(
                  label: Formatters.humanizeEnum(s.wire),
                  selected: _filter == s,
                  onTap: () => setState(() => _filter = s),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Expanded(
            child: AsyncValueView<List<Quotation>>(
              value: async,
              onRetry: () => ref.invalidate(quotationListProvider),
              loading: ListSkeleton(
                separatorHeight: AppSpacing.sm,
                itemBuilder: (_) =>
                    const QuotationCard(quotation: _skeletonQuotation),
              ),
              isEmpty: (items) => _visible(items).isEmpty,
              empty: EmptyState(
                icon: Icons.receipt_long_outlined,
                title: 'No quotations',
                message: _query.isNotEmpty || _filter != null
                    ? 'No quotations match your filters.'
                    : _tab == 0
                    ? 'No quotations in progress.'
                    : 'No completed quotations yet.',
              ),
              data: (items) => RefreshIndicator(
                onRefresh: () async => ref.invalidate(quotationListProvider),
                child: ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.lg,
                    AppSpacing.xs,
                    AppSpacing.lg,
                    AppSpacing.xxxl,
                  ),
                  itemCount: _visible(items).length,
                  separatorBuilder: (_, _) =>
                      const SizedBox(height: AppSpacing.sm),
                  itemBuilder: (context, index) =>
                      QuotationCard(quotation: _visible(items)[index]),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// UC-14.2 Generate Reports — opens the filter sheet over the already-loaded list
  /// and persists the audit log through [QuotationActions.saveReportLog].
  Future<void> _openDiscountReport(
    BuildContext context,
    WidgetRef ref,
    List<Quotation> quotations,
  ) async {
    final user = ref.read(currentUserProvider);
    await showDiscountReportSheet(
      context,
      quotations: quotations,
      generatedByName: user?.name.trim().isNotEmpty == true ? user!.name : (user?.email ?? 'Unknown'),
      generatedByRole: (user?.roles.isNotEmpty ?? false) ? user!.roles.first : null,
      onGenerate: (payload) => ref.read(quotationActionsProvider).saveReportLog(payload),
    );
  }
}

/// App-bar action for [QuotationPendingApprovalsScreen] — an icon with a badge
/// showing how many quotations are waiting, split into its own [Consumer] so
/// watching [pendingApprovalsProvider] (a network call) only happens for the
/// MANAGER who can actually use it, not every quotation-list viewer.
class _PendingApprovalsAction extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pendingAsync = ref.watch(pendingApprovalsProvider);
    final count = pendingAsync.valueOrNull?.length ?? 0;

    return IconButton(
      tooltip: 'Pending approvals',
      onPressed: () => context.pushNamed(RouteNames.quotationPendingApprovals),
      icon: Badge(
        label: Text('$count'),
        isLabelVisible: count > 0,
        child: const Icon(Icons.fact_check_outlined),
      ),
    );
  }
}

/// A single quotation row — public so [QuotationPendingApprovalsScreen] and any
/// other quotation screen can render the same card without duplicating it.
class QuotationCard extends StatelessWidget {
  const QuotationCard({super.key, required this.quotation});

  final Quotation quotation;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(AppRadii.lg),
      onTap: () => context.push(Routes.quotationDetailPath(quotation.id)),
      child: SectionCard(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    quotation.quoteNo,
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    quotation.contactName?.trim().isNotEmpty == true
                        ? quotation.contactName!
                        : 'No contact',
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
                  tone: quotation.status.tone,
                  rawStatus: quotation.status.wire,
                  dense: true,
                ),
                const SizedBox(height: 6),
                Text(
                  Formatters.money(quotation.totalAmount),
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
