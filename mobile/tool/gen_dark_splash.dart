// Generates the dark-mode native splash logo from the light source.
//
// Mirrors the in-app BrandLogo dark treatment (web `dark:invert` parity):
// invert the mark, then screen-blend with the dark splash background color so
// the former white box melts into it seamlessly.
//
// Run from mobile/:  dart run tool/gen_dark_splash.dart
// Then regenerate:   dart run flutter_native_splash:create
import 'dart:io';

import 'package:image/image.dart' as img;

/// Must match `flutter_native_splash.color_dark` in pubspec.yaml.
const _darkBackground = (r: 0x0B, g: 0x12, b: 0x20);

void main() {
  final source = img.decodePng(
    File('assets/branding/splash_logo.png').readAsBytesSync(),
  );
  if (source == null) {
    stderr.writeln('Could not decode assets/branding/splash_logo.png');
    exit(1);
  }

  int screen(int a, int b) => 255 - ((255 - a) * (255 - b) ~/ 255);

  for (final p in source) {
    // Invert (CSS filter: invert(1)) …
    final r = 255 - p.r.toInt();
    final g = 255 - p.g.toInt();
    final b = 255 - p.b.toInt();
    // … then screen-blend with the dark splash background.
    p
      ..r = screen(r, _darkBackground.r)
      ..g = screen(g, _darkBackground.g)
      ..b = screen(b, _darkBackground.b);
  }

  File(
    'assets/branding/splash_logo_dark.png',
  ).writeAsBytesSync(img.encodePng(source));
  stdout.writeln(
    'Wrote assets/branding/splash_logo_dark.png '
    '(${source.width}x${source.height})',
  );
}
