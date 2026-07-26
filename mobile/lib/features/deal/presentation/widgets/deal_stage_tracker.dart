import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../data/deal_models.dart';
import '../providers/deal_providers.dart';

/// Move a deal along the funnel, mirroring the web drawer's "Pipeline Stage Progression".
///
/// Reads [Deal.stage], which comes from `DealResponse.stageCode` — the authoritative enum.
/// `DealResponse.stage` is a display label the backend derives lossily (both `CLOSED_WON`
/// and `CLOSED_LOST` can serialize to "Confirmed"), so it is never used for logic here.
///
/// The backend validates each transition (`DealValidation`), e.g. Proposal requires a deal
/// value. A refusal is surfaced as its message rather than pre-empted, so the rule lives in
/// exactly one place.
class DealStageTracker extends ConsumerStatefulWidget {
  const DealStageTracker({super.key, required this.deal});

  final Deal deal;

  @override
  ConsumerState<DealStageTracker> createState() => _DealStageTrackerState();
}

class _DealStageTrackerState extends ConsumerState<DealStageTracker> {
  /// The stage being written, so only that chip shows progress.
  DealStage? _saving;

  /// The funnel, in order. The two terminal stages share the last slot in
  /// [DealStage.order], so they are offered as one step and split by status.
  static const _progression = <DealStage>[
    DealStage.prospecting,
    DealStage.qualification,
    DealStage.proposal,
    DealStage.negotiation,
  ];

  bool get _isClosed =>
      widget.deal.status == DealStatus.won || widget.deal.status == DealStatus.lost;

  Future<void> _moveTo(DealStage target) async {
    final deal = widget.deal;
    if (deal.stage == target || _saving != null) return;

    setState(() => _saving = target);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(dealActionsProvider).update(
        deal.id,
        DealPayload(
          title: deal.title,
          contactName: deal.contactName ?? '',
          stage: target,
        ),
      );
      messenger.showSnackBar(
        SnackBar(content: Text('Stage moved to ${target.label}')),
      );
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _saving = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final current = widget.deal.stage;
    final currentOrder = current?.order ?? -1;

    return SectionCard(
      title: 'Pipeline stage',
      icon: Icons.linear_scale_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (_isClosed)
            _ClosedBanner(status: widget.deal.status)
          else
            Text(
              'Tap a stage to move this deal. The backend checks each step.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
          const SizedBox(height: AppSpacing.md),
          // Wrap, not Row: four labels plus a busy spinner will not fit one line at
          // 320dp / scale 1.3, and a Row would clip the last stage.
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              for (final stage in _progression)
                _StageChip(
                  label: stage.label,
                  isCurrent: stage == current,
                  isPast: stage.order < currentOrder,
                  busy: _saving == stage,
                  onTap: _isClosed || _saving != null
                      ? null
                      : () => _moveTo(stage),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ClosedBanner extends StatelessWidget {
  const _ClosedBanner({required this.status});

  final DealStatus status;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final won = status == DealStatus.won;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.sm),
      decoration: BoxDecoration(
        color: won ? scheme.secondaryContainer : scheme.errorContainer,
        borderRadius: BorderRadius.circular(AppRadii.sm),
      ),
      child: Row(
        children: [
          Icon(
            won ? Icons.emoji_events_rounded : Icons.block_rounded,
            size: AppIconSize.sm,
            color: won ? scheme.onSecondaryContainer : scheme.onErrorContainer,
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              'This deal is ${won ? "won" : "lost"} and can no longer be moved.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: won ? scheme.onSecondaryContainer : scheme.onErrorContainer,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// One step in the funnel: done, current, or still ahead.
class _StageChip extends StatelessWidget {
  const _StageChip({
    required this.label,
    required this.isCurrent,
    required this.isPast,
    required this.busy,
    required this.onTap,
  });

  final String label;
  final bool isCurrent;
  final bool isPast;
  final bool busy;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    final (bg, fg) = switch (this) {
      _StageChip(isCurrent: true) => (scheme.primary, scheme.onPrimary),
      _StageChip(isPast: true) => (scheme.secondaryContainer, scheme.onSecondaryContainer),
      _ => (scheme.surfaceContainerHighest, scheme.onSurfaceVariant),
    };

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadii.pill),
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: AppSpacing.sm,
        ),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(AppRadii.pill),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (busy)
              SizedBox(
                width: AppIconSize.xs,
                height: AppIconSize.xs,
                child: CircularProgressIndicator(strokeWidth: 2, color: fg),
              )
            else if (isPast)
              Icon(Icons.check_rounded, size: AppIconSize.xs, color: fg),
            if (busy || isPast) const SizedBox(width: AppSpacing.xs),
            Text(
              label,
              style: theme.textTheme.labelMedium?.copyWith(
                color: fg,
                fontWeight: isCurrent ? FontWeight.w800 : FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
