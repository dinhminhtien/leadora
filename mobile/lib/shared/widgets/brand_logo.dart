import 'package:flutter/material.dart';

import '../../core/theme/app_dimens.dart';

/// The official Leadora brand mark — the same `logo1.jpg` asset the website
/// uses (bundled as `assets/images/logo.png`), clipped to the app's radius
/// scale so it reads as one identity across splash, login and headers.
class BrandLogo extends StatelessWidget {
  const BrandLogo({super.key, this.size = 64, this.radius});

  /// Square edge length in dp.
  final double size;

  /// Corner radius override; defaults to a proportional soft radius.
  final double? radius;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius ?? (size >= 56 ? AppRadii.xl : AppRadii.sm)),
      child: Image.asset(
        'assets/images/logo.png',
        width: size,
        height: size,
        fit: BoxFit.cover,
        // Never let a missing asset break a screen — fall back to the old mark.
        errorBuilder: (context, _, _) => Container(
          width: size,
          height: size,
          color: Theme.of(context).colorScheme.primaryContainer,
          child: Icon(Icons.hub_rounded,
              size: size * 0.5, color: Theme.of(context).colorScheme.primary),
        ),
      ),
    );
  }
}
