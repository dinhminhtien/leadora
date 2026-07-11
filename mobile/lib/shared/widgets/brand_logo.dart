import 'package:flutter/material.dart';

import '../../core/theme/app_dimens.dart';

/// The official Leadora brand mark — the same `logo1.jpg` asset the website
/// uses (bundled as `assets/images/logo.png`), clipped to the app's radius
/// scale so it reads as one identity across splash, login and headers.
///
/// The source asset is the colored mark on a white box, so it has two
/// theme-dependent color states mirroring the web (`mix-blend-multiply
/// dark:invert` in DashboardLayout/LandingNavbar):
///   * light — multiply-blend against the surface, melting the white box away;
///   * dark  — invert the mark (web parity), then screen-blend against the
///     surface so the now-black box melts into the dark background.
class BrandLogo extends StatelessWidget {
  const BrandLogo({super.key, this.size = 64, this.radius});

  /// Square edge length in dp.
  final double size;

  /// Corner radius override; defaults to a proportional soft radius.
  final double? radius;

  /// CSS `filter: invert(1)` as a color matrix.
  static const ColorFilter _invert = ColorFilter.matrix([
    -1, 0, 0, 0, 255, //
    0, -1, 0, 0, 255, //
    0, 0, -1, 0, 255, //
    0, 0, 0, 1, 0,
  ]);

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;

    Widget logo = Image.asset(
      'assets/images/logo.png',
      width: size,
      height: size,
      fit: BoxFit.cover,
      // Never let a missing asset break a screen — fall back to the old mark.
      errorBuilder: (context, _, _) => Container(
        width: size,
        height: size,
        color: scheme.primaryContainer,
        child: Icon(Icons.hub_rounded, size: size * 0.5, color: scheme.primary),
      ),
    );

    if (dark) {
      logo = ColorFiltered(colorFilter: _invert, child: logo);
    }
    // Melt the box into whatever surface the logo sits on: multiply leaves
    // white pixels as the surface color; screen does the same for black ones.
    logo = ColorFiltered(
      colorFilter: ColorFilter.mode(
        scheme.surface,
        dark ? BlendMode.screen : BlendMode.multiply,
      ),
      child: logo,
    );

    return ClipRRect(
      borderRadius: BorderRadius.circular(
        radius ?? (size >= 56 ? AppRadii.xl : AppRadii.sm),
      ),
      child: logo,
    );
  }
}
