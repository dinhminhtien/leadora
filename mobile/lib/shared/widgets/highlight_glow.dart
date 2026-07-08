import 'package:flutter/material.dart';

import '../../core/theme/app_dimens.dart';

/// Wraps a list card so it briefly glows with the primary color when the
/// user is deep-linked to it (e.g. tapping a notification) — mirrors the
/// web's `useHighlightRow` scroll-and-flash behavior.
class HighlightGlow extends StatelessWidget {
  const HighlightGlow({super.key, required this.highlighted, required this.child});

  final bool highlighted;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOut,
      padding: const EdgeInsets.all(2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppRadii.lg + 2),
        border: Border.all(
          color: highlighted ? scheme.primary : Colors.transparent,
          width: 2,
        ),
        color: highlighted ? scheme.primary.withValues(alpha: 0.06) : Colors.transparent,
      ),
      child: child,
    );
  }
}
