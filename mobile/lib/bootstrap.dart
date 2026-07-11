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
/// The [FlutterError.onError] / [PlatformDispatcher.onError] hooks are the
/// seam where a crash reporter (Crashlytics/Sentry) plugs in when adopted.
Future<void> bootstrap() async {
  await runZonedGuarded(
    () async {
      WidgetsFlutterBinding.ensureInitialized();

      await Supabase.initialize(
        url: Env.supabaseUrl,
        anonKey: Env.supabaseAnonKey, // ignore: deprecated_member_use
      );

      // Global Flutter framework errors.
      FlutterError.onError = FlutterError.presentError;

      // Uncaught async errors from the platform dispatcher.
      PlatformDispatcher.instance.onError = (error, stack) {
        debugPrint('Uncaught platform error: $error');
        return true;
      };

      runApp(const ProviderScope(child: LeadoraApp()));
    },
    (error, stack) {
      debugPrint('Uncaught zone error: $error');
    },
  );
}
