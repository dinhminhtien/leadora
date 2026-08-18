import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/handover_models.dart';
import '../providers/handover_providers.dart';

/// One handover Sales submitted, with everything the arrival desk was told.
///
/// Readiness is shown but not editable: only `/arrival-handovers/{id}/readiness` accepts
/// that change and only FO/MANAGER/ADMIN may call it, so preparing an arrival happens on
/// the web app. A `NEED_CLARIFICATION` note is surfaced here because answering it is the
/// Sales rep's job.
class HandoverDetailScreen extends ConsumerWidget {
  const HandoverDetailScreen({super.key, required this.handoverId});

  final String handoverId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(operationalHandoverDetailProvider(handoverId));

    void refresh() => ref.invalidate(operationalHandoverDetailProvider(handoverId));

    return Scaffold(
      appBar: AppBar(title: const Text('Handover detail')),
      body: AsyncValueView<Handover>(
        value: async,
        onRetry: refresh,
        loading: const DetailSkeleton(),
        data: (handover) {
          debugPrint('Handover received in detail: ID=${handover.handoverId}, ref=${handover.paymentReference}');
          return RefreshIndicator(
            onRefresh: () async => refresh(),
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
                handover.bookingCode ?? 'Handover',
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
                    tone: handover.statusTone,
                    label: handover.displayStatus,
                  ),
                  StatusChip(
                    tone: handover.readinessTone,
                    label: handover.displayReadiness,
                  ),
                  if (handover.isArrivingToday)
                    const StatusChip(
                      tone: StatusTone.danger,
                      label: 'Arriving',
                      icon: Icons.flight_land_rounded,
                    ),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),

              SectionCard(
                title: 'Guest',
                icon: Icons.person_outline_rounded,
                child: Column(
                  children: [
                    InfoRow(label: 'Customer', value: handover.customerName),
                    InfoRow(label: 'Phone', value: handover.customerPhone),
                    InfoRow(
                      label: 'Arrival',
                      value: handover.checkInDate == null
                          ? null
                          : Formatters.date(handover.checkInDate),
                    ),
                    InfoRow(
                      label: 'Departure',
                      value: handover.checkOutDate == null
                          ? null
                          : Formatters.date(handover.checkOutDate),
                    ),
                  ],
                ),
              ),

              if (handover.rooms.isNotEmpty) ...[
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  title: 'Rooms',
                  icon: Icons.meeting_room_outlined,
                  child: Column(
                    children: [
                      for (final room in handover.rooms)
                        InfoRow(
                          label: room.productName ?? 'Room',
                          value: [
                            if (room.quantity != null) '${room.quantity}×',
                            if (room.roomNumber != null) 'Room ${room.roomNumber}',
                            if (room.nights != null) '${room.nights}n',
                          ].join(' · '),
                        ),
                    ],
                  ),
                ),
              ],

              // The notes the desk actually acts on. Only rendered when present so the
              // screen does not fill with empty rows.
              if (_hasAnyNote(handover)) ...[
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  title: 'Notes for the desk',
                  icon: Icons.notes_rounded,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _Note(label: 'Special requests', value: handover.specialRequests),
                      _Note(label: 'Room preferences', value: handover.roomPreferences),
                      _Note(label: 'VIP notes', value: handover.vipNotes),
                      _Note(label: 'Operational', value: handover.operationalNotes),
                    ],
                  ),
                ),
              ],

              if (handover.clarificationNote != null &&
                  handover.clarificationNote!.trim().isNotEmpty) ...[
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  title: 'Clarification requested',
                  icon: Icons.help_outline_rounded,
                  child: Text(handover.clarificationNote!),
                ),
              ],

              if (handover.paymentReference != null) ...[
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  title: 'Payment',
                  icon: Icons.payments_outlined,
                  child: InfoRow(
                    label: 'Reference',
                    value: handover.paymentReference,
                  ),
                ),
              ],

              const SizedBox(height: AppSpacing.md),
              SectionCard(
                title: 'Audit',
                icon: Icons.history_rounded,
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Submitted',
                      value: handover.submittedAt == null
                          ? null
                          : Formatters.dateTime(handover.submittedAt),
                    ),
                    InfoRow(
                      label: 'Acknowledged',
                      value: handover.acknowledgedAt == null
                          ? null
                          : Formatters.dateTime(handover.acknowledgedAt),
                    ),
                    InfoRow(label: 'Last updated by', value: handover.updatedByName),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    ),
  );
  }

  static bool _hasAnyNote(Handover h) =>
      [h.specialRequests, h.roomPreferences, h.vipNotes, h.operationalNotes]
          .any((v) => v != null && v.trim().isNotEmpty);

}

class _Note extends StatelessWidget {
  const _Note({required this.label, required this.value});

  final String label;
  final String? value;

  @override
  Widget build(BuildContext context) {
    if (value == null || value!.trim().isEmpty) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: AppSpacing.xxs),
          Text(value!, style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }
}
