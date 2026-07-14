import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/config/env.dart';
import 'core/localization/generated/app_localizations.dart';
import 'core/routing/app_router.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/theme_mode_controller.dart';
import 'features/auth/presentation/providers/auth_controller.dart';

/// Root application widget.
///
/// A plain [ConsumerWidget]: the boot-time session probe is owned by
/// [authControllerProvider]'s `build` (reads the persisted token, rehydrates
/// the user via `/auth/profile`, and flips `AppSession`). Watching it here
/// simply instantiates the controller so restore runs on first frame; the
/// splash route stays up until the session resolves.
class LeadoraApp extends ConsumerWidget {
  const LeadoraApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Instantiate the auth controller → triggers session restore at startup.
    ref.watch(authControllerProvider);
    final router = ref.watch(routerProvider);
    final themeMode = ref.watch(themeModeProvider);
    return MaterialApp.router(
      title: Env.appName,
      debugShowCheckedModeBanner: Env.isDev,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: themeMode,
      routerConfig: router,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
    );
  }
}
