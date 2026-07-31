import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/lead_models.dart';
import '../../data/lead_repository.dart';

/// Accumulated, filterable state for the assigned-lead list (infinite scroll).
class LeadListState {
  const LeadListState({
    this.items = const [],
    this.isLoadingMore = false,
    this.hasMore = true,
    this.nextPage = 0,
    this.filters = const LeadFilters(),
  });

  final List<Lead> items;
  final bool isLoadingMore;
  final bool hasMore;
  final int nextPage;
  final LeadFilters filters;

  LeadListState copyWith({
    List<Lead>? items,
    bool? isLoadingMore,
    bool? hasMore,
    int? nextPage,
    LeadFilters? filters,
  }) {
    return LeadListState(
      items: items ?? this.items,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      nextPage: nextPage ?? this.nextPage,
      filters: filters ?? this.filters,
    );
  }
}

/// Loads and paginates the assigned-lead list, reacting to search/status
/// filters. Kept alive while the list screen is mounted; disposed on leave.
class LeadListController extends AutoDisposeAsyncNotifier<LeadListState> {
  static const _pageSize = 15;

  LeadRepository get _repo => ref.read(leadRepositoryProvider);

  /// Which reload the controller is currently showing.
  ///
  /// Search is debounced at 400ms but a round trip on mobile data is routinely
  /// slower than that, so two page-0 requests are regularly in flight at once.
  /// Without a generation stamp the one that *finishes* last wins instead of the
  /// one the user asked for last — type "ha" then "hanoi" and you can end up
  /// looking at the results for "ha" under a search box that says "hanoi".
  /// Every reload takes the next number; a response stamped with an older one is
  /// dropped rather than written to [state].
  int _generation = 0;

  /// In-flight requests, so a superseded fetch is aborted at the socket instead
  /// of being paid for and then thrown away. [_appendToken] is separate because
  /// a "load more" and a reload are cancelled on different events.
  CancelToken? _reloadToken;
  CancelToken? _appendToken;

  /// Riverpod throws if [state] is written after the notifier is disposed, which
  /// is exactly what a fetch still running when the user leaves the tab would
  /// do. Cancelling makes that rare, not impossible — a response already decoded
  /// still lands.
  bool _disposed = false;

  @override
  Future<LeadListState> build() {
    ref.onDispose(() {
      _disposed = true;
      _reloadToken?.cancel('lead list disposed');
      _appendToken?.cancel('lead list disposed');
    });
    _startReload();
    return _fetch(const LeadListState());
  }

  /// Opens a new generation: bumps the stamp and aborts whatever was running,
  /// since nothing in flight can still be relevant to the newest filter set.
  int _startReload() {
    _reloadToken?.cancel('superseded by a newer lead query');
    _appendToken?.cancel('superseded by a newer lead query');
    _reloadToken = CancelToken();
    _appendToken = null;
    return ++_generation;
  }

  Future<LeadListState> _fetch(LeadListState base) async {
    final page = await _repo.getLeads(
      filters: base.filters,
      page: 0,
      size: _pageSize,
      cancelToken: _reloadToken,
    );
    return base.copyWith(
      items: page.items,
      nextPage: 1,
      hasMore: page.hasMore,
      isLoadingMore: false,
    );
  }

  /// Reload page 0 for [seed]'s filters.
  ///
  /// [seedIsVisible] makes the requested filters the value shown *while*
  /// loading, so the chips/badge reflect the selection immediately and a failed
  /// fetch + retry re-runs them rather than silently restoring the old ones.
  Future<void> _reload(LeadListState seed, {required bool seedIsVisible}) async {
    final generation = _startReload();
    state = const AsyncLoading<LeadListState>().copyWithPrevious(
      seedIsVisible ? AsyncData(seed) : state,
      isRefresh: true,
    );
    final result = await AsyncValue.guard(() => _fetch(seed));
    // A newer reload already owns the screen — including the cancellation this
    // one is about to report as an error. Dropping it here is what makes the
    // newest request the one that wins.
    if (_disposed || generation != _generation) return;
    state = result.copyWithPrevious(state);
  }

  /// Pull-to-refresh: reload page 0 with current filters.
  /// On failure the previous value (items + filters) is kept alongside the
  /// error, so a retry re-runs the same filters instead of reverting to
  /// defaults.
  Future<void> refresh() =>
      _reload(state.valueOrNull ?? const LeadListState(), seedIsVisible: false);

  /// Replace the filter set and reload from the top.
  ///
  /// Re-selecting what is already selected is not a change: the status chips
  /// and the filter sheet both hand back a full filter set on every tap, so
  /// without this a second tap on the active chip refetched the identical list
  /// and flashed the skeleton on the way. An error state is always retried —
  /// there, tapping the same chip is exactly how a user asks again.
  Future<void> applyFilters(LeadFilters filters) async {
    final current = state.valueOrNull ?? const LeadListState();
    if (current.filters == filters && !state.hasError) return;
    return _reload(current.copyWith(filters: filters), seedIsVisible: true);
  }

  /// Current filters, for screens composing an updated set.
  LeadFilters get filters => state.valueOrNull?.filters ?? const LeadFilters();

  /// Infinite scroll: append the next page.
  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;
    // A reload in flight is about to replace this list wholesale; appending to
    // the list it is replacing only produces duplicates.
    if (state.isLoading) return;

    final generation = _generation;
    final token = _appendToken = CancelToken();
    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final page = await _repo.getLeads(
        filters: current.filters,
        page: current.nextPage,
        size: _pageSize,
        cancelToken: token,
      );
      // The page was fetched under filters that are no longer on screen — a
      // refresh or a filter change landed while it was in flight. Appending it
      // would splice results from the old query into the new list and rewind
      // `nextPage` to boot.
      if (_disposed || generation != _generation) return;
      state = AsyncData(
        current.copyWith(
          items: [...current.items, ...page.items],
          nextPage: current.nextPage + 1,
          hasMore: page.hasMore,
          isLoadingMore: false,
        ),
      );
    } catch (_) {
      // Keep the accumulated list; just stop the spinner. A retry re-triggers.
      if (_disposed || generation != _generation) return;
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }
}

final leadListControllerProvider =
    AutoDisposeAsyncNotifierProvider<LeadListController, LeadListState>(
      LeadListController.new,
    );

/// Single-lead detail, keyed by id.
final leadDetailProvider = AutoDisposeFutureProvider.family<Lead, String>((
  ref,
  leadId,
) {
  return ref.watch(leadRepositoryProvider).getLead(leadId);
});
