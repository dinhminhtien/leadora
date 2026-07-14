import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/payment_models.dart';
import '../../data/payment_repository.dart';

/// Accumulated, filterable state for the payment list (infinite scroll).
class PaymentListState {
  const PaymentListState({
    this.items = const [],
    this.isLoadingMore = false,
    this.hasMore = true,
    this.nextPage = 0,
    this.filters = const PaymentFilters(),
  });

  final List<Payment> items;
  final bool isLoadingMore;
  final bool hasMore;
  final int nextPage;
  final PaymentFilters filters;

  PaymentListState copyWith({
    List<Payment>? items,
    bool? isLoadingMore,
    bool? hasMore,
    int? nextPage,
    PaymentFilters? filters,
  }) {
    return PaymentListState(
      items: items ?? this.items,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      nextPage: nextPage ?? this.nextPage,
      filters: filters ?? this.filters,
    );
  }
}

/// Loads and paginates the payment list. Search, status/type filters and sort
/// all run server-side (`GET /payments` accepts them), so every filter change
/// reloads from page 0.
class PaymentListController extends AutoDisposeAsyncNotifier<PaymentListState> {
  static const _pageSize = 15;

  PaymentRepository get _repo => ref.read(paymentRepositoryProvider);

  @override
  Future<PaymentListState> build() => _fetch(const PaymentListState());

  Future<PaymentListState> _fetch(PaymentListState base) async {
    final page = await _repo.getPayments(
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

  Future<void> refresh() async {
    final current = state.valueOrNull ?? const PaymentListState();
    state = const AsyncLoading<PaymentListState>().copyWithPrevious(state);
    state = (await AsyncValue.guard(
      () => _fetch(current),
    )).copyWithPrevious(state);
  }

  /// Replace the filter set and reload from the top. The requested filters seed
  /// the loading state so the chips update immediately and a retry re-runs the
  /// selection rather than reverting to defaults.
  Future<void> applyFilters(PaymentFilters filters) async {
    final current = state.valueOrNull ?? const PaymentListState();
    final next = current.copyWith(filters: filters);
    state = const AsyncLoading<PaymentListState>().copyWithPrevious(
      AsyncData(next),
      isRefresh: true,
    );
    state = (await AsyncValue.guard(
      () => _fetch(next),
    )).copyWithPrevious(state);
  }

  PaymentFilters get filters =>
      state.valueOrNull?.filters ?? const PaymentFilters();

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final page = await _repo.getPayments(
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

final paymentListControllerProvider =
    AutoDisposeAsyncNotifierProvider<PaymentListController, PaymentListState>(
      PaymentListController.new,
    );

/// Single-payment detail, keyed by id.
final paymentDetailProvider = AutoDisposeFutureProvider.family<Payment, String>(
  (ref, paymentId) => ref.watch(paymentRepositoryProvider).getPayment(paymentId),
);

/// Mutations. One place for screens to await + invalidate.
class PaymentActions {
  PaymentActions(this._ref);

  final Ref _ref;

  PaymentRepository get _repo => _ref.read(paymentRepositoryProvider);

  void _invalidate(String paymentId) {
    _ref.invalidate(paymentDetailProvider(paymentId));
    if (_ref.exists(paymentListControllerProvider)) {
      _ref.invalidate(paymentListControllerProvider);
    }
  }

  Future<Payment> generate(GeneratePaymentPayload payload) async {
    final payment = await _repo.generate(payload);
    if (_ref.exists(paymentListControllerProvider)) {
      _ref.invalidate(paymentListControllerProvider);
    }
    return payment;
  }

  Future<Payment> updateStatus(
    String paymentId,
    PaymentStatus status, {
    String? verificationNote,
  }) async {
    final payment = await _repo.updateStatus(
      paymentId,
      status,
      verificationNote: verificationNote,
    );
    _invalidate(paymentId);
    return payment;
  }

  Future<Payment> cancel(String paymentId) async {
    final payment = await _repo.cancel(paymentId);
    _invalidate(paymentId);
    return payment;
  }
}

final paymentActionsProvider = Provider<PaymentActions>(PaymentActions.new);
