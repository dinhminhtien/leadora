/// Keys for local persistence.
///
/// [Secure] keys go to flutter_secure_storage (Keychain / EncryptedSharedPrefs).
/// [Prefs] keys go to shared_preferences (non-sensitive, plaintext).
class StorageKeys {
  const StorageKeys._();

  // Secure (credentials / tokens) — never logged, cleared on logout.
  static const String accessToken = 'secure.access_token';
  static const String refreshToken = 'secure.refresh_token';
  static const String biometricEnabled = 'secure.biometric_enabled';

  // Prefs (non-sensitive UX state).
  static const String themeMode = 'prefs.theme_mode';
  static const String locale = 'prefs.locale';
  static const String onboardingSeen = 'prefs.onboarding_seen';

  // Offline retry queue (persisted JSON list of pending mutations).
  static const String retryQueue = 'prefs.retry_queue';
}
