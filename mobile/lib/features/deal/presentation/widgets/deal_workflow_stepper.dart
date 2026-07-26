import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/deal_models.dart';
import '../providers/deal_providers.dart';

/// The Sales lifecycle for one deal, mirroring the web `DealWorkflowStepper`.
///
/// Read-only by design: every step is derived from `GET /deals/{id}/workflow`, which the
/// backend resolves from the live quotation → booking → payment chain. Nothing here writes
/// to the deal.
///
/// Failure is rendered inline rather than through `AsyncValueView`: this sits inside a deal
/// that loaded fine, and a workflow lookup that fails must not replace the whole screen.
class DealWorkflowStepper extends ConsumerWidget {
  const DealWorkflowStepper({super.key, required this.dealId});

  final String dealId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final async = ref.watch(dealWorkflowProvider(dealId));

    return SectionCard(
      title: 'Sales lifecycle',
      icon: Icons.timeline_rounded,
      child: async.when(
        loading: () => Padding(
          padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
          child: Row(
            children: [
              const SizedBox(
                width: AppIconSize.sm,
                height: AppIconSize.sm,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  'Loading progress…',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
        ),
        error: (_, _) => Row(
          children: [
            Icon(
              Icons.error_outline_rounded,
              size: AppIconSize.sm,
              color: scheme.error,
            ),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: Text(
                'Could not load lifecycle progress.',
                style: theme.textTheme.bodySmall?.copyWith(color: scheme.error),
              ),
            ),
            TextButton(
              onPressed: () => ref.invalidate(dealWorkflowProvider(dealId)),
              child: const Text('Retry'),
            ),
          ],
        ),
        data: (summary) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (final (index, step) in _steps(summary).indexed)
              _StepRow(step: step, isLast: index == _steps(summary).length - 1),
          ],
        ),
      ),
    );
  }

  /// The five stages of the sale, in order.
  ///
  /// Stage comparisons use the wire enum names (`PROPOSAL`, `NEGOTIATION`, …) — the payload
  /// carries `DealPipelineStage.name()`, not the display label.
  static List<_Step> _steps(DealWorkflowSummary s) {
    final stage = s.stage;
    final open = !s.isClosed;
    return [
      _Step(
        title: 'Inquiry',
        description: 'Initial client inquiry registered',
        // Reaching a deal record at all means the inquiry happened.
        done: true,
        active:
            open &&
            (stage == DealStage.prospecting || stage == DealStage.qualification),
        detail: 'Stage: ${s.displayStage}',
      ),
      _Step(
        title: 'Proposal / quotation',
        description: 'Send pricing options and get approval',
        done: s.activeQuotationId != null,
        active: open && stage == DealStage.proposal,
        detail: s.activeQuotationId == null ? 'No active quotation' : null,
        chipLabel: s.activeQuotationStatus,
        chipTone: StatusTone.info,
      ),
      _Step(
        title: 'Booking reservation',
        description: 'Confirm details and reserve the rooms',
        done: s.activeBookingId != null,
        active: open && stage == DealStage.negotiation,
        detail: s.activeBookingId == null ? 'No active booking' : null,
        chipLabel: s.activeBookingStatus,
        chipTone: StatusTone.brand,
      ),
      _Step(
        title: 'Securing payment',
        description: 'Collect the deposit or full amount',
        done: s.hasPaidPayment,
        active: open && s.activeBookingId != null && !s.hasPaidPayment,
        detail: s.currentPaymentStatus == null ? 'No payments recorded' : null,
        chipLabel: s.currentPaymentStatus,
        chipTone: s.hasPaidPayment ? StatusTone.success : StatusTone.warning,
      ),
      _Step(
        title: 'Deal closed won',
        description: 'Contract signed, booking paid, sale complete',
        done: s.isWon,
        // The stage can sit on CLOSED_WON before the deal itself is marked won.
        active: open && stage == DealStage.closedWon,
        chipLabel: s.dealStatusRaw,
        chipTone: s.isWon
            ? StatusTone.success
            : s.isLost
            ? StatusTone.danger
            : StatusTone.neutral,
      ),
    ];
  }
}

class _Step {
  const _Step({
    required this.title,
    required this.description,
    required this.done,
    required this.active,
    this.detail,
    this.chipLabel,
    this.chipTone = StatusTone.neutral,
  });

  final String title;
  final String description;
  final bool done;
  final bool active;

  /// Fallback line shown when there is nothing to put in a chip.
  final String? detail;
  final String? chipLabel;
  final StatusTone chipTone;
}

class _StepRow extends StatelessWidget {
  const _StepRow({required this.step, required this.isLast});

  final _Step step;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    final (icon, iconColor) = switch (step) {
      _Step(done: true) => (Icons.check_circle_rounded, scheme.primary),
      _Step(active: true) => (Icons.radio_button_checked_rounded, scheme.primary),
      _ => (Icons.circle_outlined, scheme.outlineVariant),
    };

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Marker plus the connector to the next step, so the rail is continuous
        // regardless of how tall each step's text runs.
        Column(
          children: [
            Icon(icon, size: AppIconSize.lg, color: iconColor),
            if (!isLast)
              Container(
                width: 2,
                height: 28,
                margin: const EdgeInsets.symmetric(vertical: AppSpacing.xxs),
                color: scheme.outlineVariant,
              ),
          ],
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Padding(
            padding: EdgeInsets.only(bottom: isLast ? 0 : AppSpacing.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  step.title,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontWeight: step.active ? FontWeight.w700 : FontWeight.w600,
                    color: step.done || step.active
                        ? scheme.onSurface
                        : scheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: AppSpacing.xxs),
                Text(
                  step.description,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
                if (step.chipLabel != null && step.chipLabel!.isNotEmpty) ...[
                  const SizedBox(height: AppSpacing.xs),
                  StatusChip(
                    tone: step.chipTone,
                    rawStatus: step.chipLabel,
                    dense: true,
                  ),
                ] else if (step.detail != null) ...[
                  const SizedBox(height: AppSpacing.xs),
                  Text(
                    step.detail!,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }
}
