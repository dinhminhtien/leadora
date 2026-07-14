import 'package:flutter/material.dart';

import '../../core/theme/app_dimens.dart';

/// A single choice chip in a horizontally scrolling filter bar, with an optional
/// trailing count.
///
/// Lead and Task list screens still carry private copies of this; migrate them
/// here rather than growing a third.
class AppFilterChip extends StatelessWidget {
  const AppFilterChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
    this.count,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  /// Rendered after the label when non-null. A zero count still shows, so an
  /// empty stage reads as "genuinely empty" rather than "not loaded".
  final int? count;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xs),
      child: ChoiceChip(
        selected: selected,
        onSelected: (_) => onTap(),
        label: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(label),
            if (count != null) ...[
              const SizedBox(width: AppSpacing.sm),
              Text(
                '$count',
                style: theme.textTheme.labelSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                  color: selected
                      ? scheme.onSecondaryContainer
                      : scheme.onSurfaceVariant,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Horizontal, scrollable row of [AppFilterChip]s with a consistent height so it
/// does not jump when chips gain or lose a count badge.
class AppFilterChipBar extends StatelessWidget {
  const AppFilterChipBar({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    // Scales with the user's text size so the chips never clip at large font
    // scale (see the KPI-card overflow that motivated this).
    final scale = MediaQuery.textScalerOf(context).scale(1);

    return SizedBox(
      height: 44 * scale.clamp(1.0, 1.6),
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
        children: children,
      ),
    );
  }
}
