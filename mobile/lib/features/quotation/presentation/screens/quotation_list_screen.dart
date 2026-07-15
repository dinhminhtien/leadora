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
import '../../data/quotation_models.dart';
import '../providers/quotation_providers.dart';

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

/// View Quotation Status on Mobile — browsable entry point onto
/// [QuotationDetailScreen]. The backend already owner-scopes the list
/// (SALES sees only quotations they created; MANAGER/ADMIN see all) — the
/// status chips below just filter client-side over whatever that returns.
class QuotationListScreen extends ConsumerStatefulWidget {
  const QuotationListScreen({super.key});

  @override
  ConsumerState<QuotationListScreen> createState() =>
      _QuotationListScreenState();
}

class _QuotationListScreenState extends ConsumerState<QuotationListScreen> {
  QuotationStatus? _filter;

  List<Quotation> _visible(List<Quotation> items) => _filter == null
      ? items
      : items.where((q) => q.status == _filter).toList();

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(quotationListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Quotations')),
      body: Column(
        children: [
          AppFilterChipBar(
            children: [
              AppFilterChip(
                label: 'All',
                selected: _filter == null,
                onTap: () => setState(() => _filter = null),
              ),
              for (final s in QuotationStatus.values)
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
                    const _QuotationCard(quotation: _skeletonQuotation),
              ),
              isEmpty: (items) => _visible(items).isEmpty,
              empty: const EmptyState(
                icon: Icons.receipt_long_outlined,
                title: 'No quotations',
                message: 'Quotations sent to customers will show up here.',
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
                      _QuotationCard(quotation: _visible(items)[index]),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _QuotationCard extends StatelessWidget {
  const _QuotationCard({required this.quotation});

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
