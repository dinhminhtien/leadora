import 'package:dio/dio.dart';

import 'api_exception.dart';
import 'api_response.dart';
import 'dio_error_mapper.dart';
import 'pagination_response.dart';

/// The single typed HTTP facade the data layer talks to. UI and providers
/// never touch [Dio] directly — they call repositories, which call this.
///
/// Every method:
///   * unwraps the backend `ApiResponse<T>` envelope,
///   * decodes `data` with a caller-supplied decoder (keeps this class free of
///     any feature model imports),
///   * converts every failure into a typed [AppException] via [DioErrorMapper].
///
/// `decode` receives the raw `data` node (`Object?`) — typically a
/// `Map<String, dynamic>` for objects or a `List` for arrays.
class ApiClient {
  ApiClient(this._dio);

  final Dio _dio;

  /// Escape hatch for the rare case a caller needs the underlying Dio (e.g.
  /// the refresh interceptor replaying a request). Prefer the typed methods.
  Dio get raw => _dio;

  Future<T> get<T>(
    String path, {
    Map<String, dynamic>? query,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) {
    return _request(
      () => _dio.get<dynamic>(path, queryParameters: query, cancelToken: cancelToken),
      decode,
    );
  }

  /// GET a Spring `Page<T>` wrapped in `ApiResponse`.
  Future<PaginationResponse<T>> getPaged<T>(
    String path, {
    Map<String, dynamic>? query,
    required T Function(Object? item) decodeItem,
    CancelToken? cancelToken,
  }) {
    return _request<PaginationResponse<T>>(
      () => _dio.get<dynamic>(path, queryParameters: query, cancelToken: cancelToken),
      (data) => PaginationResponse<T>.parse(data, decodeItem),
    );
  }

  Future<T> post<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? query,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) {
    return _request(
      () => _dio.post<dynamic>(path,
          data: data, queryParameters: query, cancelToken: cancelToken),
      decode,
    );
  }

  Future<T> put<T>(
    String path, {
    Object? data,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) {
    return _request(
      () => _dio.put<dynamic>(path, data: data, cancelToken: cancelToken),
      decode,
    );
  }

  Future<T> patch<T>(
    String path, {
    Object? data,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) {
    return _request(
      () => _dio.patch<dynamic>(path, data: data, cancelToken: cancelToken),
      decode,
    );
  }

  Future<T> delete<T>(
    String path, {
    Object? data,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) {
    return _request(
      () => _dio.delete<dynamic>(path, data: data, cancelToken: cancelToken),
      decode,
    );
  }

  /// Multipart upload (image/file). [formData] should carry the file part(s)
  /// plus any scalar fields. Idempotency keys, when needed, are passed as a
  /// header by the caller.
  Future<T> upload<T>(
    String path, {
    required FormData formData,
    required T Function(Object? data) decode,
    ProgressCallback? onSendProgress,
    Map<String, dynamic>? headers,
    CancelToken? cancelToken,
  }) {
    return _request(
      () => _dio.post<dynamic>(
        path,
        data: formData,
        options: Options(headers: headers),
        onSendProgress: onSendProgress,
        cancelToken: cancelToken,
      ),
      decode,
    );
  }

  /// Shared execution + envelope-unwrap + error-mapping pipeline.
  Future<T> _request<T>(
    Future<Response<dynamic>> Function() send,
    T Function(Object? data) decode,
  ) async {
    try {
      final response = await send();
      final envelope = ApiResponse<T>.parse(response.data, decode);
      if (!envelope.success) {
        throw ApiException(
          message: envelope.message ?? 'Request failed.',
          errorCode: envelope.errorCode,
          debugDetail: envelope.details,
          statusCode: response.statusCode,
        );
      }
      return envelope.dataOrThrow();
    } on DioException catch (e) {
      throw DioErrorMapper.map(e);
    } on AppException {
      rethrow; // already typed (e.g. from ApiResponse.parse / dataOrThrow)
    } catch (e) {
      throw UnknownException(debugDetail: e.toString());
    }
  }
}
