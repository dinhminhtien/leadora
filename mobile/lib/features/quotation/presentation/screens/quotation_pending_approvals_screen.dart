import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/quotation_models.dart';
import '../providers/quotation_providers.dart';
import '../widgets/quotation_action_sheets.dart';

/// UC-14.3 Processing Quotations — a manager's queue of PENDING_APPROVAL quotations,
/// with Approve / Reject / Request changes actions.
///
/// Server-side this is MANAGER only (`QuotationController.getPendingApprovals` /
/// `processApproval` both require `hasRole('MANAGER')`) — the entry point on
/// [QuotationListScreen] is gated the same way, but this screen re-checks nothing
/// itself: a stray link here still gets a 403 from the backend, same as any other
/// screen in the app.
class QuotationPendingApprovalsScreen extends ConsumerStatefulWidget {
  const QuotationPendingApprovalsScreen({super.key});

  @override
  ConsumerState<QuotationPendingApprovalsScreen> createState() =>
      _QuotationPendingApprovalsScreenState();
}

class _QuotationPendingApprovalsScreenState
    extends ConsumerState<QuotationPendingApprovalsScreen> {
  final _searchController = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<Quotation> _visible(List<Quotation> items) {
    final query = _query.trim().toLowerCase();
    if (query.isEmpty) return items;
    return items.where((q) {
      return q.quoteNo.toLowerCase().contains(query) ||
          (q.contactName ?? '').toLowerCase().contains(query) ||
          (q.dealName ?? '').toLowerCase().contains(query);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(pendingApprovalsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Pending approvals')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.lg,
              AppSpacing.md,
              AppSpacing.lg,
              AppSpacing.xs,
            ),
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
          const SizedBox(height: AppSpacing.xs),
          Expanded(
            child: AsyncValueView<List<Quotation>>(
              value: async,
              onRetry: () => ref.invalidate(pendingApprovalsProvider),
              isEmpty: (items) => _visible(items).isEmpty,
              empty: const EmptyState(
                icon: Icons.fact_check_outlined,
                title: 'All clear',
                message: 'No quotations are waiting on a decision.',
              ),
              data: (items) => RefreshIndicator(
                onRefresh: () async => ref.invalidate(pendingApprovalsProvider),
                child: ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.lg,
                    AppSpacing.xs,
                    AppSpacing.lg,
                    AppSpacing.xxxl,
                  ),
                  itemCount: _visible(items).length,
                  separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
                  itemBuilder: (context, index) {
                    final quotation = _visible(items)[index];
                    return _PendingApprovalCard(
                      quotation: quotation,
                      onReview: () => _review(context, ref, quotation),
                    );
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _review(BuildContext context, WidgetRef ref, Quotation quotation) async {
    final payload = await showProcessApprovalSheet(context, quotation);
    if (payload == null || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      final updated = await ref
          .read(quotationActionsProvider)
          .processApproval(quotation.id, payload);
      messenger.showSnackBar(
        SnackBar(content: Text('${updated.quoteNo} — ${_labelFor(payload.action)}')),
      );
    } on AppException catch (e) {
      // E3 (already processed by another manager) lands here too — the backend's
      // message is surfaced verbatim rather than guessed at client-side.
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  static String _labelFor(ApprovalDecision decision) => switch (decision) {
    ApprovalDecision.approve => 'Approved',
    ApprovalDecision.reject => 'Rejected',
    ApprovalDecision.requestChanges => 'Sent back for revision',
  };
}

class _PendingApprovalCard extends StatelessWidget {
  const _PendingApprovalCard({required this.quotation, required this.onReview});

  final Quotation quotation;
  final VoidCallback onReview;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final overThreshold = (quotation.discountPercent ?? 0) > 10;

    return InkWell(
      borderRadius: BorderRadius.circular(AppRadii.lg),
      onTap: () => context.push(Routes.quotationDetailPath(quotation.id)),
      child: SectionCard(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        quotation.quoteNo,
                        style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        quotation.contactName?.trim().isNotEmpty == true
                            ? quotation.contactName!
                            : 'No contact',
                        style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      if (quotation.dealName?.trim().isNotEmpty == true)
                        Text(
                          quotation.dealName!,
                          style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    StatusChip(tone: quotation.status.tone, rawStatus: quotation.status.wire, dense: true),
                    const SizedBox(height: 6),
                    Text(
                      Formatters.money(quotation.totalAmount),
                      style: theme.textTheme.labelSmall?.copyWith(color: scheme.outline),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Row(
              children: [
                Icon(
                  Icons.percent_rounded,
                  size: AppIconSize.sm,
                  color: overThreshold ? scheme.error : scheme.tertiary,
                ),
                const SizedBox(width: 4),
                Text(
                  '${quotation.discountPercent ?? 0}% discount'
                  '${overThreshold ? ' — exceeds 10% authority' : ''}',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: overThreshold ? scheme.error : scheme.tertiary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            Align(
              alignment: Alignment.centerRight,
              child: OutlinedButton.icon(
                onPressed: onReview,
                icon: const Icon(Icons.fact_check_outlined, size: AppIconSize.md),
                label: const Text('Review'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
