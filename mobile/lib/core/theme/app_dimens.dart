import 'package:flutter/animation.dart';

/// Design tokens — the single source for spacing, corner radii, icon sizes,
/// elevation and motion.
///
/// Screens and widgets pull from these instead of hardcoding magic numbers, so
/// the whole app shares one rhythm. Spacing follows a 4-based scale.
class AppSpacing {
  const AppSpacing._();

  static const double xxs = 2;
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 20;
  static const double xxl = 24;
  static const double xxxl = 32;
  static const double huge = 40;

  /// Bottom padding for a scrollable list that sits under an extended FAB, so
  /// the last row is never trapped behind it.
  static const double fabClearance = 96;
}

/// Corner radii. The app reads as a set of soft, consistent surfaces.
class AppRadii {
  const AppRadii._();

  static const double sm = 10;
  static const double md = 14;
  static const double lg = 18;
  static const double xl = 22;
  static const double pill = 999;
}

/// Icon sizes. Inline icons sit on this scale; anything larger is decorative
/// (empty/error illustrations) and uses [display] or [hero].
class AppIconSize {
  const AppIconSize._();

  /// Trailing metadata icons inside dense rows and chips.
  static const double xs = 14;
  static const double sm = 16;

  /// The default for icons paired with body text.
  static const double md = 18;
  static const double lg = 20;

  /// Standard Material action/nav icon.
  static const double xl = 24;
  static const double xxl = 32;

  /// Empty-state illustration.
  static const double display = 40;

  /// Error-state illustration.
  static const double hero = 56;
}

/// Elevation steps. The theme is intentionally flat — depth comes from tonal
/// surface steps, not shadows — so only the FAB lifts off the surface.
class AppElevation {
  const AppElevation._();

  static const double none = 0;

  /// Hairline lift used when a surface scrolls under an app bar.
  static const double hairline = 0.5;

  static const double raised = 2;
  static const double floating = 4;
}

/// Motion durations. Nothing in the UI should animate longer than [slow]; the
/// two long-running values exist only for the highlight-glow attention cue.
class AppDurations {
  const AppDurations._();

  static const Duration fast = Duration(milliseconds: 180);
  static const Duration base = Duration(milliseconds: 220);
  static const Duration slow = Duration(milliseconds: 300);

  /// Input debounce before a search term hits the network.
  static const Duration debounce = Duration(milliseconds: 400);
}

/// Animation curves, so easing is consistent across implicit animations.
class AppCurves {
  const AppCurves._();

  /// Default for entrances, fades and state swaps.
  static const Curve standard = Curves.easeOut;

  /// Slightly more expressive; for elements the user just acted on.
  static const Curve emphasized = Curves.easeOutCubic;
}
