import 'package:flutter/material.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../data/task_models.dart';

/// The six activity types as one horizontal strip.
///
/// It never wraps: the row shows every type when the width allows and scrolls
/// sideways when it doesn't, so the selector always costs exactly one line of
/// the form. (It used to `Wrap`, which pushed the real inputs two rows down on a
/// narrow phone.)
///
/// The selected chip is *filled* with its type colour rather than merely
/// outlined — colour is what makes the current choice readable at a glance, and
/// the fill/elevation animate so the change registers as motion, not a repaint.
class ActivityTypeSelector extends StatelessWidget {
  const ActivityTypeSelector({
    super.key,
    required this.selected,
    required this.onSelected,
    this.enabled = true,
  });

  final TaskActivityType selected;
  final ValueChanged<TaskActivityType> onSelected;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: _ActivityTypeChip.height,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        physics: const ClampingScrollPhysics(),
        // Let the strip bleed to the screen edge so a scrollable row *looks*
        // scrollable, while the first chip still lines up with the form fields.
        padding: EdgeInsets.zero,
        itemCount: TaskActivityType.values.length,
        separatorBuilder: (_, _) => const SizedBox(width: AppSpacing.sm),
        itemBuilder: (context, index) {
          final type = TaskActivityType.values[index];
          return _ActivityTypeChip(
            type: type,
            selected: type == selected,
            enabled: enabled,
            onTap: () => onSelected(type),
          );
        },
      ),
    );
  }
}

class _ActivityTypeChip extends StatelessWidget {
  const _ActivityTypeChip({
    required this.type,
    required this.selected,
    required this.enabled,
    required this.onTap,
  });

  /// Tall enough to clear the 48dp minimum touch target.
  static const double height = 48;

  final TaskActivityType type;
  final bool selected;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final dark = theme.brightness == Brightness.dark;

    // Selected: solid type colour, white content, soft lift. Unselected: quiet
    // surface with a hairline border, so only one chip ever pulls the eye.
    final background = selected ? type.color : scheme.surfaceContainerLow;
    final foreground = selected
        ? Colors.white
        : (enabled ? scheme.onSurfaceVariant : scheme.onSurfaceVariant.withValues(alpha: 0.5));

    return Semantics(
      button: true,
      selected: selected,
      label: type.label,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: enabled ? onTap : null,
          customBorder: const StadiumBorder(),
          child: AnimatedContainer(
            duration: AppDurations.fast,
            curve: AppCurves.emphasized,
            height: height,
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
            decoration: BoxDecoration(
              color: background,
              borderRadius: BorderRadius.circular(AppRadii.pill),
              border: Border.all(
                color: selected
                    ? type.color
                    : scheme.outlineVariant.withValues(alpha: 0.6),
              ),
              boxShadow: selected
                  ? [
                      BoxShadow(
                        color: type.color.withValues(alpha: dark ? 0.45 : 0.30),
                        blurRadius: 10,
                        offset: const Offset(0, 3),
                      ),
                    ]
                  : null,
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(type.icon, size: AppIconSize.md, color: foreground),
                const SizedBox(width: AppSpacing.sm),
                Text(
                  type.label,
                  style: theme.textTheme.labelLarge?.copyWith(
                    color: foreground,
                    fontWeight: selected ? FontWeight.w700 : FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
