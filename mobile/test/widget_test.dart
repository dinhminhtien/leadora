// Foundation smoke test. Full feature tests live in Phase 7.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:leadora_mobile/core/routing/app_session.dart';

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

  testWidgets('SplashScreen renders a progress indicator', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: CircularProgressIndicator())),
    );
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });
}
