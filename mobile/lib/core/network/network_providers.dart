import 'package:dio/dio.dart';
import 'package:dio_cache_interceptor/dio_cache_interceptor.dart';
import 'package:dio_smart_retry/dio_smart_retry.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/env.dart';
import '../routing/app_session.dart';
import '../storage/token_store.dart';
import 'api_client.dart';
import 'interceptors/auth_interceptor.dart';
import 'interceptors/logging_interceptor.dart';
import 'interceptors/refresh_interceptor.dart';
import 'token_refresher.dart';

/// GET-cache configuration. `hitCacheOnErrorExcept` is the offline-resilience
/// lever: when a GET fails with a transport error (or any status *not* in the
/// list), a previously cached response is served instead of throwing — so a
/// list screen keeps rendering the last-known data when the network drops.
/// Auth failures (401/403) are excluded so we never serve stale data across a
/// permission/session boundary.
final _cacheOptionsProvider = Provider<CacheOptions>((ref) {
  return CacheOptions(
    store: MemCacheStore(),
    policy: CachePolicy.request,
    hitCacheOnErrorExcept: const [401, 403],
    maxStale: const Duration(days: 7),
    priority: CachePriority.normal,
    allowPostMethod: false,
  );
});

/// Strategy used by [RefreshInterceptor] on 401. No-op today (no backend
/// refresh endpoint); override this single provider when one lands.
final tokenRefresherProvider = Provider<TokenRefresher>((ref) {
  return const NoOpTokenRefresher();
});

/// The fully-configured Dio instance. Interceptor order is deliberate:
///   1. Auth      — stamp the Bearer token onto the outbound request.
///   2. Retry     — transparently retry transient network/timeout failures.
///   3. Cache     — serve/refresh GETs; backstop for offline reads.
///   4. Refresh   — catch 401s, single-flight refresh or force logout.
///   5. Logging   — observe the final outcome (with secrets redacted).
final dioProvider = Provider<Dio>((ref) {
  final dio = Dio(
    BaseOptions(
      baseUrl: Env.apiBaseUrl,
      connectTimeout: Duration(milliseconds: Env.connectTimeoutMs),
      receiveTimeout: Duration(milliseconds: Env.receiveTimeoutMs),
      sendTimeout: Duration(milliseconds: Env.connectTimeoutMs),
      contentType: Headers.jsonContentType,
      responseType: ResponseType.json,
      // Default validation (throw on non-2xx). Non-2xx responses still carry
      // their body on `err.response.data`, so DioErrorMapper can mine the
      // ApiResponse envelope and RefreshInterceptor.onError fires on 401.
    ),
  );

  final tokenStore = ref.watch(tokenStoreProvider);
  final session = ref.watch(appSessionProvider);

  dio.interceptors.addAll([
    AuthInterceptor(tokenStore),
    RetryInterceptor(
      dio: dio,
      retries: 3,
      retryDelays: const [
        Duration(milliseconds: 400),
        Duration(milliseconds: 900),
        Duration(seconds: 2),
      ],
    ),
    DioCacheInterceptor(options: ref.watch(_cacheOptionsProvider)),
    RefreshInterceptor(
      dio: dio,
      refresher: ref.watch(tokenRefresherProvider),
      onSessionExpired: () async {
        await tokenStore.clear();
        session.update(AuthStatus.unauthenticated);
      },
    ),
    LoggingInterceptor(enabled: Env.logLevel != 'none' && !Env.isProd),
  ]);

  ref.onDispose(dio.close);
  return dio;
});

/// The typed HTTP facade every repository depends on.
final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(ref.watch(dioProvider));
});
