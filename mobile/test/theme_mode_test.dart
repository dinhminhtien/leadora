import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/core/theme/theme_mode_controller.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('ThemeModeController', () {
    test('defaults to system when nothing is persisted', () async {
      SharedPreferences.setMockInitialValues({});
      final container = ProviderContainer();
      addTearDown(container.dispose);

      expect(container.read(themeModeProvider), ThemeMode.system);
      // Let the async restore settle — it must not flip the default.
      await Future<void>.delayed(Duration.zero);
      expect(container.read(themeModeProvider), ThemeMode.system);
    });

    test('restores a persisted choice on startup', () async {
      SharedPreferences.setMockInitialValues(
          {'prefs.theme_mode': 'dark'});
      final container = ProviderContainer();
      addTearDown(container.dispose);

      container.read(themeModeProvider); // instantiate → kicks off restore
      await Future<void>.delayed(Duration.zero);
      expect(container.read(themeModeProvider), ThemeMode.dark);
    });

    test('setMode persists light/dark and clears on system', () async {
      SharedPreferences.setMockInitialValues({});
      final container = ProviderContainer();
      addTearDown(container.dispose);

      await container.read(themeModeProvider.notifier).setMode(ThemeMode.light);
      expect(container.read(themeModeProvider), ThemeMode.light);
      var prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('prefs.theme_mode'), 'light');

      await container
          .read(themeModeProvider.notifier)
          .setMode(ThemeMode.system);
      prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('prefs.theme_mode'), isNull);
    });
  });
}
