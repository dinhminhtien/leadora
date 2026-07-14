// Foundation smoke test.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:leadora_mobile/core/routing/app_session.dart';
import 'package:leadora_mobile/core/widgets/splash_screen.dart';

void main() {
  test('AppSession starts unknown and transitions on update', () {
    final container = ProviderContainer();
    addTearDown(container.dispose);

    final session = container.read(appSessionProvider);
    expect(session.status, AuthStatus.unknown);
    expect(session.isResolved, isFalse);

    session.update(AuthStatus.authenticated);
    expect(session.isAuthenticated, isTrue);
    expect(session.isResolved, isTrue);
  });

  testWidgets('SplashScreen renders the brand mark and a progress indicator', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: SplashScreen()));
    expect(find.byType(SplashScreen), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });
}
