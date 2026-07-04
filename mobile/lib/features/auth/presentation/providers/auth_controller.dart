import 'package:flutter_riverpod/flutter_riverpod.dart';

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
    if (token == null) {
      _session.update(AuthStatus.unauthenticated);
      return null;
    }
    try {
      final user = await _repo.fetchProfile();
      _session.update(AuthStatus.authenticated);
      return user;
    } catch (_) {
      // Token present but rejected/expired or profile unreachable → treat as
      // signed out. Clear the dead token so we don't loop on it next boot.
      await _tokenStore.clear();
      _session.update(AuthStatus.unauthenticated);
      return null;
    }
  }

  /// Sign in. On success flips the session (router redirects to dashboard).
  /// Errors surface as an [AsyncError] the form renders; the previous state is
  /// preserved so the UI doesn't flash empty on a failed attempt.
  Future<void> login({
    required String email,
    required String password,
  }) async {
    state = const AsyncLoading<AuthUser?>().copyWithPrevious(state);
    state = await AsyncValue.guard(() async {
      final user = await _repo.login(email: email, password: password);
      _session.update(AuthStatus.authenticated);
      return user;
    });
  }

  /// Sign out. Always resolves the local session to unauthenticated even if the
  /// server revocation call fails.
  Future<void> logout() async {
    await _repo.logout();
    _session.update(AuthStatus.unauthenticated);
    state = const AsyncData<AuthUser?>(null);
  }
}

final authControllerProvider =
    AsyncNotifierProvider<AuthController, AuthUser?>(AuthController.new);

/// Convenience accessor for the current user (null while loading/signed out).
final currentUserProvider = Provider<AuthUser?>((ref) {
  return ref.watch(authControllerProvider).valueOrNull;
});
