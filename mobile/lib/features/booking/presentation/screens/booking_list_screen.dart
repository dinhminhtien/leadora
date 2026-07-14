import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_filter_chip.dart';
import '../../../../shared/widgets/app_search_field.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/list_skeleton.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/booking_models.dart';
import '../providers/booking_providers.dart';

/// Dummy row for the loading skeleton — same widget as a real row so the list
/// keeps its shape when data lands.
const _skeletonBooking = Booking(
  bookingId: '',
  bookingCode: 'BK-0000-0000',
  customerName: 'Placeholder customer name',
  status: BookingStatus.confirmed,
  totalAmount: 24000000,
);

/// Booking list — search, status chips, pull-to-refresh and infinite scroll.
///
/// Room availability and the booking-request composer are not built yet; see
/// [BookingRepository] for the endpoints still unwrapped.
class BookingListScreen extends ConsumerStatefulWidget {
  const BookingListScreen({super.key});

  @override
  ConsumerState<BookingListScreen> createState() => _BookingListScreenState();
}

class _BookingListScreenState extends ConsumerState<BookingListScreen> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  BookingListController get _controller =>
      ref.read(bookingListControllerProvider.notifier);

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      _controller.loadMore();
    }
  }

  void _onSearchChanged(String term) =>
      _controller.applyFilters(_controller.filters.copyWith(search: term));

  @override
  Widget build(BuildContext context) {
    final asyncState = ref.watch(bookingListControllerProvider);
    final filters = asyncState.valueOrNull?.filters ?? const BookingFilters();

    return Scaffold(
      appBar: AppBar(title: const Text('Bookings')),
      body: Column(
        children: [
          AppSearchField(
            hintText: 'Search booking code, customer…',
            initialValue: filters.search,
            onChanged: _onSearchChanged,
          ),
          AppFilterChipBar(
            children: [
              AppFilterChip(
                label: 'All',
                selected: filters.status == null,
                onTap: () =>
                    _controller.applyFilters(filters.copyWith(status: null)),
              ),
              for (final s in BookingStatus.values)
                AppFilterChip(
                  label: s.label,
                  selected: filters.status == s,
                  onTap: () =>
                      _controller.applyFilters(filters.copyWith(status: s)),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Expanded(
            child: AsyncValueView<BookingListState>(
              value: asyncState,
              onRetry: _controller.refresh,
              loading: ListSkeleton(
                separatorHeight: AppSpacing.sm,
                itemBuilder: (_) => const _BookingCard(booking: _skeletonBooking),
              ),
              isEmpty: (s) => s.items.isEmpty,
              empty: const EmptyState(
                icon: Icons.hotel_outlined,
                title: 'No bookings',
                message: 'Confirmed reservations will show up here.',
              ),
              data: (s) => RefreshIndicator(
                onRefresh: _controller.refresh,
                child: ListView.separated(
                  controller: _scrollController,
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.lg,
                    AppSpacing.xs,
                    AppSpacing.lg,
                    AppSpacing.xxxl,
                  ),
                  itemCount: s.items.length + (s.hasMore ? 1 : 0),
                  separatorBuilder: (_, _) =>
                      const SizedBox(height: AppSpacing.sm),
                  itemBuilder: (context, index) {
                    if (index >= s.items.length) {
                      return const Padding(
                        padding: EdgeInsets.all(AppSpacing.lg),
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    return _BookingCard(booking: s.items[index]);
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _BookingCard extends StatelessWidget {
  const _BookingCard({required this.booking});

  final Booking booking;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final nights = booking.nights;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.pushNamed(
          RouteNames.bookingDetail,
          pathParameters: {'id': booking.bookingId},
        ),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      booking.bookingCode ?? 'Booking',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  StatusChip(
                    tone: booking.statusTone,
                    label: booking.displayStatus,
                    dense: true,
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(
                booking.customerName ?? 'Unknown customer',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      Formatters.money(booking.totalAmount),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                        color: scheme.primary,
                      ),
                    ),
                  ),
                  if (booking.checkInDate != null) ...[
                    Icon(
                      Icons.event_rounded,
                      size: AppIconSize.xs,
                      color: scheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: AppSpacing.xs),
                    Text(
                      nights == null
                          ? Formatters.shortDate(booking.checkInDate)
                          : '${Formatters.shortDate(booking.checkInDate)} · ${nights}n',
                      style: theme.textTheme.labelMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
