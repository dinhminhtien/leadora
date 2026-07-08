import 'package:dio/dio.dart';

import 'api_exception.dart';

/// Single choke point that turns any [DioException] into a typed
/// [AppException]. Interceptors and repositories funnel through here so error
/// handling is defined exactly once.
///
/// Backend error bodies follow the `ApiResponse` envelope, so when a response
/// is present we mine `message` / `errorCode` / `details` from it and prefer
/// the server's (already user-facing) message over a generic one.
class DioErrorMapper {
  const DioErrorMapper._();

  static AppException map(DioException e) {
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.transformTimeout:
        return TimeoutException(debugDetail: e.message);

      case DioExceptionType.cancel:
        return RequestCancelledException(debugDetail: e.message);

      case DioExceptionType.connectionError:
        return NetworkException(debugDetail: e.message);

      case DioExceptionType.badCertificate:
        return ApiException(
          message: 'Secure connection failed.',
          debugDetail: e.message,
        );

      case DioExceptionType.badResponse:
        return _mapResponse(e);

      case DioExceptionType.unknown:
        // Socket errors surface here on some platforms.
        final err = e.error;
        if (err != null && err.toString().contains('SocketException')) {
          return NetworkException(debugDetail: err.toString());
        }
        return UnknownException(debugDetail: e.message ?? e.error?.toString());
    }
  }

  static AppException _mapResponse(DioException e) {
    final response = e.response;
    final status = response?.statusCode ?? 0;
    final body = response?.data;

    final _ServerError parsed = _ServerError.from(body);
    final message = parsed.message;
    final code = parsed.errorCode;
    final detail = parsed.details ?? e.message;

    switch (status) {
      case 400:
      case 422:
        return ValidationException(
          message: message ?? 'Please check the highlighted fields.',
          fieldErrors: parsed.fieldErrors,
          errorCode: code,
          debugDetail: detail,
          statusCode: status,
        );
      case 401:
        return UnauthorizedException(
          message: message ?? 'Your session has expired. Please sign in again.',
          errorCode: code,
          debugDetail: detail,
        );
      case 403:
        return ForbiddenException(
          message: message ?? "You don't have permission to do that.",
          errorCode: code,
          debugDetail: detail,
        );
      case 404:
        return NotFoundException(
          message: message ?? 'The requested item could not be found.',
          errorCode: code,
          debugDetail: detail,
        );
      default:
        if (status >= 500) {
          return ServerException(
            message: message ??
                'Something went wrong on our end. Please try again later.',
            errorCode: code,
            debugDetail: detail,
            statusCode: status,
          );
        }
        return ApiException(
          message: message ?? 'Request failed ($status).',
          errorCode: code,
          details: parsed.details,
          debugDetail: detail,
          statusCode: status,
        );
    }
  }
}

/// Best-effort extraction of the backend error envelope from a response body,
/// tolerant of non-JSON or unexpected shapes (e.g. an HTML gateway page).
class _ServerError {
  const _ServerError({
    this.message,
    this.errorCode,
    this.details,
    this.fieldErrors = const {},
  });

  final String? message;
  final String? errorCode;
  final String? details;
  final Map<String, String> fieldErrors;

  factory _ServerError.from(Object? body) {
    if (body is! Map) return const _ServerError();

    // Some validation handlers return a { field: message } map under
    // `errors`/`fieldErrors`/`data`; capture whichever is present.
    final fieldErrors = <String, String>{};
    for (final key in const ['fieldErrors', 'errors']) {
      final raw = body[key];
      if (raw is Map) {
        raw.forEach((k, v) => fieldErrors['$k'] = '$v');
      }
    }

    return _ServerError(
      message: body['message'] as String?,
      errorCode: body['errorCode'] as String?,
      details: body['details'] as String?,
      fieldErrors: fieldErrors,
    );
  }
}
