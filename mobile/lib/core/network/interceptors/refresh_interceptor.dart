import 'package:dio/dio.dart';
import 'package:mutex/mutex.dart';

import '../../constants/api_paths.dart';
import '../token_refresher.dart';

/// Handles 401 responses with a single-flight refresh, then either replays the
/// original request or forces a logout.
///
/// Why the mutex: when a screen fires several requests at once and the token
/// has expired, they all get 401 nearly simultaneously. Without a lock each
/// would independently try to refresh, producing a stampede (and, with a real
/// rotating-refresh-token backend, invalidating each other). The [Mutex]
/// ensures exactly one refresh runs; requests that arrive mid-refresh await the
/// same result and then replay with the new token.
///
/// On this backend [TokenRefresher] is a no-op (no refresh endpoint), so a 401
/// deterministically resolves to [onSessionExpired] → global logout. The
/// machinery is real and correct for the day a refresh endpoint exists.
class RefreshInterceptor extends QueuedInterceptor {
  RefreshInterceptor({
    required Dio dio,
    required TokenRefresher refresher,
    required Future<void> Function() onSessionExpired,
  }) : _dio = dio,
       _refresher = refresher,
       _onSessionExpired = onSessionExpired;

  final Dio _dio;
  final TokenRefresher _refresher;
  final Future<void> Function() _onSessionExpired;
  final _lock = Mutex();

  static bool _isAuthEndpoint(String path) =>
      path.contains(ApiPaths.login) ||
      path.contains(ApiPaths.refresh) ||
      path.contains(ApiPaths.forgotPassword) ||
      path.contains(ApiPaths.resetPassword);

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final response = err.response;
    final isUnauthorized = response?.statusCode == 401;

    // A 401 from login itself is a real credential failure — let it through.
    if (!isUnauthorized || _isAuthEndpoint(err.requestOptions.path)) {
      return handler.next(err);
    }

    // Prevent an already-retried request from looping forever.
    if (err.requestOptions.extra['__retried__'] == true) {
      await _onSessionExpired();
      return handler.next(err);
    }

    String? newToken;
    try {
      // Single-flight: only the first caller performs the refresh; the rest
      // block here and reuse whatever token it produced.
      newToken = await _lock.protect(() => _refresher.refresh());
    } catch (_) {
      newToken = null;
    }

    if (newToken == null) {
      await _onSessionExpired();
      return handler.next(err);
    }

    // Replay the original request with the refreshed token.
    try {
      final options = err.requestOptions
        ..headers['Authorization'] = 'Bearer $newToken'
        ..extra['__retried__'] = true;
      final retried = await _dio.fetch<dynamic>(options);
      return handler.resolve(retried);
    } on DioException catch (retryErr) {
      return handler.next(retryErr);
    }
  }
}
