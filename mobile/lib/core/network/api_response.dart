import 'api_exception.dart';

/// Dart mirror of the backend `common.response.ApiResponse<T>`:
///
/// ```json
/// { "success": true, "message": "...", "errorCode": null,
///   "details": null, "data": { ... }, "timestamp": "2026-07-04T..." }
/// ```
///
/// Repositories call [ApiResponse.parse] with a `data` decoder and get back a
/// typed envelope; [dataOrThrow] unwraps the payload or throws a typed
/// [AppException] when the server reported `success: false`.
class ApiResponse<T> {
  const ApiResponse({
    required this.success,
    required this.message,
    required this.data,
    this.errorCode,
    this.details,
    this.timestamp,
  });

  final bool success;
  final String? message;
  final String? errorCode;
  final String? details;
  final T? data;
  final String? timestamp;

  /// Parse a decoded JSON map into a typed envelope.
  ///
  /// [decodeData] converts the raw `data` node into `T`. It is only invoked
  /// when `data` is non-null, so `Void`/no-content responses (login-less
  /// endpoints like logout) parse cleanly with `T == void`/`Object?`.
  factory ApiResponse.parse(
    Object? body,
    T Function(Object? data) decodeData,
  ) {
    if (body is! Map<String, dynamic>) {
      throw SerializationException(
        debugDetail: 'Expected a JSON object envelope, got ${body.runtimeType}',
      );
    }
    final rawData = body['data'];
    return ApiResponse<T>(
      success: body['success'] as bool? ?? false,
      message: body['message'] as String?,
      errorCode: body['errorCode'] as String?,
      details: body['details'] as String?,
      data: rawData == null ? null : decodeData(rawData),
      timestamp: body['timestamp'] as String?,
    );
  }

  /// Unwrap [data] or throw. Use for endpoints that must return a payload.
  T dataOrThrow() {
    if (!success) {
      throw ApiException(
        message: message ?? 'Request failed.',
        errorCode: errorCode,
        debugDetail: details,
      );
    }
    final value = data;
    if (value == null) {
      throw const SerializationException(
        debugDetail: 'success=true but data was null',
      );
    }
    return value;
  }
}
