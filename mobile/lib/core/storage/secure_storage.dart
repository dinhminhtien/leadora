import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Thin, testable wrapper over [FlutterSecureStorage].
///
/// Everything sensitive (tokens, biometric flag) goes through here; it is the
/// only place that touches Keychain / EncryptedSharedPreferences. Exposed as a
/// provider so tests can override it with an in-memory fake.
class SecureStorage {
  SecureStorage(this._storage);

  final FlutterSecureStorage _storage;

  static const _androidOptions = AndroidOptions(
    encryptedSharedPreferences: true,
  );
  static const _iosOptions = IOSOptions(
    accessibility: KeychainAccessibility.first_unlock,
  );

  Future<String?> read(String key) =>
      _storage.read(key: key, aOptions: _androidOptions, iOptions: _iosOptions);

  Future<void> write(String key, String value) => _storage.write(
    key: key,
    value: value,
    aOptions: _androidOptions,
    iOptions: _iosOptions,
  );

  Future<void> delete(String key) => _storage.delete(
    key: key,
    aOptions: _androidOptions,
    iOptions: _iosOptions,
  );

  /// Wipe all secure entries — used on logout / on-tamper.
  Future<void> deleteAll() =>
      _storage.deleteAll(aOptions: _androidOptions, iOptions: _iosOptions);
}

final secureStorageProvider = Provider<SecureStorage>((ref) {
  return SecureStorage(const FlutterSecureStorage());
});
