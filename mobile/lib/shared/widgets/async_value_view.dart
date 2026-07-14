import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'empty_state.dart';
import 'error_state.dart';

/// Renders an [AsyncValue] with consistent loading / error / data states so no
/// screen re-implements the spinner-error-retry triad.
///
/// * loading → [loading] (defaults to a centered progress indicator; pass a
///   [ListSkeleton] on any list or detail — a bare spinner reads as dead);
/// * error   → [ErrorStateView] plus a Retry button that calls [onRetry];
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
      error: (error, _) => ErrorStateView(error: error, onRetry: onRetry),
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
