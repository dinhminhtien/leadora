import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:supabase_flutter/supabase_flutter.dart' as supabase;

import '../../../core/constants/api_paths.dart';
import '../../../core/constants/storage_keys.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../../core/storage/secure_storage.dart';
import '../../../core/storage/token_store.dart';
import 'dto/auth_user.dart';

/// Owns all auth API calls and the persistence of the resulting session.
///
/// Keeps the [ApiClient] and the [TokenStore] together so "log in" atomically
/// means "call the API, then persist the token and cache the user" — the
/// controller above it never juggles storage directly.
class AuthRepository {
  AuthRepository({
    required ApiClient client,
    required TokenStore tokenStore,
    required SecureStorage secureStorage,
    GoogleSignIn? googleSignIn,
    supabase.SupabaseClient? supabaseClient,
  }) : _client = client,
       _tokenStore = tokenStore,
       _secure = secureStorage,
       _googleSignIn =
           googleSignIn ?? GoogleSignIn(scopes: ['email', 'profile']),
       _supabaseClient = supabaseClient ?? supabase.Supabase.instance.client;

  final ApiClient _client;
  final TokenStore _tokenStore;
  final SecureStorage _secure;
  final GoogleSignIn _googleSignIn;
  final supabase.SupabaseClient _supabaseClient;

  /// `POST /auth/login` → persists the access token, returns the user.
  Future<AuthUser> login({
    required String email,
    required String password,
  }) async {
    final result = await _client.post<LoginResult>(
      ApiPaths.login,
      data: {'email': email, 'password': password},
      decode: (data) => LoginResult.fromJson(data as Map<String, dynamic>),
    );
    await _tokenStore.saveAccessToken(result.accessToken);
    return result.user;
  }

  /// Starts Supabase's OAuth2 flow for the Google provider.
  Future<void> loginWithGoogle() async {
    final String redirectTo;
    if (kIsWeb) {
      // Dynamic local/production web origin (e.g., http://localhost:1000/)
      redirectTo = '${Uri.base.origin}/';
    } else {
      redirectTo = 'com.novax.leadora-mobile://login-callback';
    }

    await _supabaseClient.auth.signInWithOAuth(
      supabase.OAuthProvider.google,
      redirectTo: redirectTo,
    );
  }

  /// Verifies the OAuth session token with the Spring Boot backend.
  Future<AuthUser> verifyOAuthSession(String token) async {
    return _client.get<AuthUser>(
      ApiPaths.oauthVerify,
      headers: {'Authorization': 'Bearer $token'},
      decode: (data) => AuthUser.fromJson(data as Map<String, dynamic>),
    );
  }

  /// `GET /auth/profile` — used on session restore to rehydrate the current
  /// user from a still-valid persisted token.
  Future<AuthUser> fetchProfile() {
    return _client.get<AuthUser>(
      ApiPaths.profile,
      decode: (data) => AuthUser.fromJson(data as Map<String, dynamic>),
    );
  }

  /// `POST /auth/logout` (blacklists the token server-side), then clears local
  /// secrets regardless of the network result — logout must always succeed
  /// locally even if the server is unreachable.
  Future<void> logout() async {
    try {
      await _client.post<void>(ApiPaths.logout, decode: (_) {});
    } catch (_) {
      // Best-effort server revocation; ignore failures.
    } finally {
      try {
        await _supabaseClient.auth.signOut();
        await _googleSignIn.signOut();
      } catch (_) {}
      await _tokenStore.clear();
      await _secure.delete(StorageKeys.biometricEnabled);
    }
  }

  Future<void> forgotPassword(String email) {
    return _client.post<void>(
      ApiPaths.forgotPassword,
      data: {'email': email, 'clientType': 'mobile'},
      decode: (_) {},
    );
  }

  Future<void> resetPassword({
    required String token,
    required String password,
  }) {
    return _client.post<void>(
      ApiPaths.resetPassword,
      data: {'token': token, 'password': password},
      decode: (_) {},
    );
  }

  bool get hasPersistedToken => _tokenStore.hasToken;
}

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    client: ref.watch(apiClientProvider),
    tokenStore: ref.watch(tokenStoreProvider),
    secureStorage: ref.watch(secureStorageProvider),
  );
});
