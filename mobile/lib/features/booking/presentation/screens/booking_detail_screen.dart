import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/booking_models.dart';
import '../providers/booking_providers.dart';

/// Booking detail — stay window, room lines, totals and the request's audit
/// trail. Read-only: `PUT /bookings/{id}/process` is a reservation-desk action
/// with no mobile screen.
class BookingDetailScreen extends ConsumerWidget {
  const BookingDetailScreen({super.key, required this.bookingId});

  final String bookingId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(bookingDetailProvider(bookingId));

    return Scaffold(
      appBar: AppBar(title: const Text('Booking detail')),
      body: AsyncValueView<Booking>(
        value: async,
        onRetry: () => ref.invalidate(bookingDetailProvider(bookingId)),
        loading: const DetailSkeleton(),
        data: (booking) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(bookingDetailProvider(bookingId)),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.lg,
              AppSpacing.lg,
              AppSpacing.lg,
              AppSpacing.xxxl,
            ),
            children: [
              Text(
                booking.bookingCode ?? 'Booking',
                style: Theme.of(
                  context,
                ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: AppSpacing.md),
              Wrap(
                spacing: AppSpacing.sm,
                runSpacing: AppSpacing.sm,
                children: [
                  StatusChip(
                    tone: booking.statusTone,
                    label: booking.displayStatus,
                  ),
                  if (booking.nights != null)
                    StatusChip(
                      tone: StatusTone.info,
                      label: '${booking.nights} night(s)',
                      icon: Icons.nights_stay_outlined,
                    ),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
              SectionCard(
                title: 'Stay',
                icon: Icons.calendar_month_outlined,
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Check-in',
                      value: booking.checkInDate == null
                          ? null
                          : Formatters.date(booking.checkInDate),
                    ),
                    InfoRow(
                      label: 'Check-out',
                      value: booking.checkOutDate == null
                          ? null
                          : Formatters.date(booking.checkOutDate),
                    ),
                    InfoRow(label: 'Customer', value: booking.customerName),
                    InfoRow(label: 'Owner', value: booking.assignedUserName),
                  ],
                ),
              ),
              if (booking.lines.isNotEmpty) ...[
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  title: 'Rooms',
                  icon: Icons.meeting_room_outlined,
                  child: Column(
                    children: [
                      for (final line in booking.lines)
                        _RoomLine(line: line),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: AppSpacing.md),
              SectionCard(
                title: 'Total',
                icon: Icons.payments_outlined,
                child: InfoRow(
                  label: 'Amount',
                  value: Formatters.money(booking.totalAmount),
                ),
              ),
              if (booking.specialRequests != null &&
                  booking.specialRequests!.trim().isNotEmpty) ...[
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  title: 'Special requests',
                  icon: Icons.notes_rounded,
                  child: Text(booking.specialRequests!),
                ),
              ],
              if (booking.rejectionReason != null &&
                  booking.rejectionReason!.trim().isNotEmpty) ...[
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  title: 'Rejection reason',
                  icon: Icons.block_rounded,
                  child: Text(booking.rejectionReason!),
                ),
              ],
              const SizedBox(height: AppSpacing.md),
              SectionCard(
                title: 'Audit',
                icon: Icons.history_rounded,
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Created',
                      value: Formatters.dateTime(booking.createdAt),
                    ),
                    InfoRow(
                      label: 'Updated',
                      value: Formatters.dateTime(booking.updatedAt),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RoomLine extends StatelessWidget {
  const _RoomLine({required this.line});

  final BookingLine line;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final parts = <String>[
      if (line.quantity != null) '${line.quantity}×',
      if (line.roomNumber != null) 'Room ${line.roomNumber}',
      if (line.nights != null) '${line.nights}n',
    ];

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  line.productName ?? 'Room',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (parts.isNotEmpty)
                  Text(
                    parts.join(' · '),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          Text(
            Formatters.money(line.lineTotal),
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}
