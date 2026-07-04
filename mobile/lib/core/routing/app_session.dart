import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Coarse authentication status the router guards on.
///
/// [unknown] is the boot state while we probe secure storage for a token; the
/// splash route is shown until it resolves, preventing a login-screen flash for
/// already-authenticated users.
enum AuthStatus { unknown, authenticated, unauthenticated }

/// A [ChangeNotifier] mirror of auth status, used as go_router's
/// `refreshListenable` so navigation re-evaluates the moment auth changes.
///
/// This lives in `core` (not the auth feature) because routing is a
/// cross-cutting concern; the auth feature *drives* it via [update].
class AppSession extends ChangeNotifier {
  AuthStatus _status = AuthStatus.unknown;
  AuthStatus get status => _status;

  bool get isAuthenticated => _status == AuthStatus.authenticated;
  bool get isResolved => _status != AuthStatus.unknown;

  void update(AuthStatus next) {
    if (_status == next) return;
    _status = next;
    notifyListeners();
  }
}

/// Single app-wide session. Kept alive for the whole app lifetime.
final appSessionProvider = Provider<AppSession>((ref) {
  final session = AppSession();
  ref.onDispose(session.dispose);
  return session;
});
