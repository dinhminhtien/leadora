import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import 'empty_state.dart';

/// Renders an [AsyncValue] with consistent loading / error / data states so no
/// screen re-implements the spinner-error-retry triad.
///
/// * loading → [loading] (defaults to a centered progress indicator, or a
///   skeleton when provided);
/// * error   → a friendly message (mapped from [AppException]) plus a Retry
///   button that calls [onRetry];
/// * data    → [data]. If [isEmpty] returns true, [empty] is shown instead.
class AsyncValueView<T> extends StatelessWidget {
  const AsyncValueView({
    super.key,
    required this.value,
    required this.data,
    this.onRetry,
    this.loading,
    this.empty,
    this.isEmpty,
  });

  final AsyncValue<T> value;
  final Widget Function(T data) data;
  final VoidCallback? onRetry;
  final Widget? loading;
  final Widget? empty;
  final bool Function(T data)? isEmpty;

  @override
  Widget build(BuildContext context) {
    return value.when(
      skipLoadingOnRefresh: false,
      skipLoadingOnReload: true,
      loading: () => loading ?? const _CenteredSpinner(),
      error: (error, _) => _ErrorState(error: error, onRetry: onRetry),
      data: (value) {
        if (isEmpty?.call(value) ?? false) {
          return empty ?? const EmptyState(message: 'Nothing here yet.');
        }
        return data(value);
      },
    );
  }
}

class _CenteredSpinner extends StatelessWidget {
  const _CenteredSpinner();

  @override
  Widget build(BuildContext context) =>
      const Center(child: CircularProgressIndicator());
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.error, this.onRetry});

  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final message =
        error is AppException ? (error as AppException).message : 'Something went wrong.';
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.cloud_off_rounded,
                size: 56, color: theme.colorScheme.outline),
            const SizedBox(height: 16),
            Text(
              message,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyLarge
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
            if (onRetry != null) ...[
              const SizedBox(height: 20),
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
