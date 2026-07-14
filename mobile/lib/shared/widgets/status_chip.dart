import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_dimens.dart';
import '../formatters.dart';

/// A compact, color-coded status pill used across leads / tasks / notifications.
///
/// The [tone] drives the color; the label is auto-humanized from an enum string
/// unless [label] is supplied. For categorical colors outside the semantic set
/// (task activity types), pass [color] instead of a tone.
enum StatusTone { neutral, info, success, warning, danger, brand }

class StatusChip extends StatelessWidget {
  const StatusChip({
    super.key,
    required this.tone,
    this.label,
    this.rawStatus,
    this.icon,
    this.dense = false,
    this.color,
  }) : assert(
         label != null || rawStatus != null,
         'Provide either a label or a rawStatus',
       );

  final StatusTone tone;
  final String? label;
  final String? rawStatus;
  final IconData? icon;
  final bool dense;

  /// Overrides the color [tone] would pick. Use for peer categories that carry
  /// no state meaning — e.g. `TaskActivityType.color`.
  final Color? color;

  /// Chip fill opacity over the surface; higher in dark mode so the tint reads.
  static const double _fillAlphaDark = 0.22;
  static const double _fillAlphaLight = 0.12;

  /// Foreground lightness. Pushed away from the fill so text clears WCAG AA on
  /// both backgrounds.
  static const double _fgLightnessDark = 0.75;
  static const double _fgLightnessLight = 0.28;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final base = color ?? _color(tone);
    final bg = base.withValues(alpha: dark ? _fillAlphaDark : _fillAlphaLight);
    final fg = dark ? _lighten(base) : _darken(base);
    final text = label ?? Formatters.humanizeEnum(rawStatus);

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: dense ? AppSpacing.sm : 10,
        vertical: dense ? 3 : 5,
      ),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(AppRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: dense ? 12 : AppIconSize.xs, color: fg),
            const SizedBox(width: AppSpacing.xs),
          ],
          // Flexible + ellipsis so a chip squeezed by a tight row (320dp
          // phones, large text scale) truncates instead of overflowing.
          Flexible(
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style:
                  (dense
                          ? theme.textTheme.labelSmall
                          : theme.textTheme.labelMedium)
                      ?.copyWith(color: fg, fontWeight: FontWeight.w600),
            ),
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
      HSLColor.fromColor(c).withLightness(_fgLightnessLight).toColor();
  static Color _lighten(Color c) =>
      HSLColor.fromColor(c).withLightness(_fgLightnessDark).toColor();
}
