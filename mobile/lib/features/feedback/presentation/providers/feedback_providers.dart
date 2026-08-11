import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../auth/data/dto/auth_user.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/feedback_models.dart';
import '../../data/feedback_repository.dart';

/// What the signed-in user may do in the Feedback module.
///
/// Mirrors the controller's `@PreAuthorize`: SALES/MANAGER/ADMIN can read,
/// only MANAGER/ADMIN can move the review status. Gating the UI here grants
/// nothing — the server re-checks — it just keeps us from offering a button
/// that would come back 403.
class FeedbackPermissions {
  const FeedbackPermissions({required this.canModerate});

  static const FeedbackPermissions none = FeedbackPermissions(
    canModerate: false,
  );

  final bool canModerate;

  factory FeedbackPermissions.of(AuthUser? user) {
    if (user == null) return none;
    return FeedbackPermissions(canModerate: user.hasFullAccess);
  }
}

final feedbackPermissionsProvider = Provider<FeedbackPermissions>((ref) {
  return FeedbackPermissions.of(ref.watch(currentUserProvider));
});

/// Accumulated list state: the pages loaded so far plus the filters that
/// produced them, so the search field and chips can read back what is active.
class FeedbackListState {
  const FeedbackListState({
    this.items = const [],
    this.filters = const FeedbackFilters(),
    this.page = 0,
    this.hasMore = false,
    this.total = 0,
    this.isLoadingMore = false,
  });

  final List<GuestFeedback> items;
  final FeedbackFilters filters;
  final int page;
  final bool hasMore;
  final int total;
  final bool isLoadingMore;

  FeedbackListState copyWith({
    List<GuestFeedback>? items,
    FeedbackFilters? filters,
    int? page,
    bool? hasMore,
    int? total,
    bool? isLoadingMore,
  }) {
    return FeedbackListState(
      items: items ?? this.items,
      filters: filters ?? this.filters,
      page: page ?? this.page,
      hasMore: hasMore ?? this.hasMore,
      total: total ?? this.total,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
    );
  }
}

class FeedbackListController
    extends AutoDisposeAsyncNotifier<FeedbackListState> {
  static const int _pageSize = 20;

  FeedbackRepository get _repo => ref.read(feedbackRepositoryProvider);

  Future<FeedbackListState> _fetch(FeedbackFilters filters) async {
    final result = await _repo.getFeedbacks(filters: filters, size: _pageSize);
    return FeedbackListState(
      items: result.items,
      filters: filters,
      page: 0,
      hasMore: !result.isLast,
      total: result.totalElements,
    );
  }

  @override
  Future<FeedbackListState> build() => _fetch(const FeedbackFilters());

  Future<void> refresh() async {
    final filters = state.valueOrNull?.filters ?? const FeedbackFilters();
    state = const AsyncLoading<FeedbackListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(() => _fetch(filters));
  }

  /// Replace the filter set and reload. The requested filters seed the loading
  /// state so the chips update immediately and a retry re-runs the user's
  /// selection rather than reverting to defaults.
  Future<void> applyFilters(FeedbackFilters filters) async {
    final seeded = (state.valueOrNull ?? const FeedbackListState()).copyWith(
      filters: filters,
    );
    state = const AsyncLoading<FeedbackListState>().copyWithPrevious(
      AsyncData(seeded),
      isRefresh: true,
    );
    state = await AsyncValue.guard(() => _fetch(filters));
  }

  FeedbackFilters get filters =>
      state.valueOrNull?.filters ?? const FeedbackFilters();

  /// Append the next page. Guarded so a fast scroll cannot fire two requests
  /// for the same page and duplicate rows.
  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;

    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final next = current.page + 1;
      final result = await _repo.getFeedbacks(
        filters: current.filters,
        page: next,
        size: _pageSize,
      );
      state = AsyncData(
        current.copyWith(
          items: [...current.items, ...result.items],
          page: next,
          hasMore: !result.isLast,
          total: result.totalElements,
          isLoadingMore: false,
        ),
      );
    } catch (_) {
      // Keep what is already on screen; the row simply stops growing.
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }

  /// Moderation (MANAGER/ADMIN). Optimistic, reverting on failure — the
  /// endpoint returns no body, so there is nothing to reconcile against.
  Future<void> setReviewStatus(
    GuestFeedback feedback,
    FeedbackReviewStatus next,
  ) async {
    final current = state.valueOrNull;
    if (current == null) return;
    final me = ref.read(currentUserProvider);

    state = AsyncData(
      current.copyWith(
        items: [
          for (final item in current.items)
            if (item.feedbackId == feedback.feedbackId)
              item.withReviewStatus(next, reviewerName: me?.name)
            else
              item,
        ],
      ),
    );

    try {
      await _repo.updateReviewStatus(feedback.feedbackId, next);
    } catch (_) {
      state = AsyncData(current);
      rethrow;
    }
  }
}

final feedbackListControllerProvider =
    AutoDisposeAsyncNotifierProvider<FeedbackListController, FeedbackListState>(
      FeedbackListController.new,
    );

/// One feedback, fetched fresh — used by the detail route when arriving from a
/// deep link, where no list row is available to pass through.
final feedbackDetailProvider = AutoDisposeFutureProvider.family<GuestFeedback, String>(
  (ref, id) => ref.watch(feedbackRepositoryProvider).getById(id),
);
