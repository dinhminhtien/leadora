import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:leadora_mobile/core/network/interceptors/refresh_interceptor.dart';
import 'package:leadora_mobile/core/network/token_refresher.dart';

class MockDio extends Mock implements Dio {}

class MockTokenRefresher extends Mock implements TokenRefresher {}

class MockErrorInterceptorHandler extends Mock
    implements ErrorInterceptorHandler {}

void main() {
  setUpAll(() {
    registerFallbackValue(RequestOptions());
    registerFallbackValue(
      DioException(requestOptions: RequestOptions(path: '/test')),
    );
    registerFallbackValue(
      Response<dynamic>(requestOptions: RequestOptions(path: '/test')),
    );
  });

  late MockDio mockDio;
  late MockTokenRefresher mockRefresher;
  late List<bool> sessionExpiredCalls;
  late RefreshInterceptor interceptor;

  Future<void> onSessionExpired() async {
    sessionExpiredCalls.add(true);
  }

  setUp(() {
    mockDio = MockDio();
    mockRefresher = MockTokenRefresher();
    sessionExpiredCalls = [];
    interceptor = RefreshInterceptor(
      dio: mockDio,
      refresher: mockRefresher,
      onSessionExpired: onSessionExpired,
    );
  });

  group('RefreshInterceptor — Basic flow', () {
    test('non-401 errors are passed to next handler directly', () async {
      final handler = MockErrorInterceptorHandler();
      final exception = DioException(
        requestOptions: RequestOptions(path: '/test'),
        response: Response(
          requestOptions: RequestOptions(path: '/test'),
          statusCode: 400,
        ),
      );

      await interceptor.onError(exception, handler);

      verify(() => handler.next(exception)).called(1);
      verifyZeroInteractions(mockRefresher);
    });

    test('401 from login or auth endpoints is passed to next handler directly',
        () async {
      final handler = MockErrorInterceptorHandler();
      final exception = DioException(
        requestOptions: RequestOptions(path: '/auth/login'),
        response: Response(
          requestOptions: RequestOptions(path: '/auth/login'),
          statusCode: 401,
        ),
      );

      await interceptor.onError(exception, handler);

      verify(() => handler.next(exception)).called(1);
      verifyZeroInteractions(mockRefresher);
    });

    test('already retried request triggers session expired and calls next',
        () async {
      final handler = MockErrorInterceptorHandler();
      final exception = DioException(
        requestOptions: RequestOptions(
          path: '/deals/123',
          extra: {'__retried__': true},
        ),
        response: Response(
          requestOptions: RequestOptions(path: '/deals/123'),
          statusCode: 401,
        ),
      );

      await interceptor.onError(exception, handler);

      expect(sessionExpiredCalls.length, 1);
      verify(() => handler.next(exception)).called(1);
    });
  });

  group('RefreshInterceptor — Race condition / Mutex serialization', () {
    test(
        'multiple simultaneous 401s only trigger 1 refresh and retry concurrently with new token',
        () async {
      // Stub the refresher to delay and then return a new token
      var refreshCount = 0;
      when(() => mockRefresher.refresh()).thenAnswer((_) async {
        refreshCount++;
        await Future<void>.delayed(const Duration(milliseconds: 50));
        return 'new-access-token';
      });

      // Stub dio fetch to return a successful response on retry
      when(() => mockDio.fetch<dynamic>(any())).thenAnswer((_) async {
        return Response<dynamic>(
          requestOptions: RequestOptions(path: '/deals/123'),
          data: {'status': 'success'},
        );
      });

      // Fire 3 simultaneous 401 requests (UC-12.5 simulation)
      final requestOptionsList = [
        RequestOptions(path: '/deals/1'),
        RequestOptions(path: '/deals/2'),
        RequestOptions(path: '/deals/3'),
      ];

      final handlers = List.generate(
        3,
        (_) => MockErrorInterceptorHandler(),
      );

      final futures = List.generate(3, (index) {
        final exception = DioException(
          requestOptions: requestOptionsList[index],
          response: Response(
            requestOptions: requestOptionsList[index],
            statusCode: 401,
          ),
        );
        return interceptor.onError(exception, handlers[index]);
      });

      await Future.wait(futures);

      // Verify that refresher.refresh() was called exactly ONCE
      expect(refreshCount, 1);
      verify(() => mockRefresher.refresh()).called(1);

      // Verify that all 3 handlers resolved the retried request successfully
      for (final handler in handlers) {
        verify(() => handler.resolve(any())).called(1);
      }

      // Verify that the requests were updated with the new authorization header and __retried__ flag
      final capturedOptions = verify(
        () => mockDio.fetch<dynamic>(captureAny()),
      ).captured;

      expect(capturedOptions.length, 3);
      for (final option in capturedOptions) {
        final reqOptions = option as RequestOptions;
        expect(reqOptions.headers['Authorization'], 'Bearer new-access-token');
        expect(reqOptions.extra['__retried__'], isTrue);
      }

      // Verify that session was not expired
      expect(sessionExpiredCalls.isEmpty, isTrue);
    });

    test(
        'if refresh fails (returns null), all queued requests trigger onSessionExpired and call next',
        () async {
      when(() => mockRefresher.refresh()).thenAnswer((_) async {
        await Future<void>.delayed(const Duration(milliseconds: 50));
        return null;
      });

      final requestOptionsList = [
        RequestOptions(path: '/deals/1'),
        RequestOptions(path: '/deals/2'),
      ];

      final handlers = List.generate(
        2,
        (_) => MockErrorInterceptorHandler(),
      );

      final futures = List.generate(2, (index) {
        final exception = DioException(
          requestOptions: requestOptionsList[index],
          response: Response(
            requestOptions: requestOptionsList[index],
            statusCode: 401,
          ),
        );
        return interceptor.onError(exception, handlers[index]);
      });

      await Future.wait(futures);

      // Verify refresher.refresh() called exactly once
      verify(() => mockRefresher.refresh()).called(1);

      // Verify session expired triggered for both callers
      expect(sessionExpiredCalls.length, 2);

      // Verify both handlers called next with original exception
      for (final handler in handlers) {
        verify(() => handler.next(any())).called(1);
      }
    });
  });
}
