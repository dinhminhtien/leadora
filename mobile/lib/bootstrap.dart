import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'app.dart';
import 'core/config/env.dart';

/// Composition root. All app entrypoints funnel through here so error handling,
/// DI (ProviderScope), and platform init live in exactly one place.
///
/// Phase 2 extends this with Firebase.initializeApp + Crashlytics wiring; the
/// [FlutterError.onError] / [PlatformDispatcher.onError] hooks are already in
/// place so those integrations are a one-line drop-in later.
Future<void> bootstrap() async {
  await runZonedGuarded(() async {
    WidgetsFlutterBinding.ensureInitialized();

    await Supabase.initialize(
      url: Env.supabaseUrl,
      anonKey: Env.supabaseAnonKey, // ignore: deprecated_member_use
    );

    // Global Flutter framework errors.
    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      // Phase 2: FirebaseCrashlytics.instance.recordFlutterFatalError(details);
    };

    // Uncaught async errors from the platform dispatcher.
    PlatformDispatcher.instance.onError = (error, stack) {
      debugPrint('Uncaught platform error: $error');
      // Phase 2: FirebaseCrashlytics.instance.recordError(error, stack, fatal: true);
      return true;
    };

    runApp(const ProviderScope(child: LeadoraApp()));
  }, (error, stack) {
    debugPrint('Uncaught zone error: $error');
    // Phase 2: FirebaseCrashlytics.instance.recordError(error, stack, fatal: true);
  });
}
