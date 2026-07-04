/// Compile-time environment configuration.
///
/// Values are injected via `--dart-define-from-file=config/<flavor>.json`.
/// We deliberately use [String.fromEnvironment] (const) rather than a runtime
/// `.env` file so that:
///   * secrets/URLs are baked per-build and cannot be swapped at runtime,
///   * tree-shaking can const-fold flags in release builds,
///   * there is a single source of truth per flavor (dev/staging/prod).
///
/// Run:
///   flutter run --dart-define-from-file=config/dev.json
///   flutter build apk --dart-define-from-file=config/prod.json
library;

enum Flavor { dev, staging, prod }

class Env {
  const Env._();

  static const String _flavorRaw =
      String.fromEnvironment('FLAVOR', defaultValue: 'dev');

  static Flavor get flavor => switch (_flavorRaw) {
        'prod' => Flavor.prod,
        'staging' => Flavor.staging,
        _ => Flavor.dev,
      };

  static const String appName =
      String.fromEnvironment('APP_NAME', defaultValue: 'Leadora (Dev)');

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8085/api/v1',
  );

  static const int connectTimeoutMs =
      int.fromEnvironment('CONNECT_TIMEOUT_MS', defaultValue: 20000);

  static const int receiveTimeoutMs =
      int.fromEnvironment('RECEIVE_TIMEOUT_MS', defaultValue: 20000);

  static const bool enableCrashlytics =
      bool.fromEnvironment('ENABLE_CRASHLYTICS', defaultValue: false);

  static const String logLevel =
      String.fromEnvironment('LOG_LEVEL', defaultValue: 'verbose');

  static bool get isDev => flavor == Flavor.dev;
  static bool get isProd => flavor == Flavor.prod;
}
