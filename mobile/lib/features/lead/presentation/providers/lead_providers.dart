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

  @override
  Future<LeadListState> build() => _fetch(const LeadListState());

  Future<LeadListState> _fetch(LeadListState base) async {
    final page = await _repo.getLeads(
      filters: base.filters,
      page: 0,
      size: _pageSize,
    );
    return base.copyWith(
      items: page.items,
      nextPage: 1,
      hasMore: page.hasMore,
      isLoadingMore: false,
    );
  }

  /// Pull-to-refresh: reload page 0 with current filters.
  /// On failure the previous value (items + filters) is kept alongside the
  /// error, so a retry re-runs the same filters instead of reverting to
  /// defaults.
  Future<void> refresh() async {
    final current = state.valueOrNull ?? const LeadListState();
    state = const AsyncLoading<LeadListState>().copyWithPrevious(state);
    state = (await AsyncValue.guard(
      () => _fetch(current),
    )).copyWithPrevious(state);
  }

  /// Replace the filter set and reload from the top.
  Future<void> applyFilters(LeadFilters filters) async {
    final current = state.valueOrNull ?? const LeadListState();
    final next = current.copyWith(filters: filters);
    // Seed the loading state with the *requested* filters so the chips/badge
    // reflect the selection immediately, and so a failed fetch + retry re-runs
    // these filters rather than silently restoring the old ones.
    state = const AsyncLoading<LeadListState>().copyWithPrevious(
      AsyncData(next),
      isRefresh: true,
    );
    state = (await AsyncValue.guard(
      () => _fetch(next),
    )).copyWithPrevious(state);
  }

  /// Current filters, for screens composing an updated set.
  LeadFilters get filters => state.valueOrNull?.filters ?? const LeadFilters();

  /// Infinite scroll: append the next page.
  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final page = await _repo.getLeads(
        filters: current.filters,
        page: current.nextPage,
        size: _pageSize,
      );
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
