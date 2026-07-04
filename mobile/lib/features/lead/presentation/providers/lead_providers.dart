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
    this.search,
    this.status,
  });

  final List<Lead> items;
  final bool isLoadingMore;
  final bool hasMore;
  final int nextPage;
  final String? search;
  final LeadStatus? status;

  LeadListState copyWith({
    List<Lead>? items,
    bool? isLoadingMore,
    bool? hasMore,
    int? nextPage,
    Object? search = _sentinel,
    Object? status = _sentinel,
  }) {
    return LeadListState(
      items: items ?? this.items,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      nextPage: nextPage ?? this.nextPage,
      search: search == _sentinel ? this.search : search as String?,
      status: status == _sentinel ? this.status : status as LeadStatus?,
    );
  }

  static const _sentinel = Object();
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
      search: base.search,
      status: base.status?.wire,
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
  Future<void> refresh() async {
    final current = state.valueOrNull ?? const LeadListState();
    state = const AsyncLoading<LeadListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(() => _fetch(current));
  }

  /// Apply a new search/status filter and reload from the top.
  Future<void> applyFilters({String? search, LeadStatus? status, bool clearStatus = false}) async {
    final current = state.valueOrNull ?? const LeadListState();
    final next = current.copyWith(
      search: search,
      status: clearStatus ? null : (status ?? current.status),
    );
    state = const AsyncLoading<LeadListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(() => _fetch(next));
  }

  /// Infinite scroll: append the next page.
  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final page = await _repo.getLeads(
        search: current.search,
        status: current.status?.wire,
        page: current.nextPage,
        size: _pageSize,
      );
      state = AsyncData(current.copyWith(
        items: [...current.items, ...page.items],
        nextPage: current.nextPage + 1,
        hasMore: page.hasMore,
        isLoadingMore: false,
      ));
    } catch (_) {
      // Keep the accumulated list; just stop the spinner. A retry re-triggers.
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }
}

final leadListControllerProvider =
    AutoDisposeAsyncNotifierProvider<LeadListController, LeadListState>(
        LeadListController.new);

/// Single-lead detail, keyed by id.
final leadDetailProvider =
    AutoDisposeFutureProvider.family<Lead, String>((ref, leadId) {
  return ref.watch(leadRepositoryProvider).getLead(leadId);
});
