import 'package:flutter/material.dart';

import '../../core/network/api_exception.dart';
import '../../core/theme/app_dimens.dart';

/// How a failure should be presented: what it looks like, what it says, and
/// whether offering "Try again" is honest.
///
/// Kept as a plain value class (not a widget) so the classification is unit
/// tested directly — the mapping is the part that goes wrong, not the layout.
@immutable
class ErrorPresentation {
  const ErrorPresentation({
    required this.icon,
    required this.title,
    required this.message,
    required this.retriable,
  });

  final IconData icon;
  final String title;
  final String message;

  /// Whether retrying the same request could plausibly succeed. A 403 or a 404
  /// will not fix itself, and offering a retry button there just teaches users
  /// that the button does nothing.
  final bool retriable;

  static const _genericMessage = 'Something went wrong.';

  factory ErrorPresentation.of(Object error) {
    // Anything that is not an AppException escaped DioErrorMapper — a bug, but
    // never leak its toString() to the user.
    if (error is! AppException) {
      return const ErrorPresentation(
        icon: Icons.error_outline_rounded,
        title: 'Something went wrong',
        message: _genericMessage,
        retriable: true,
      );
    }

    return switch (error) {
      NetworkException() => ErrorPresentation(
        icon: Icons.wifi_off_rounded,
        title: "You're offline",
        message: error.message,
        retriable: true,
      ),
      TimeoutException() => ErrorPresentation(
        icon: Icons.schedule_rounded,
        title: 'Taking too long',
        message: error.message,
        retriable: true,
      ),
      ServerException() => ErrorPresentation(
        icon: Icons.cloud_off_rounded,
        title: 'Server problem',
        message: error.message,
        retriable: true,
      ),
      ForbiddenException() => ErrorPresentation(
        icon: Icons.lock_outline_rounded,
        title: 'No access',
        message: error.message,
        retriable: false,
      ),
      NotFoundException() => ErrorPresentation(
        icon: Icons.search_off_rounded,
        title: 'Not found',
        message: error.message,
        retriable: false,
      ),
      UnauthorizedException() => ErrorPresentation(
        icon: Icons.lock_person_rounded,
        title: 'Session expired',
        message: error.message,
        retriable: false,
      ),
      ValidationException() => ErrorPresentation(
        icon: Icons.rule_rounded,
        title: 'Check the details',
        message: error.message,
        retriable: false,
      ),
      SerializationException() => ErrorPresentation(
        icon: Icons.data_object_rounded,
        title: 'Unexpected response',
        message: error.message,
        retriable: false,
      ),
      // 4xx that is not one of the above (409 conflict, 429 rate-limit, …).
      // Only a rate-limit is worth retrying; a conflict needs the user to act.
      ApiException() => ErrorPresentation(
        icon: Icons.error_outline_rounded,
        title: 'Request failed',
        message: error.message,
        retriable: error.statusCode == 429 || error.statusCode == 408,
      ),
      RequestCancelledException() => ErrorPresentation(
        icon: Icons.error_outline_rounded,
        title: 'Cancelled',
        message: error.message,
        retriable: true,
      ),
      UnknownException() => ErrorPresentation(
        icon: Icons.error_outline_rounded,
        title: 'Something went wrong',
        message: error.message,
        retriable: true,
      ),
    };
  }
}

/// The error + retry surface shown when a load fails.
///
/// Extracted from `AsyncValueView` so screens that manage their own failure
/// state (forms, mutations) show the same thing rather than a bare SnackBar.
/// The retry button appears only when [ErrorPresentation.retriable].
class ErrorStateView extends StatelessWidget {
  const ErrorStateView({super.key, required this.error, this.onRetry});

  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final view = ErrorPresentation.of(error);
    final showRetry = onRetry != null && view.retriable;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xxxl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(view.icon, size: AppIconSize.hero, color: scheme.outline),
            const SizedBox(height: AppSpacing.lg),
            Text(
              view.title,
              textAlign: TextAlign.center,
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              view.message,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
            if (showRetry) ...[
              const SizedBox(height: AppSpacing.xl),
              FilledButton.tonalIcon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh_rounded),
                label: const Text('Try again'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
