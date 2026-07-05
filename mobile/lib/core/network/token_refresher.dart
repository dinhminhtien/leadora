/// Strategy for obtaining a fresh access token after a 401.
///
/// The current backend issues a single 24h token and exposes **no** refresh
/// endpoint (`ApiPaths.refresh` is a reserved seam). [NoOpTokenRefresher] is
/// therefore the wired default: it cannot refresh, so a 401 becomes a clean
/// forced-logout. When the backend grows `POST /auth/refresh`, implement
/// [refresh] to call it and swap the provider override — nothing else changes.
abstract class TokenRefresher {
  /// Returns a new access token, or `null` if refresh is impossible/failed
  /// (which the caller treats as "session is dead → log out").
  Future<String?> refresh();
}

class NoOpTokenRefresher implements TokenRefresher {
  const NoOpTokenRefresher();

  @override
  Future<String?> refresh() async => null;
}
