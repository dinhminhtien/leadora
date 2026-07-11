import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../constants/storage_keys.dart';

/// Persisted theme selection — the mobile mirror of the web's theme toggle
/// (`ui_store.ts`): an explicit user choice wins and is saved to storage; with
/// no saved choice the app follows the system theme.
///
/// State is synchronous ([ThemeMode.system] until the pref loads, which is the
/// correct default anyway) so `MaterialApp.themeMode` can watch it directly
/// without an async gate at startup.
class ThemeModeController extends Notifier<ThemeMode> {
  @override
  ThemeMode build() {
    _restore();
    return ThemeMode.system;
  }

  Future<void> _restore() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(StorageKeys.themeMode);
    final restored = _fromName(saved);
    if (restored != state) state = restored;
  }

  /// Set + persist. `ThemeMode.system` clears the override, matching the web
  /// where removing the saved theme falls back to `prefers-color-scheme`.
  Future<void> setMode(ThemeMode mode) async {
    state = mode;
    final prefs = await SharedPreferences.getInstance();
    if (mode == ThemeMode.system) {
      await prefs.remove(StorageKeys.themeMode);
    } else {
      await prefs.setString(StorageKeys.themeMode, mode.name);
    }
  }

  static ThemeMode _fromName(String? name) => switch (name) {
    'light' => ThemeMode.light,
    'dark' => ThemeMode.dark,
    _ => ThemeMode.system,
  };
}

final themeModeProvider = NotifierProvider<ThemeModeController, ThemeMode>(
  ThemeModeController.new,
);
