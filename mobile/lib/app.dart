import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/config/env.dart';
import 'core/localization/generated/app_localizations.dart';
import 'core/routing/app_router.dart';
import 'core/routing/app_session.dart';
import 'core/theme/app_theme.dart';

/// Root application widget.
///
/// [ConsumerStatefulWidget] so we can kick off the boot-time session probe once.
/// In Phase 1 the probe simply resolves to `unauthenticated`; Phase 3 replaces
/// [_resolveSession] with a real secure-storage token check.
class LeadoraApp extends ConsumerStatefulWidget {
  const LeadoraApp({super.key});

  @override
  ConsumerState<LeadoraApp> createState() => _LeadoraAppState();
}

class _LeadoraAppState extends ConsumerState<LeadoraApp> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _resolveSession());
  }

  Future<void> _resolveSession() async {
    // Placeholder boot probe. Phase 3: read token from secure storage,
    // validate expiry, and preload current user before flipping the session.
    ref.read(appSessionProvider).update(AuthStatus.unauthenticated);
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(routerProvider);
    return MaterialApp.router(
      title: Env.appName,
      debugShowCheckedModeBanner: Env.isDev,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ThemeMode.system,
      routerConfig: router,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
    );
  }
}
