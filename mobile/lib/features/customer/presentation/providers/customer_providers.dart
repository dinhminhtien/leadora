import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../task/data/task_models.dart';
import '../../../task/data/task_repository.dart';
import '../../data/customer_models.dart';
import '../../data/customer_repository.dart';

/// Search + type/status filters backing the customer list. Mirrors the web
/// Customer Profiles screen, which sends the same query params server-side.
class CustomerFilters {
  const CustomerFilters({this.search = '', this.customerType, this.status});

  final String search;
  final CustomerType? customerType;
  final CustomerStatus? status;

  CustomerFilters copyWith({String? search}) => CustomerFilters(
    search: search ?? this.search,
    customerType: customerType,
    status: status,
  );

  /// copyWith can't null a field out, so type/status get explicit setters.
  CustomerFilters withType(CustomerType? type) =>
      CustomerFilters(search: search, customerType: type, status: status);

  CustomerFilters withStatus(CustomerStatus? s) =>
      CustomerFilters(search: search, customerType: customerType, status: s);
}

class CustomerListState {
  const CustomerListState({
    this.items = const [],
    this.filters = const CustomerFilters(),
    this.totalElements = 0,
    this.isLoadingMore = false,
    this.hasMore = true,
    this.nextPage = 0,
  });

  final List<Customer> items;
  final CustomerFilters filters;
  final int totalElements;
  final bool isLoadingMore;
  final bool hasMore;
  final int nextPage;

  CustomerListState copyWith({
    List<Customer>? items,
    CustomerFilters? filters,
    int? totalElements,
    bool? isLoadingMore,
    bool? hasMore,
    int? nextPage,
  }) {
    return CustomerListState(
      items: items ?? this.items,
      filters: filters ?? this.filters,
      totalElements: totalElements ?? this.totalElements,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      nextPage: nextPage ?? this.nextPage,
    );
  }
}

class CustomerListController
    extends AutoDisposeAsyncNotifier<CustomerListState> {
  static const _pageSize = 10;

  CustomerRepository get _repo => ref.read(customerRepositoryProvider);

  CustomerFilters get filters =>
      state.valueOrNull?.filters ?? const CustomerFilters();

  @override
  Future<CustomerListState> build() => _fetch(const CustomerListState());

  Future<CustomerListState> _fetch(CustomerListState base) async {
    final f = base.filters;
    final page = await _repo.getCustomers(
      search: f.search,
      customerType: f.customerType,
      status: f.status,
      page: 0,
      size: _pageSize,
    );
    return base.copyWith(
      items: page.items,
      totalElements: page.totalElements,
      nextPage: 1,
      hasMore: page.hasMore,
      isLoadingMore: false,
    );
  }

  Future<void> refresh() async {
    final current = state.valueOrNull ?? const CustomerListState();
    state = const AsyncLoading<CustomerListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(() => _fetch(current));
  }

  Future<void> applyFilters(CustomerFilters filters) async {
    final current = state.valueOrNull ?? const CustomerListState();
    state = const AsyncLoading<CustomerListState>().copyWithPrevious(state);
    state = await AsyncValue.guard(
      () => _fetch(current.copyWith(filters: filters)),
    );
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    final f = current.filters;
    try {
      final page = await _repo.getCustomers(
        search: f.search,
        customerType: f.customerType,
        status: f.status,
        page: current.nextPage,
        size: _pageSize,
      );
      state = AsyncData(
        current.copyWith(
          items: [...current.items, ...page.items],
          totalElements: page.totalElements,
          nextPage: current.nextPage + 1,
          hasMore: page.hasMore,
          isLoadingMore: false,
        ),
      );
    } catch (_) {
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }
}

final customerListControllerProvider =
    AutoDisposeAsyncNotifierProvider<CustomerListController, CustomerListState>(
      CustomerListController.new,
    );

/// Global counts for the stat cards. Kept alive so it survives list rebuilds;
/// invalidate after a create/update to refresh the numbers.
final customerStatsProvider = FutureProvider<CustomerStats>((ref) {
  return ref.watch(customerRepositoryProvider).getStats();
});

final customerDetailProvider =
    AutoDisposeFutureProvider.family<Customer, String>((ref, customerId) {
      return ref.watch(customerRepositoryProvider).getCustomer(customerId);
    });

final customerHistoryProvider =
    AutoDisposeFutureProvider.family<List<CustomerHistoryItem>, String>((
      ref,
      customerId,
    ) {
      return ref.watch(customerRepositoryProvider).getHistory(customerId);
    });

/// Follow-up tasks linked to a customer — reuses the task repository's
/// `customerId` filter. Pulls a generous page so the detail screen can compute
/// open/overdue/completed counts locally, mirroring the web behaviour.
final customerTasksProvider =
    AutoDisposeFutureProvider.family<List<Task>, String>((
      ref,
      customerId,
    ) async {
      final page = await ref
          .watch(taskRepositoryProvider)
          .getTasks(customerId: customerId, page: 0, size: 100);
      return page.items;
    });
