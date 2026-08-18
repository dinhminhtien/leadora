import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/search_picker_sheet.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/deal_models.dart';
import '../providers/deal_providers.dart';

/// Search-first picker for the deals a quotation can be raised against (UC-14.1).
///
/// Replaces the drop-down that inflated every deal the rep could see into one menu:
/// unusable past a couple of screens' worth, and — because the mobile form applied no
/// eligibility filter at all — it happily offered won and lost deals that cannot be quoted.
///
/// Eligibility is now `GET /deals/quotable`'s job, and it is one condition: the deal is
/// still active. This widget renders the page it is given and filters nothing.
class QuotableDealPickerSheet extends ConsumerWidget {
  const QuotableDealPickerSheet({super.key, this.selectedDealId});

  /// Marked with a check so reopening the sheet shows what is already chosen.
  final String? selectedDealId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SearchPickerSheet<Deal>(
      title: 'Select a deal',
      subtitle: 'Active deals only. Closing soonest first.',
      searchHint: 'Search by deal, customer or company…',
      leadingIcon: Icons.work_outline_rounded,
      emptyMessage: 'No matching deal. Only active deals can be quoted — won and '
          'lost deals are closed.',
      noOptionsMessage: 'No deal is ready to quote. A deal must still be active — '
          'won and lost deals are closed.',
      selectedKey: selectedDealId,
      keyOf: (deal) => deal.id,
      fetch: (query, page) async {
        final result = await ref.read(
          quotableDealsProvider((search: query, page: page)).future,
        );
        return SearchPickerPage.fromPage(result);
      },
      itemBuilder: (context, deal, isSelected) =>
          _DealRow(deal: deal, isSelected: isSelected),
    );
  }
}

/// Opens the picker and returns the chosen deal, or `null` if dismissed.
Future<Deal?> showQuotableDealPicker(
  BuildContext context, {
  String? selectedDealId,
}) {
  return showModalBottomSheet<Deal>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    // The sheet sizes itself against the viewport, so it stays usable in landscape
    // and on tablets rather than being pinned to a phone-sized fraction.
    useSafeArea: true,
    builder: (_) => QuotableDealPickerSheet(selectedDealId: selectedDealId),
  );
}

class _DealRow extends StatelessWidget {
  const _DealRow({required this.deal, required this.isSelected});

  final Deal deal;
  final bool isSelected;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    // Title alone is ambiguous when one customer has several deals — the contact and
    // the close date are what tell them apart.
    final meta = <String>[
      if ((deal.contactName ?? '').isNotEmpty) deal.contactName!,
      if (deal.expectedClose != null)
        'closes ${Formatters.date(deal.expectedClose!)}',
    ].join(' · ');

    return Container(
      color: isSelected ? scheme.primaryContainer.withValues(alpha: 0.35) : null,
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.lg,
        vertical: AppSpacing.md,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          CircleAvatar(
            backgroundColor: scheme.primaryContainer,
            child: Icon(
              Icons.work_outline_rounded,
              size: AppIconSize.md,
              color: scheme.onPrimaryContainer,
            ),
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  deal.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.titleSmall,
                ),
                if (meta.isNotEmpty) ...[
                  const SizedBox(height: AppSpacing.xxs),
                  Text(
                    meta,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ],
                if (deal.value != null) ...[
                  const SizedBox(height: AppSpacing.xxs),
                  Text(
                    Formatters.money(deal.value),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          if (isSelected)
            Icon(Icons.check_circle_rounded, size: AppIconSize.lg, color: scheme.primary)
          else
            StatusChip(
              tone: deal.stageTone,
              rawStatus: deal.displayStage,
              dense: true,
            ),
        ],
      ),
    );
  }
}
