import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../../../shared/widgets/glass_surface.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../data/feedback_models.dart';
import '../providers/feedback_providers.dart';
import '../widgets/feedback_rating.dart';

/// UC-25.2 — one guest response in full, plus the moderation transition for
/// Manager/Admin.
///
/// Fetches by id rather than accepting a pushed object: the screen is reachable
/// from a notification deep link where no list row exists, and re-reading also
/// picks up a moderation another manager made in the meantime.
class FeedbackDetailScreen extends ConsumerWidget {
  const FeedbackDetailScreen({super.key, required this.feedbackId});

  final String feedbackId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(feedbackDetailProvider(feedbackId));
    final permissions = ref.watch(feedbackPermissionsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Feedback')),
      body: AsyncValueView(
        value: async,
        loading: const DetailSkeleton(),
        onRetry: () => ref.invalidate(feedbackDetailProvider(feedbackId)),
        data: (feedback) => _Body(
          feedback: feedback,
          canModerate: permissions.canModerate,
        ),
      ),
    );
  }
}

class _Body extends ConsumerStatefulWidget {
  const _Body({required this.feedback, required this.canModerate});

  final GuestFeedback feedback;
  final bool canModerate;

  @override
  ConsumerState<_Body> createState() => _BodyState();
}

class _BodyState extends ConsumerState<_Body> {
  bool _saving = false;

  Future<void> _setStatus(FeedbackReviewStatus next) async {
    if (_saving) return;
    setState(() => _saving = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(feedbackListControllerProvider.notifier)
          .setReviewStatus(widget.feedback, next);
      // The list holds the optimistic copy; the detail provider must re-read so
      // this screen does not keep showing the old status.
      ref.invalidate(feedbackDetailProvider(widget.feedback.feedbackId));
      messenger.showSnackBar(
        SnackBar(content: Text('Marked as ${next.label.toLowerCase()}.')),
      );
    } catch (_) {
      messenger.showSnackBar(
        const SnackBar(content: Text('Could not update the review status.')),
      );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final f = widget.feedback;

    return ListView(
      padding: const EdgeInsets.all(AppSpacing.md),
      children: [
        GlassCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      f.customerName ?? 'Unknown guest',
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  FeedbackStatusChip(status: f.reviewStatus),
                ],
              ),
              if (f.bookingCode != null) ...[
                const SizedBox(height: 2),
                Text(
                  'Booking ${f.bookingCode}',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
              const SizedBox(height: AppSpacing.md),
              StarRating(rating: f.rating, size: 22),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.md),

        if (f.hasBreakdown)
          SectionCard(
            title: 'Service breakdown',
            child: Column(
              children: [
                RatingBreakdownRow(label: 'Staff attitude', value: f.ratingAttitude),
                RatingBreakdownRow(label: 'Response speed', value: f.ratingSpeed),
                RatingBreakdownRow(label: 'Accuracy', value: f.ratingAccuracy),
              ],
            ),
          ),
        if (f.hasBreakdown) const SizedBox(height: AppSpacing.md),

        SectionCard(
          title: 'Comment',
          child: Text(
            f.comment?.trim().isNotEmpty == true
                ? f.comment!
                : 'The guest did not leave a comment.',
            style: theme.textTheme.bodyMedium?.copyWith(
              fontStyle: f.comment?.trim().isNotEmpty == true
                  ? null
                  : FontStyle.italic,
              color: f.comment?.trim().isNotEmpty == true
                  ? null
                  : theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.md),

        SectionCard(
          title: 'Record',
          child: Column(
            children: [
              InfoRow(label: 'Sales staff', value: f.salesStaffName ?? '—'),
              InfoRow(
                label: 'Submitted',
                value: f.submittedAt == null
                    ? '—'
                    : Formatters.dateTime(f.submittedAt!),
              ),
              if (f.reviewedByName != null)
                InfoRow(label: 'Reviewed by', value: f.reviewedByName!),
              if (f.reviewedAt != null)
                InfoRow(
                  label: 'Reviewed at',
                  value: Formatters.dateTime(f.reviewedAt!),
                ),
            ],
          ),
        ),

        if (widget.canModerate) ...[
          const SizedBox(height: AppSpacing.lg),
          Text(
            'Moderation',
            style: theme.textTheme.labelLarge?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          // Wrap, not Row: at a large text scale two full-width buttons will not
          // fit side by side, and wrapping is better than clipping a label.
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              FilledButton.icon(
                onPressed:
                    _saving || f.reviewStatus == FeedbackReviewStatus.reviewed
                    ? null
                    : () => _setStatus(FeedbackReviewStatus.reviewed),
                icon: const Icon(Icons.check_rounded, size: 18),
                label: const Text('Mark reviewed'),
              ),
              OutlinedButton.icon(
                onPressed:
                    _saving || f.reviewStatus == FeedbackReviewStatus.dismissed
                    ? null
                    : () => _setStatus(FeedbackReviewStatus.dismissed),
                icon: const Icon(Icons.block_rounded, size: 18),
                label: const Text('Dismiss'),
              ),
            ],
          ),
        ],
        const SizedBox(height: AppSpacing.xl),
      ],
    );
  }
}
