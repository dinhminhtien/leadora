import 'package:dio/dio.dart';
import 'package:logger/logger.dart';

/// Structured request/response/error logging with secret redaction.
///
/// Never logs the `Authorization` header value or password-bearing bodies —
/// those are masked before they reach the log sink (and, later, Crashlytics
/// breadcrumbs). Disabled in release unless explicitly enabled by the flavor.
class LoggingInterceptor extends Interceptor {
  LoggingInterceptor({required bool enabled})
    : _enabled = enabled,
      _logger = Logger(
        printer: PrettyPrinter(
          methodCount: 0,
          errorMethodCount: 4,
          colors: true,
          printEmojis: false,
        ),
      );

  final bool _enabled;
  final Logger _logger;

  static const _sensitiveHeaders = {'authorization', 'cookie', 'set-cookie'};
  static const _sensitiveBodyKeys = {'password', 'newPassword', 'token'};

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (_enabled) {
      _logger.i(
        '→ ${options.method} ${options.uri}\n'
        'headers: ${_redactHeaders(options.headers)}\n'
        'body: ${_redactBody(options.data)}',
      );
    }
    handler.next(options);
  }

  @override
  void onResponse(Response response, ResponseInterceptorHandler handler) {
    if (_enabled) {
      _logger.d(
        '← ${response.statusCode} ${response.requestOptions.method} '
        '${response.requestOptions.uri}',
      );
    }
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (_enabled) {
      _logger.w(
        '✕ ${err.response?.statusCode ?? '-'} '
        '${err.requestOptions.method} ${err.requestOptions.uri}\n'
        'type: ${err.type}\n'
        'body: ${_redactBody(err.response?.data)}',
      );
    }
    handler.next(err);
  }

  Map<String, Object?> _redactHeaders(Map<String, Object?> headers) {
    return headers.map(
      (k, v) =>
          MapEntry(k, _sensitiveHeaders.contains(k.toLowerCase()) ? '***' : v),
    );
  }

  Object? _redactBody(Object? body) {
    if (body is Map) {
      return body.map(
        (k, v) => MapEntry(k, _sensitiveBodyKeys.contains('$k') ? '***' : v),
      );
    }
    return body;
  }
}
