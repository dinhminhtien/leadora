import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/core/network/api_exception.dart';
import 'package:leadora_mobile/shared/widgets/error_state.dart';

void main() {
  group('ErrorPresentation.of — retriability', () {
    test('transport failures are retriable', () {
      expect(ErrorPresentation.of(const NetworkException()).retriable, isTrue);
      expect(ErrorPresentation.of(const TimeoutException()).retriable, isTrue);
      expect(ErrorPresentation.of(const ServerException()).retriable, isTrue);
      expect(ErrorPresentation.of(const UnknownException()).retriable, isTrue);
    });

    test('permission and lookup failures are not retriable', () {
      // Retrying a 403 or a 404 cannot change the outcome; offering the button
      // teaches users it does nothing.
      expect(ErrorPresentation.of(const ForbiddenException()).retriable, isFalse);
      expect(ErrorPresentation.of(const NotFoundException()).retriable, isFalse);
      expect(
        ErrorPresentation.of(const UnauthorizedException()).retriable,
        isFalse,
      );
      expect(
        ErrorPresentation.of(
          const ValidationException(message: 'bad field'),
        ).retriable,
        isFalse,
      );
    });

    test('a rate-limited request is retriable, a conflict is not', () {
      final rateLimited = ErrorPresentation.of(
        const ApiException(message: 'Too many requests', statusCode: 429),
      );
      final conflict = ErrorPresentation.of(
        const ApiException(message: 'Already exists', statusCode: 409),
      );

      expect(rateLimited.retriable, isTrue);
      expect(conflict.retriable, isFalse);
    });
  });

  group('ErrorPresentation.of — identity', () {
    test('offline is visually distinct from a server fault', () {
      final offline = ErrorPresentation.of(const NetworkException());
      final server = ErrorPresentation.of(const ServerException());

      expect(offline.icon, Icons.wifi_off_rounded);
      expect(offline.title, "You're offline");
      expect(server.icon, isNot(offline.icon));
      expect(server.title, isNot(offline.title));
    });

    test('forbidden and not-found do not both read as a network problem', () {
      expect(
        ErrorPresentation.of(const ForbiddenException()).icon,
        Icons.lock_outline_rounded,
      );
      expect(
        ErrorPresentation.of(const NotFoundException()).icon,
        Icons.search_off_rounded,
      );
    });

    test("the exception's user-safe message is surfaced verbatim", () {
      const message = 'Deal 42 could not be found.';
      expect(
        ErrorPresentation.of(const NotFoundException(message: message)).message,
        message,
      );
    });

    test('a non-AppException never leaks its toString to the user', () {
      final view = ErrorPresentation.of(StateError('null check on nil'));

      expect(view.message, 'Something went wrong.');
      expect(view.message, isNot(contains('null check')));
      expect(view.retriable, isTrue);
    });
  });

  group('ErrorStateView', () {
    Future<void> pump(WidgetTester tester, Object error, {VoidCallback? onRetry}) {
      return tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: ErrorStateView(error: error, onRetry: onRetry)),
        ),
      );
    }

    testWidgets('offers retry for a retriable error', (tester) async {
      await pump(tester, const NetworkException(), onRetry: () {});
      expect(find.text('Try again'), findsOneWidget);
      expect(find.text("You're offline"), findsOneWidget);
    });

    testWidgets('hides retry for a non-retriable error', (tester) async {
      await pump(tester, const ForbiddenException(), onRetry: () {});
      expect(find.text('Try again'), findsNothing);
      expect(find.text('No access'), findsOneWidget);
    });

    testWidgets('hides retry when no callback is supplied', (tester) async {
      await pump(tester, const NetworkException());
      expect(find.text('Try again'), findsNothing);
    });
  });
}
