import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/sla_models.dart';
import '../../data/sla_repository.dart';

/// SLA monitoring list, keyed by the selected `displayStatus` filter chip
/// (`null` = All). Server-filtered — see [SlaRepository.getMonitoring].
final slaMonitoringProvider =
    AutoDisposeFutureProvider.family<List<SlaTrackingEntry>, String?>((
      ref,
      displayStatus,
    ) {
      return ref
          .watch(slaRepositoryProvider)
          .getMonitoring(displayStatus: displayStatus);
    });

/// UC-17.4 — Resolve SLA Task on Mobile. Refetch-based rather than an
/// optimistic local patch: resolving also sets `resolvedAt` server-side
/// (`ResolveSlaBreachUseCase`), which changes `hoursRemaining`'s meaning for
/// that row, so re-fetching is simpler and more correct than recomputing it
/// on the client. [state] tracks which tracking ids currently have a resolve
/// in flight, so the card can show a per-row spinner and disable its button.
class SlaResolutionController extends AutoDisposeNotifier<Set<String>> {
  @override
  Set<String> build() => const {};

  bool isResolving(String trackingId) => state.contains(trackingId);

  /// Throws the same [AppException] the repository call would on failure
  /// (403 not-your-record, 409 already-resolved, etc.) — the caller surfaces
  /// it, e.g. as a SnackBar.
  Future<void> resolve(String trackingId) async {
    state = {...state, trackingId};
    try {
      await ref.read(slaRepositoryProvider).resolve(trackingId);
      // Invalidates every `slaMonitoringProvider(displayStatus)` instance
      // (All/Within/Warning/Breached), not just whichever tab is currently
      // open, so the resolved row disappears/updates everywhere it's shown.
      ref.invalidate(slaMonitoringProvider);
    } finally {
      state = {...state}..remove(trackingId);
    }
  }
}

final slaResolutionControllerProvider =
    AutoDisposeNotifierProvider<SlaResolutionController, Set<String>>(
      SlaResolutionController.new,
    );
