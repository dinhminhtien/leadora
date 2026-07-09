import 'package:flutter/material.dart';

import '../../core/theme/app_dimens.dart';

/// Wraps a list card so it briefly glows with the primary color when the
/// user is deep-linked to it (e.g. tapping a notification) — mirrors the
/// web's `useHighlightRow` scroll-and-flash behavior.
class HighlightGlow extends StatelessWidget {
  const HighlightGlow({
    super.key,
    required this.highlighted,
    required this.child,
  });

  final bool highlighted;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AnimatedContainer(
      duration: AppDurations.slow,
      curve: AppCurves.standard,
      // The ring sits outside the card, so its radius is the card's plus the
      // ring's own width.
      padding: const EdgeInsets.all(AppSpacing.xxs),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppRadii.lg + AppSpacing.xxs),
        border: Border.all(
          color: highlighted ? scheme.primary : Colors.transparent,
          width: 2,
        ),
        color: highlighted
            ? scheme.primary.withValues(alpha: 0.06)
            : Colors.transparent,
      ),
      child: child,
    );
  }
}
