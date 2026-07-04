import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/localization/generated/app_localizations.dart';
import '../../../../core/routing/app_session.dart';

/// Phase 1 stub. Replaced by the full form + biometric flow in Phase 3.
/// For now it flips the session so the route guard and shell can be exercised.
class LoginScreen extends ConsumerWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(l10n.loginTitle,
                  style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 8),
              Text(l10n.loginSubtitle),
              const SizedBox(height: 32),
              FilledButton(
                onPressed: () => ref
                    .read(appSessionProvider)
                    .update(AuthStatus.authenticated),
                child: Text(l10n.loginSubmit),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
