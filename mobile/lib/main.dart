import 'bootstrap.dart';

/// Single entrypoint. Flavor selection is done via the build config, not code:
///
///   flutter run       --dart-define-from-file=config/dev.json
///   flutter run       --dart-define-from-file=config/staging.json
///   flutter build apk --dart-define-from-file=config/prod.json
///
/// See `core/config/env.dart` for how the injected values are read.
Future<void> main() => bootstrap();
