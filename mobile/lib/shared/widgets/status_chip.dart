import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../formatters.dart';

/// A compact, color-coded status pill used across leads / tasks / notifications.
///
/// The [tone] drives the color; the label is auto-humanized from an enum string
/// unless [label] is supplied.
enum StatusTone { neutral, info, success, warning, danger, brand }

class StatusChip extends StatelessWidget {
  const StatusChip({
    super.key,
    required this.tone,
    this.label,
    this.rawStatus,
    this.icon,
    this.dense = false,
  }) : assert(label != null || rawStatus != null,
            'Provide either a label or a rawStatus');

  final StatusTone tone;
  final String? label;
  final String? rawStatus;
  final IconData? icon;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final base = _color(tone);
    final bg = base.withValues(alpha: dark ? 0.22 : 0.12);
    final fg = dark ? _lighten(base) : _darken(base);
    final text = label ?? Formatters.humanizeEnum(rawStatus);

    return Container(
      padding: EdgeInsets.symmetric(
          horizontal: dense ? 8 : 10, vertical: dense ? 3 : 5),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: dense ? 12 : 14, color: fg),
            const SizedBox(width: 4),
          ],
          Text(
            text,
            style: (dense ? theme.textTheme.labelSmall : theme.textTheme.labelMedium)
                ?.copyWith(color: fg, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }

  static Color _color(StatusTone tone) => switch (tone) {
        StatusTone.neutral => AppColors.neutral,
        StatusTone.info => AppColors.info,
        StatusTone.success => AppColors.success,
        StatusTone.warning => AppColors.warning,
        StatusTone.danger => AppColors.danger,
        StatusTone.brand => AppColors.brandSeed,
      };

  static Color _darken(Color c) =>
      HSLColor.fromColor(c).withLightness(0.28).toColor();
  static Color _lighten(Color c) =>
      HSLColor.fromColor(c).withLightness(0.75).toColor();
}
