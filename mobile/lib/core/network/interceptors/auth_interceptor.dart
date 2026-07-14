import 'package:dio/dio.dart';

import '../../constants/api_paths.dart';
import '../../storage/token_store.dart';

/// Injects `Authorization: Bearer <token>` on every outbound request except the
/// public auth endpoints (login / forgot / reset), which must go out
/// unauthenticated.
///
/// Reads from [TokenStore.accessTokenSync] (in-memory mirror) so this stays
/// synchronous and adds no latency to the request path.
class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._tokenStore);

  final TokenStore _tokenStore;

  static const _publicPaths = <String>{
    ApiPaths.login,
    ApiPaths.forgotPassword,
    ApiPaths.resetPassword,
  };

  bool _isPublic(String path) => _publicPaths.any(path.contains);

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (!_isPublic(options.path)) {
      final token = _tokenStore.accessTokenSync;
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
    }
    handler.next(options);
  }
}
