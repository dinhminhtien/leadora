import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';
import '../../data/feedback_models.dart';

/// Star row for an overall score.
///
/// A null rating renders "Not rated" rather than zero stars: the guest left a
/// comment without scoring, and five empty stars would read as one out of five.
class StarRating extends StatelessWidget {
  const StarRating({
    super.key,
    required this.rating,
    this.size = 16,
    this.showValue = true,
  });

  final int? rating;
  final double size;
  final bool showValue;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (rating == null || rating! <= 0) {
      return Text(
        'Not rated',
        style: theme.textTheme.labelSmall?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
          fontStyle: FontStyle.italic,
        ),
      );
    }

    final value = rating!.clamp(0, 5);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Semantics carries the score for screen readers; the icons themselves
        // are decorative once the label says "4 out of 5".
        Semantics(
          label: '$value out of 5 stars',
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (var i = 1; i <= 5; i++)
                Icon(
                  i <= value ? Icons.star_rounded : Icons.star_outline_rounded,
                  size: size,
                  color: i <= value
                      ? AppColors.warning
                      : theme.colorScheme.outlineVariant,
                ),
            ],
          ),
        ),
        if (showValue) ...[
          const SizedBox(width: 6),
          Text(
            '$value.0',
            style: theme.textTheme.labelMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ],
    );
  }
}

/// One of the three service sub-scores, as a labelled bar.
///
/// A bar rather than more stars: three star rows stacked read as one
/// fifteen-star score, and the point of the breakdown is comparing dimensions
/// against each other.
class RatingBreakdownRow extends StatelessWidget {
  const RatingBreakdownRow({
    super.key,
    required this.label,
    required this.value,
  });

  final String label;
  final int? value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final score = (value ?? 0).clamp(0, 5);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          // Fixed-ish label column, but expressed as a flex so a large text
          // scale redistributes width instead of clipping the label.
          Expanded(
            flex: 4,
            child: Text(
              label,
              style: theme.textTheme.bodySmall,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Expanded(
            flex: 6,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: value == null ? 0 : score / 5,
                minHeight: 6,
                backgroundColor: theme.colorScheme.surfaceContainerHighest,
                valueColor: AlwaysStoppedAnimation<Color>(
                  score >= 4
                      ? AppColors.success
                      : score >= 3
                      ? AppColors.warning
                      : AppColors.danger,
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 28,
            child: Text(
              value == null ? '—' : '$score',
              textAlign: TextAlign.end,
              style: theme.textTheme.labelMedium?.copyWith(
                fontWeight: FontWeight.w700,
                color: value == null
                    ? theme.colorScheme.onSurfaceVariant
                    : null,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Review-status pill. Kept local to the module because the tone mapping is
/// feedback-specific: `dismissed` is neutral here, not a failure — a manager
/// dismissing a duplicate is a normal outcome.
class FeedbackStatusChip extends StatelessWidget {
  const FeedbackStatusChip({super.key, required this.status});

  final FeedbackReviewStatus status;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final (bg, fg) = switch (status) {
      FeedbackReviewStatus.pending => (
        AppColors.warning.withValues(alpha: 0.14),
        AppColors.warning,
      ),
      FeedbackReviewStatus.reviewed => (
        AppColors.success.withValues(alpha: 0.14),
        AppColors.success,
      ),
      FeedbackReviewStatus.dismissed => (
        theme.colorScheme.surfaceContainerHighest,
        theme.colorScheme.onSurfaceVariant,
      ),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        status.label,
        style: theme.textTheme.labelSmall?.copyWith(
          color: fg,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
