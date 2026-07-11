import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart' as supabase;

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/app_session.dart';
import '../../../../core/storage/token_store.dart';
import '../../data/auth_repository.dart';
import '../../data/dto/auth_user.dart';

/// Single source of truth for "who is signed in".
///
/// Responsibilities:
///   * boot-time session restore ([build]) — hydrate the token cache from
///     secure storage and, if present, rehydrate the user from `/auth/profile`;
///   * [login] / [logout] mutations, exposing loading/error via [AsyncValue] so
///     the login form can bind to it directly;
///   * driving the router's coarse [AppSession] (the go_router `redirect`
///     watches that, not this provider, keeping routing decoupled from the
///     user model).
///
/// State is `AuthUser?`: the current user, or `null` when signed out.
class AuthController extends AsyncNotifier<AuthUser?> {
  AuthRepository get _repo => ref.read(authRepositoryProvider);
  TokenStore get _tokenStore => ref.read(tokenStoreProvider);
  AppSession get _session => ref.read(appSessionProvider);

  @override
  Future<AuthUser?> build() async {
    // Populate the in-memory token cache so the auth interceptor can attach it.
    final token = await _tokenStore.restore();

    // Start listening to Supabase auth events (e.g. OAuth redirects)
    _initSupabaseListener();

    if (token == null) {
      _session.update(AuthStatus.unauthenticated);
      return null;
    }
    try {
      // Bounded so a slow/unreachable backend can never freeze the splash:
      // whatever the retry interceptor is doing under the hood, we stop waiting
      // after 8s and fall through to the login screen.
      final user = await _repo.fetchProfile().timeout(
        const Duration(seconds: 8),
      );
      _session.update(AuthStatus.authenticated);
      return user;
    } on UnauthorizedException {
      // Token is genuinely dead (401/403) → clear it so we don't loop on it.
      await _tokenStore.clear();
      _session.update(AuthStatus.unauthenticated);
      return null;
    } on ForbiddenException {
      await _tokenStore.clear();
      _session.update(AuthStatus.unauthenticated);
      return null;
    } catch (_) {
      // Network error / timeout / server hiccup: the token may still be valid,
      // so KEEP it and just show login. The next launch (or a manual sign-in)
      // retries. This is what turns an indefinite hang into a fast fallback.
      _session.update(AuthStatus.unauthenticated);
      return null;
    }
  }

  void _initSupabaseListener() {
    supabase.Supabase.instance.client.auth.onAuthStateChange.listen((
      data,
    ) async {
      final session = data.session;
      final event = data.event;
      if (session != null &&
          session.accessToken != _tokenStore.accessTokenSync) {
        if (event == supabase.AuthChangeEvent.signedIn ||
            event == supabase.AuthChangeEvent.initialSession ||
            event == supabase.AuthChangeEvent.tokenRefreshed) {
          state = const AsyncLoading<AuthUser?>().copyWithPrevious(state);
          state = await AsyncValue.guard(() async {
            try {
              final user = await _repo.verifyOAuthSession(session.accessToken);
              await _tokenStore.saveAccessToken(session.accessToken);
              _session.update(AuthStatus.authenticated);
              return user;
            } catch (e) {
              await _repo.logout();
              rethrow;
            }
          });
        }
      }
    });
  }

  /// Sign in. On success flips the session (router redirects to dashboard).
  /// Errors surface as an [AsyncError] the form renders; the previous state is
  /// preserved so the UI doesn't flash empty on a failed attempt.
  Future<void> login({required String email, required String password}) async {
    state = const AsyncLoading<AuthUser?>().copyWithPrevious(state);
    state = await AsyncValue.guard(() async {
      final user = await _repo.login(email: email, password: password);
      _session.update(AuthStatus.authenticated);
      return user;
    });
  }

  /// Sign in with Google.
  Future<void> loginWithGoogle() async {
    state = const AsyncLoading<AuthUser?>().copyWithPrevious(state);
    try {
      await _repo.loginWithGoogle();
    } catch (e) {
      state = AsyncError(e, StackTrace.current);
    }
  }

  /// Refresh the signed-in user's display fields after a profile edit — the
  /// mobile mirror of the web auth store's `updateUserFields`, so the
  /// dashboard greeting/avatar update without a re-login.
  void updateUserFields({
    String? name,
    String? avatarUrl,
    bool clearAvatar = false,
  }) {
    final user = state.valueOrNull;
    if (user == null) return;
    state = AsyncData(
      user.copyWith(name: name, avatarUrl: avatarUrl, clearAvatar: clearAvatar),
    );
  }

  /// Sign out. Always resolves the local session to unauthenticated even if the
  /// server revocation call fails.
  Future<void> logout() async {
    await _repo.logout();
    _session.update(AuthStatus.unauthenticated);
    state = const AsyncData<AuthUser?>(null);
  }
}

final authControllerProvider = AsyncNotifierProvider<AuthController, AuthUser?>(
  AuthController.new,
);

/// Convenience accessor for the current user (null while loading/signed out).
final currentUserProvider = Provider<AuthUser?>((ref) {
  return ref.watch(authControllerProvider).valueOrNull;
});
