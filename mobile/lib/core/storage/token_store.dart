import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../constants/storage_keys.dart';
import 'secure_storage.dart';

/// Owns the JWT access token lifecycle.
///
/// The backend issues a single 24h HS256 access token and exposes **no**
/// refresh endpoint (see `ApiPaths.refresh`), so there is intentionally no
/// refresh-token persistence wired here yet — the field exists in
/// [StorageKeys] for when the backend grows one. Until then, expiry is handled
/// by the 401 → logout flow in [RefreshInterceptor].
///
/// An in-memory cache mirrors secure storage so the auth interceptor can read
/// the token synchronously on the hot path without an async Keychain hit per
/// request; it is kept in sync on every write/clear.
class TokenStore {
  TokenStore(this._secure);

  final SecureStorage _secure;

  String? _cachedAccessToken;

  /// Synchronous access for interceptors. Populated by [restore] at boot and
  /// on every [saveAccessToken]. May be null before [restore] completes.
  String? get accessTokenSync => _cachedAccessToken;

  bool get hasToken => _cachedAccessToken != null;

  /// Load the persisted token into the in-memory cache. Call once at startup.
  Future<String?> restore() async {
    _cachedAccessToken = await _secure.read(StorageKeys.accessToken);
    return _cachedAccessToken;
  }

  Future<void> saveAccessToken(String token) async {
    _cachedAccessToken = token;
    await _secure.write(StorageKeys.accessToken, token);
  }

  Future<void> clear() async {
    _cachedAccessToken = null;
    await _secure.delete(StorageKeys.accessToken);
    await _secure.delete(StorageKeys.refreshToken);
  }
}

final tokenStoreProvider = Provider<TokenStore>((ref) {
  return TokenStore(ref.watch(secureStorageProvider));
});
