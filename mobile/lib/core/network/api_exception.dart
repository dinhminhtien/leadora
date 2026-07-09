/// Typed application exception hierarchy.
///
/// Every failure surfaced to the presentation layer is one of these — the UI
/// never sees a raw [DioException] or a stray [FormatException]. The mapping
/// from transport errors happens once, in [DioErrorMapper].
///
/// Design notes:
///   * [message] is always safe to show to an end user (already localized-ish /
///     de-jargoned). Technical detail goes in [debugDetail] which is logged but
///     never rendered.
///   * [errorCode] mirrors the backend `ApiResponse.errorCode` (e.g.
///     `LEAD_NOT_FOUND`) so features can branch on domain errors without string
///     matching the message.
///   * [fieldErrors] carries per-field validation messages for form binding.
library;

sealed class AppException implements Exception {
  const AppException(
    this.message, {
    this.errorCode,
    this.debugDetail,
    this.statusCode,
  });

  /// User-safe, human-readable message.
  final String message;

  /// Backend business error code, when present (`ApiResponse.errorCode`).
  final String? errorCode;

  /// Technical detail for logs/Crashlytics — never shown in the UI.
  final String? debugDetail;

  /// HTTP status, when the error originated from a response.
  final int? statusCode;

  @override
  String toString() =>
      '$runtimeType(status: $statusCode, code: $errorCode, message: $message)';
}

/// No connectivity, DNS failure, connection refused, socket closed.
class NetworkException extends AppException {
  const NetworkException({
    String message = 'No internet connection. Please check your network.',
    super.debugDetail,
  }) : super(message);
}

/// Request/response exceeded the configured timeout.
class TimeoutException extends AppException {
  const TimeoutException({
    String message = 'The request took too long. Please try again.',
    super.debugDetail,
  }) : super(message);
}

/// The caller cancelled the request (e.g. widget disposed, debounced search).
/// Presentation layers generally ignore this rather than showing an error.
class RequestCancelledException extends AppException {
  const RequestCancelledException({super.debugDetail})
    : super('Request cancelled');
}

/// 401 — token missing/expired/invalid. Triggers the global logout flow.
class UnauthorizedException extends AppException {
  const UnauthorizedException({
    String message = 'Your session has expired. Please sign in again.',
    super.errorCode,
    super.debugDetail,
  }) : super(message, statusCode: 401);
}

/// 403 — authenticated but lacking the required permission/role.
class ForbiddenException extends AppException {
  const ForbiddenException({
    String message = "You don't have permission to do that.",
    super.errorCode,
    super.debugDetail,
  }) : super(message, statusCode: 403);
}

/// 404 — resource not found.
class NotFoundException extends AppException {
  const NotFoundException({
    String message = 'The requested item could not be found.',
    super.errorCode,
    super.debugDetail,
  }) : super(message, statusCode: 404);
}

/// 400/422 — request rejected by validation. [fieldErrors] maps field → message
/// so forms can highlight the offending inputs.
class ValidationException extends AppException {
  const ValidationException({
    required String message,
    this.fieldErrors = const {},
    super.errorCode,
    super.debugDetail,
    super.statusCode,
  }) : super(message);

  final Map<String, String> fieldErrors;
}

/// Any 4xx not covered above (409 conflict, 429 rate-limit, etc.).
class ApiException extends AppException {
  const ApiException({
    required String message,
    this.details,
    super.errorCode,
    super.debugDetail,
    super.statusCode,
  }) : super(message);

  /// Machine-readable payload from `ApiResponse.details`, when the backend
  /// attaches one (e.g. `DUPLICATE_LEAD` carries the existing lead's id so
  /// the UI can link to it). Unlike [debugDetail], this may drive UI flow.
  final String? details;
}

/// 5xx — server-side failure. Retriable.
class ServerException extends AppException {
  const ServerException({
    String message = 'Something went wrong on our end. Please try again later.',
    super.errorCode,
    super.debugDetail,
    super.statusCode,
  }) : super(message);
}

/// Response body could not be parsed into the expected shape.
class SerializationException extends AppException {
  const SerializationException({super.debugDetail})
    : super('Received an unexpected response from the server.');
}

/// Fallback for anything unclassified.
class UnknownException extends AppException {
  const UnknownException({
    String message = 'An unexpected error occurred.',
    super.debugDetail,
  }) : super(message);
}
