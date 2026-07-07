/// Design tokens — the single source for spacing and corner radii.
///
/// Screens and widgets pull from these instead of hardcoding magic numbers, so
/// the whole app shares one rhythm. Spacing follows a 4-based scale.
class AppSpacing {
  const AppSpacing._();

  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 20;
  static const double xxl = 24;
  static const double xxxl = 32;
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
