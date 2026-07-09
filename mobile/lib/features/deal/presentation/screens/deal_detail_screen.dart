import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../interaction/presentation/widgets/interaction_summary_card.dart';
import '../../data/deal_models.dart';
import '../providers/deal_providers.dart';

/// View Related Deal Detail on Mobile.
class DealDetailScreen extends ConsumerWidget {
  const DealDetailScreen({super.key, required this.dealId});

  final String dealId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(dealDetailProvider(dealId));

    return Scaffold(
      appBar: AppBar(title: const Text('Deal detail')),
      body: AsyncValueView<Deal>(
        value: async,
        onRetry: () => ref.invalidate(dealDetailProvider(dealId)),
        loading: const DetailSkeleton(),
        data: (deal) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(dealDetailProvider(dealId)),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.xxxl),
            children: [
              _Header(deal: deal),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Contact',
                icon: Icons.contact_page_outlined,
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Name',
                      value: deal.contactName,
                      icon: Icons.badge_outlined,
                    ),
                    InfoRow(
                      label: 'Email',
                      value: deal.email,
                      icon: Icons.mail_outline,
                    ),
                    InfoRow(
                      label: 'Phone',
                      value: deal.phone,
                      icon: Icons.phone_outlined,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SectionCard(
                title: 'Pipeline',
                icon: Icons.timeline_outlined,
                child: Column(
                  children: [
                    InfoRow(label: 'Stage', value: deal.displayStage),
                    InfoRow(label: 'Owner', value: deal.owner),
                    InfoRow(
                      label: 'Value',
                      value: Formatters.money(deal.value),
                    ),
                    InfoRow(
                      label: 'Probability',
                      value: deal.probability != null
                          ? '${deal.probability}%'
                          : null,
                    ),
                    InfoRow(
                      label: 'Expected close',
                      value: Formatters.date(deal.expectedClose),
                    ),
                    InfoRow(
                      label: 'Created',
                      value: Formatters.date(deal.createdAt),
                    ),
                  ],
                ),
              ),
              if (deal.notes != null && deal.notes!.trim().isNotEmpty) ...[
                const SizedBox(height: 12),
                SectionCard(
                  title: 'Notes',
                  icon: Icons.sticky_note_2_outlined,
                  child: Text(
                    deal.notes!,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
              ],
              const SizedBox(height: 12),
              InteractionSummaryCard(
                linkedType: 'deal',
                linkedId: deal.id,
                linkedName: deal.title,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.deal});

  final Deal deal;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                deal.title,
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              StatusChip(tone: deal.status.tone, rawStatus: deal.status.wire),
            ],
          ),
        ),
      ],
    );
  }
}
