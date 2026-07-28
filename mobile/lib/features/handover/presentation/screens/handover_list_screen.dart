import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/pagination_response.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_filter_chip.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/list_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/handover_models.dart';
import '../providers/handover_providers.dart';
import 'handover_detail_screen.dart';

/// Placeholder row for the skeleton — the real card with dummy data so the list keeps
/// its shape when data lands.
///
/// Dates are relative, not literal: a past [Handover.checkInDate] would add the "Arriving"
/// chip, and Skeletonizer sizes its placeholders to intrinsic width (the `Expanded` booking
/// code no longer shrinks), which overflows the header row at 320dp.
final _skeletonHandover = Handover(
  handoverId: '',
  bookingCode: 'BK-00000000',
  customerName: 'Placeholder customer',
  roomSummary: '1 x Placeholder room',
  checkInDate: DateTime.now().add(const Duration(days: 7)),
  checkOutDate: DateTime.now().add(const Duration(days: 10)),
  status: HandoverStatus.submitted,
  statusRaw: 'SUBMITTED',
  readinessStatus: ReadinessStatus.pendingReview,
  readinessStatusRaw: 'PENDING_REVIEW',
);

/// What Sales handed to the Front Office.
///
/// Read-only on readiness: the desk sets that through its own FO-only endpoint on the web
/// app. Sales still needs to see it, because a `NEED_CLARIFICATION` is theirs to answer.
class HandoverListScreen extends ConsumerWidget {
  const HandoverListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(operationalHandoverListProvider);

    void refresh() => ref.invalidate(operationalHandoverListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Handovers')),
      body: Column(
        children: [
          _StatusFilterBar(ref: ref),
          Expanded(
            child: AsyncValueView<PaginationResponse<Handover>>(
              value: async,
              onRetry: refresh,
              loading: ListSkeleton(
                itemBuilder: (_) => HandoverCard(handover: _skeletonHandover),
              ),
              isEmpty: (page) => page.items.isEmpty,
              empty: const EmptyState(
                icon: Icons.assignment_turned_in_outlined,
                title: 'No handovers',
                message: 'Confirmed bookings handed to the Front Office appear here.',
              ),
              data: (page) => RefreshIndicator(
                onRefresh: () async => refresh(),
                child: ListView.separated(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  itemCount: page.items.length,
                  separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.md),
                  itemBuilder: (context, index) {
                    final handover = page.items[index];
                    return HandoverCard(
                      handover: handover,
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) =>
                              HandoverDetailScreen(handoverId: handover.handoverId),
                        ),
                      ),
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
}

class _StatusFilterBar extends StatelessWidget {
  const _StatusFilterBar({required this.ref});

  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    final filter = ref.watch(handoverStatusFilterProvider);
    return AppFilterChipBar(
      children: [
        AppFilterChip(
          label: 'All',
          selected: filter == null,
          onTap: () => ref.read(handoverStatusFilterProvider.notifier).state = null,
        ),
        for (final s in HandoverStatus.values)
          AppFilterChip(
            label: s.label,
            selected: filter == s,
            onTap: () => ref.read(handoverStatusFilterProvider.notifier).state = s,
          ),
      ],
    );
  }
}


/// One handover row: what the desk was told, and how far it has got.
class HandoverCard extends StatelessWidget {
  const HandoverCard({super.key, required this.handover, this.onTap});

  final Handover handover;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadii.lg),
      child: SectionCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              handover.bookingCode ?? 'Handover',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: AppSpacing.xs),
            // Chips wrap instead of sharing a row with the code: 'Needs clarification' is
            // ~235dp wide and does not shrink, which at 320dp left the booking code a
            // 21dp column of stacked characters. Same treatment as the detail header.
            Wrap(
              spacing: AppSpacing.xs,
              runSpacing: AppSpacing.xs,
              children: [
                // Arrival today is the desk's urgent bucket, so it is called out.
                if (handover.isArrivingToday)
                  const StatusChip(
                    tone: StatusTone.danger,
                    label: 'Arriving',
                    icon: Icons.flight_land_rounded,
                    dense: true,
                  ),
                StatusChip(
                  tone: handover.readinessTone,
                  label: handover.displayReadiness,
                  dense: true,
                ),
              ],
            ),
            if (handover.customerName != null) ...[
              const SizedBox(height: AppSpacing.xs),
              Text(
                handover.customerName!,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
            ],
            const SizedBox(height: AppSpacing.sm),
            Row(
              children: [
                Icon(
                  Icons.calendar_today_rounded,
                  size: AppIconSize.sm,
                  color: scheme.onSurfaceVariant,
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Text(
                    '${Formatters.date(handover.checkInDate)} → '
                    '${Formatters.date(handover.checkOutDate)}',
                    style: theme.textTheme.bodySmall,
                  ),
                ),
              ],
            ),
            if (handover.roomSummary != null) ...[
              const SizedBox(height: AppSpacing.xs),
              Row(
                children: [
                  Icon(
                    Icons.bed_outlined,
                    size: AppIconSize.sm,
                    color: scheme.onSurfaceVariant,
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  Expanded(
                    child: Text(
                      handover.roomSummary!,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
                ],
              ),
            ],
            // A clarification request is the one thing Sales must act on, so it is not
            // buried in the detail screen.
            if (handover.readinessStatus == ReadinessStatus.needClarification &&
                handover.clarificationNote != null) ...[
              const SizedBox(height: AppSpacing.sm),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(AppSpacing.sm),
                decoration: BoxDecoration(
                  color: scheme.errorContainer,
                  borderRadius: BorderRadius.circular(AppRadii.sm),
                ),
                child: Text(
                  handover.clarificationNote!,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onErrorContainer,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
