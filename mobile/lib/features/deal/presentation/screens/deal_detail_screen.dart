import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../interaction/presentation/widgets/interaction_summary_card.dart';
import '../../data/deal_models.dart';
import '../providers/deal_providers.dart';
import '../widgets/deal_stage_tracker.dart';
import '../widgets/deal_workflow_stepper.dart';
import 'create_deal_screen.dart';

/// View Related Deal Detail on Mobile.
class DealDetailScreen extends ConsumerWidget {
  const DealDetailScreen({super.key, required this.dealId});

  final String dealId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(dealDetailProvider(dealId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Deal detail'),
        actions: [
          // Only offered once the deal has loaded, and never on a closed deal — the web
          // drawer greys its form out the same way.
          if (async.valueOrNull?.status == DealStatus.active)
            IconButton(
              tooltip: 'Edit deal',
              icon: const Icon(Icons.edit_outlined),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => CreateDealScreen(deal: async.requireValue),
                ),
              ),
            ),
        ],
      ),
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
              // Where the sale actually stands, above the static fields — this is the
              // question a rep opens a deal to answer.
              DealWorkflowStepper(dealId: dealId),
              const SizedBox(height: 12),
              DealStageTracker(deal: deal),
              const SizedBox(height: 12),
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
              // UC-13 — closing the deal. Separate from the stage tracker because
              // `PATCH /deals/{id}/status` skips stage validation on purpose: marking a
              // deal lost must not require an estimated close date.
              if (deal.status == DealStatus.active) ...[
                const SizedBox(height: AppSpacing.xl),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: () => _close(context, ref, deal, DealStatus.won),
                        icon: const Icon(Icons.emoji_events_outlined),
                        label: const Text('Mark won'),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () => _close(context, ref, deal, DealStatus.lost),
                        icon: const Icon(Icons.do_not_disturb_alt_rounded),
                        label: const Text('Mark lost'),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  /// Confirm, then close the deal. Irreversible in the UI — the backend offers no
  /// re-open — so it always asks first.
  Future<void> _close(
    BuildContext context,
    WidgetRef ref,
    Deal deal,
    DealStatus status,
  ) async {
    final won = status == DealStatus.won;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(won ? 'Mark deal as won?' : 'Mark deal as lost?'),
        content: Text(
          won
              ? '"${deal.title}" will be closed as won. This cannot be undone here.'
              : '"${deal.title}" will be closed as lost. This cannot be undone here.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(won ? 'Mark won' : 'Mark lost'),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(dealActionsProvider).setStatus(deal.id, status);
      messenger.showSnackBar(
        SnackBar(content: Text('Deal marked ${won ? "won" : "lost"}')),
      );
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
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
