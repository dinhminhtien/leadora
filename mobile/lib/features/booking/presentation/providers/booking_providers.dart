import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/booking_models.dart';
import '../../../payment/data/payment_models.dart';
import '../../../payment/data/payment_repository.dart';
import '../../data/booking_repository.dart';

/// Accumulated, filterable state for the booking list (infinite scroll).
class BookingListState {
  const BookingListState({
    this.items = const [],
    this.isLoadingMore = false,
    this.hasMore = true,
    this.nextPage = 0,
    this.filters = const BookingFilters(),
  });

  final List<Booking> items;
  final bool isLoadingMore;
  final bool hasMore;
  final int nextPage;
  final BookingFilters filters;

  BookingListState copyWith({
    List<Booking>? items,
    bool? isLoadingMore,
    bool? hasMore,
    int? nextPage,
    BookingFilters? filters,
  }) {
    return BookingListState(
      items: items ?? this.items,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      hasMore: hasMore ?? this.hasMore,
      nextPage: nextPage ?? this.nextPage,
      filters: filters ?? this.filters,
    );
  }
}

class BookingListController extends AutoDisposeAsyncNotifier<BookingListState> {
  static const _pageSize = 15;

  BookingRepository get _repo => ref.read(bookingRepositoryProvider);

  @override
  Future<BookingListState> build() => _fetch(const BookingListState());

  Future<BookingListState> _fetch(BookingListState base) async {
    final page = await _repo.getBookings(
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
    final current = state.valueOrNull ?? const BookingListState();
    state = const AsyncLoading<BookingListState>().copyWithPrevious(state);
    state = (await AsyncValue.guard(
      () => _fetch(current),
    )).copyWithPrevious(state);
  }

  Future<void> applyFilters(BookingFilters filters) async {
    final current = state.valueOrNull ?? const BookingListState();
    final next = current.copyWith(filters: filters);
    state = const AsyncLoading<BookingListState>().copyWithPrevious(
      AsyncData(next),
      isRefresh: true,
    );
    state = (await AsyncValue.guard(
      () => _fetch(next),
    )).copyWithPrevious(state);
  }

  BookingFilters get filters =>
      state.valueOrNull?.filters ?? const BookingFilters();

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.isLoadingMore) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final page = await _repo.getBookings(
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
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }
}

final bookingListControllerProvider =
    AutoDisposeAsyncNotifierProvider<BookingListController, BookingListState>(
      BookingListController.new,
    );

final bookingDetailProvider = AutoDisposeFutureProvider.family<Booking, String>(
  (ref, bookingId) => ref.watch(bookingRepositoryProvider).getBooking(bookingId),
);

/// Search term for the booking picker, kept separate from the list screen's
/// own filters so opening the picker does not disturb the list behind it.
final bookingPickerSearchProvider = StateProvider.autoDispose<String>((_) => '');

/// Bookings a payment request can actually be raised against, matching the picker's
/// search. Payment generation targets a booking, so the picker is the only way to obtain a
/// bookingId — and offering an ineligible one just produces a server-side rejection.
///
/// Eligibility mirrors the web `DepositPaymentScreen`: CONFIRMED bookings that do not
/// already carry a live payment. `PENDING` and `PAID` count as live; a FAILED, CANCELLED or
/// EXPIRED attempt leaves the booking open to a fresh request. Matching is by
/// `bookingCode` because that is the only booking field `PaymentResponse` carries.
final bookingPickerResultsProvider = AutoDisposeFutureProvider<List<Booking>>((
  ref,
) async {
  final search = ref.watch(bookingPickerSearchProvider);

  final confirmed = await ref.watch(bookingRepositoryProvider).getBookings(
    filters: BookingFilters(search: search, status: BookingStatus.confirmed),
    size: 20,
  );

  // One wide page rather than paging: the filter has to see every live payment, and a
  // booking missed here would be offered and then rejected on submit.
  final payments = await ref
      .watch(paymentRepositoryProvider)
      .getPayments(size: 500);

  final taken = {
    for (final p in payments.items)
      if (p.status == PaymentStatus.pending || p.status == PaymentStatus.paid)
        if (p.bookingCode != null) p.bookingCode,
  };

  return confirmed.items
      .where((b) => !taken.contains(b.bookingCode))
      .toList(growable: false);
});

