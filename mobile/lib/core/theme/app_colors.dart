import 'package:flutter/material.dart';

/// Brand seed + semantic accent colors for the Leadora CRM.
///
/// Material 3 derives most of the palette from [brandSeed] via [ColorScheme.fromSeed].
/// The semantic colors below are used for domain state (e.g. payment/booking
/// status chips) and are intentionally kept outside the generated scheme so they
/// stay stable across light/dark.
///
/// Nothing outside this file should declare a raw `Color(0x…)`. Chips and other
/// surfaces tint these through `StatusChip`, which lightens/darkens per
/// brightness — so a token here works on both backgrounds.
class AppColors {
  const AppColors._();

  static const Color brandSeed = Color(0xFF185FA5); // Leadora Blue (web v2)

  // Semantic status colors (used by status chips across booking/payment).
  // Synced to the web v2 design-system token values.
  static const Color success = Color(0xFF2E7D32); // web --success
  static const Color warning = Color(0xFFF59E0B); // web --warning (amber)
  static const Color danger = Color(0xFFDC2626); // web --destructive
  static const Color info = Color(0xFF534AB7); // web --info (System/AI purple)
  static const Color neutral = Color(0xFF64748B); // web --muted-foreground

  // Categorical accents. Only for distinguishing peer categories (task activity
  // types) where the semantic colors above would imply a state they don't have.
  static const Color accentPurple = Color(0xFF7F77DD); // Leadora purple-400
  static const Color accentOrange = Color(0xFFEA580C);
  static const Color accentTeal = Color(0xFF1D9E75); // Leadora reservation teal
}
