import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/quotation_models.dart';
import '../providers/quotation_providers.dart';

/// View Quotation Status on Mobile — browsable entry point onto
/// [QuotationDetailScreen]. The backend list endpoint is unfiltered, so the
/// status chips below filter client-side over the already-fetched list.
class QuotationListScreen extends ConsumerStatefulWidget {
  const QuotationListScreen({super.key});

  @override
  ConsumerState<QuotationListScreen> createState() => _QuotationListScreenState();
}

class _QuotationListScreenState extends ConsumerState<QuotationListScreen> {
  QuotationStatus? _filter;

  List<Quotation> _visible(List<Quotation> items) =>
      _filter == null ? items : items.where((q) => q.status == _filter).toList();

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(quotationListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Quotations')),
      body: Column(
        children: [
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: ChoiceChip(
                    label: const Text('All'),
                    selected: _filter == null,
                    onSelected: (_) => setState(() => _filter = null),
                  ),
                ),
                for (final s in QuotationStatus.values)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: ChoiceChip(
                      label: Text(Formatters.humanizeEnum(s.wire)),
                      selected: _filter == s,
                      onSelected: (_) => setState(() => _filter = s),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: AsyncValueView<List<Quotation>>(
              value: async,
              onRetry: () => ref.invalidate(quotationListProvider),
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
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
                  itemCount: _visible(items).length,
                  separatorBuilder: (_, _) => const SizedBox(height: 10),
                  itemBuilder: (context, index) => _QuotationCard(quotation: _visible(items)[index]),
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
      borderRadius: BorderRadius.circular(16),
      onTap: () => context.push(Routes.quotationDetailPath(quotation.id)),
      child: SectionCard(
        padding: const EdgeInsets.all(14),
        child: Row(
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
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
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
                StatusChip(tone: quotation.status.tone, rawStatus: quotation.status.wire, dense: true),
                const SizedBox(height: 6),
                Text(
                  Formatters.money(quotation.totalAmount),
                  style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.outline),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
