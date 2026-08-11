import 'dart:ui' as ui;

import 'package:flutter/material.dart';

/// "Liquid glass" surfaces — a blurred, translucent panel over the content
/// behind it.
///
/// **Why this is a shared primitive rather than a `BackdropFilter` per screen.**
/// Glass is easy to get wrong in ways that only show up on someone else's
/// device: unreadable text over a busy background, a panel that changes height
/// when the blur layer is added, or a 60→30fps drop on a mid-range Android.
/// Centralising it means those trade-offs are decided once, here, with the
/// reasoning attached.
///
/// ## Three rules this enforces
///
/// 1. **It never changes layout.** The blur is painted *behind* the child via a
///    [Stack] whose glass layer is [Positioned.fill] — a non-participating
///    child. The widget's size is whatever the child's size already was, so
///    adding or removing glass cannot shift a single pixel. (A naive
///    implementation wraps the child in a `BackdropFilter` + `Padding`, which
///    silently reflows every screen it touches.)
///
/// 2. **Text stays legible.** Blur alone does not guarantee contrast — it
///    averages whatever is behind, so pale content over a pale photo still
///    fails. Every glass surface therefore paints an opaque-enough *tint* over
///    the blur, keyed to the theme's surface colour. [opacity] is clamped to a
///    floor so a caller cannot dial contrast below the readable range.
///
/// 3. **It respects the user's accessibility settings.** When the platform asks
///    for reduced transparency (`MediaQuery.highContrast`) or reduced motion,
///    the blur is dropped and a solid surface is used instead. Blur is a
///    decoration; readability is not.
///
/// ## Font scaling
///
/// Nothing here constrains height or sets a fixed font size, so a 200% system
/// text scale grows the child exactly as it would without glass. Callers must
/// still avoid fixed-height boxes around text — glass does not rescue that.
class GlassSurface extends StatelessWidget {
  const GlassSurface({
    super.key,
    required this.child,
    this.borderRadius,
    this.blur = 18,
    this.opacity = 0.72,
    this.showBorder = true,
    this.tint,
  });

  final Widget child;
  final BorderRadius? borderRadius;

  /// Gaussian sigma. Above ~24 the effect stops reading as glass and starts
  /// reading as a broken screenshot, so it is capped.
  final double blur;

  /// Tint opacity over the blur. Floored at [_minOpacity] for contrast.
  final double opacity;

  final bool showBorder;

  /// Overrides the themed surface tint — for branded headers.
  final Color? tint;

  /// Below this the tint stops carrying text contrast against arbitrary
  /// backgrounds. Determined by checking body text against the worst case
  /// (white text over a light photo), not by taste.
  static const double _minOpacity = 0.55;

  static const double _maxBlur = 24;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final media = MediaQuery.of(context);
    final radius = borderRadius ?? BorderRadius.circular(16);

    final base = tint ?? theme.colorScheme.surface;

    // Accessibility settings win over decoration. `highContrast` is the closest
    // signal Flutter surfaces to iOS "Reduce Transparency"; `disableAnimations`
    // is set by the same class of user preference and by many device
    // battery-saver modes, where a full-screen blur is exactly the wrong cost.
    final wantsSolid = media.highContrast || media.disableAnimations;

    if (wantsSolid) {
      return DecoratedBox(
        decoration: BoxDecoration(
          color: base,
          borderRadius: radius,
          border: showBorder
              ? Border.all(color: theme.colorScheme.outlineVariant)
              : null,
        ),
        child: child,
      );
    }

    final effectiveOpacity = opacity.clamp(_minOpacity, 1.0);
    final effectiveBlur = blur.clamp(0.0, _maxBlur);

    return Stack(
      children: [
        // The glass itself — `Positioned.fill` means this layer takes its size
        // *from* the child rather than contributing to it, which is what keeps
        // the widget layout-neutral.
        Positioned.fill(
          child: ClipRRect(
            borderRadius: radius,
            child: BackdropFilter(
              filter: ui.ImageFilter.blur(
                sigmaX: effectiveBlur,
                sigmaY: effectiveBlur,
              ),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: base.withValues(alpha: effectiveOpacity),
                  borderRadius: radius,
                  border: showBorder
                      ? Border.all(
                          // A hairline lighter than the fill is what reads as a
                          // glass *edge*; without it the panel looks like flat
                          // translucency.
                          color: theme.colorScheme.onSurface.withValues(
                            alpha: theme.brightness == Brightness.dark
                                ? 0.10
                                : 0.06,
                          ),
                        )
                      : null,
                ),
              ),
            ),
          ),
        ),
        child,
      ],
    );
  }
}

/// A glass card: [GlassSurface] plus the padding and shape our cards use, so a
/// screen does not re-derive either.
class GlassCard extends StatelessWidget {
  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.margin,
    this.onTap,
    this.borderRadius,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final VoidCallback? onTap;
  final BorderRadius? borderRadius;

  @override
  Widget build(BuildContext context) {
    final radius = borderRadius ?? BorderRadius.circular(16);

    final content = GlassSurface(
      borderRadius: radius,
      child: Padding(padding: padding, child: child),
    );

    return Padding(
      padding: margin ?? EdgeInsets.zero,
      // InkWell sits above the glass so the ripple is visible, and is clipped to
      // the same radius so it cannot bleed past the panel edge.
      child: onTap == null
          ? content
          : Material(
              color: Colors.transparent,
              borderRadius: radius,
              clipBehavior: Clip.antiAlias,
              child: InkWell(onTap: onTap, child: content),
            ),
    );
  }
}

/// Frosted app bar / bottom bar backdrop.
///
/// Sized by its child exactly like [GlassSurface]; pass the same height the
/// opaque bar had and nothing moves.
class GlassBar extends StatelessWidget {
  const GlassBar({
    super.key,
    required this.child,
    this.blur = 20,
    this.opacity = 0.80,
  });

  final Widget child;
  final double blur;
  final double opacity;

  @override
  Widget build(BuildContext context) {
    return GlassSurface(
      borderRadius: BorderRadius.zero,
      blur: blur,
      opacity: opacity,
      showBorder: false,
      child: child,
    );
  }
}
