import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/room_request_models.dart';
import '../providers/room_request_providers.dart';

/// Where a quotation's rooms stand with the Reservation team. **Read-only.**
///
/// Mirrors the web `RoomConfirmationPanel`. There is no "ask" and no "withdraw": a request is
/// raised by the workflow when the customer accepts the quotation, which is the point at which
/// the sales side actually needs a real answer.
///
/// Sales used to be able to ask at any time as well, so one quotation could put two questions
/// into the Reservation inbox by two different routes, with no way to tell which was current.
/// Withdrawing went with it — without a way to ask again by hand, a cancelled request would
/// strand the quotation with no route to a confirmation. A question that no longer applies is
/// retired automatically when the quotation is revised.
///
/// [onUsableChange] is for wording, never for disabling an action.
class RoomConfirmationCard extends ConsumerStatefulWidget {
  const RoomConfirmationCard({
    super.key,
    required this.quotationId,
    required this.roomType,
    required this.checkInDate,
    required this.checkOutDate,
    this.onUsableChange,
  });

  final String quotationId;
  final String? roomType;
  final DateTime? checkInDate;
  final DateTime? checkOutDate;

  /// Fired whenever the confirmation becomes usable / unusable — for wording, not gating.
  final ValueChanged<bool>? onUsableChange;

  @override
  ConsumerState<RoomConfirmationCard> createState() => _RoomConfirmationCardState();
}

class _RoomConfirmationCardState extends ConsumerState<RoomConfirmationCard> {
  bool? _lastReported;

  /// Reports upward outside the build phase — calling a parent's setState during build
  /// would throw.
  void _report(bool usable) {
    if (_lastReported == usable) return;
    _lastReported = usable;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.onUsableChange?.call(usable);
    });
  }

  ({StatusTone tone, String title, String detail}) _describe(RoomRequest? request) {
    if (request == null) {
      return (
        tone: StatusTone.neutral,
        title: 'Not requested yet',
        detail: 'The Reservation team is asked automatically as soon as the customer '
            'accepts this quotation.',
      );
    }
    if (request.status == RoomRequestStatus.pending) {
      return (
        tone: StatusTone.warning,
        title: 'Waiting on Reservation',
        detail: 'Asked for ${request.quantity} x ${request.roomTypeRequested ?? "room"}. '
            "They are checking the hotel's system now.",
      );
    }
    if (request.status == RoomRequestStatus.rejected) {
      return (
        tone: StatusTone.danger,
        title: 'Reservation could not confirm',
        detail: request.reservationNote?.trim().isNotEmpty == true
            ? request.reservationNote!
            : 'No rooms for these dates. Revise the quotation with dates or a room type '
                  'they can meet.',
      );
    }
    // CONFIRMED, but it may no longer describe what the quotation says.
    final usable = request.coversQuotation(
      roomType: widget.roomType,
      checkIn: widget.checkInDate,
      checkOut: widget.checkOutDate,
    );
    if (!usable) {
      return request.isHoldExpired
          ? (
              tone: StatusTone.danger,
              title: 'Room hold expired',
              detail: 'Held until ${Formatters.dateTime(request.heldUntil)}. Revise the '
                  'quotation to get a fresh confirmation.',
            )
          : (
              tone: StatusTone.danger,
              title: 'Confirmation no longer matches',
              detail: 'The room type or dates changed after Reservation confirmed them. '
                  'Revise the quotation so a fresh request is raised.',
            );
    }
    return (
      tone: StatusTone.success,
      title: 'Rooms confirmed',
      detail: request.heldUntil == null
          ? '${request.quantity} x ${request.roomTypeRequested} confirmed by Reservation.'
          : '${request.quantity} x ${request.roomTypeRequested} held until '
                '${Formatters.dateTime(request.heldUntil)}.',
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final async = ref.watch(currentRoomRequestProvider(widget.quotationId));

    return SectionCard(
      title: 'Room availability',
      icon: Icons.bed_outlined,
      child: async.when(
        loading: () => const Padding(
          padding: EdgeInsets.symmetric(vertical: AppSpacing.sm),
          child: LinearProgressIndicator(),
        ),
        error: (e, _) => Text(
          e is AppException ? e.message : 'Could not load the room request.',
          style: theme.textTheme.bodySmall?.copyWith(color: scheme.error),
        ),
        data: (request) {
          final state = _describe(request);
          final usable =
              request?.coversQuotation(
                roomType: widget.roomType,
                checkIn: widget.checkInDate,
                checkOut: widget.checkOutDate,
              ) ??
              false;
          _report(usable);

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      state.title,
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  StatusChip(
                    tone: state.tone,
                    label: request?.status.wire ?? 'NOT REQUESTED',
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(
                state.detail,
                style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
              ),
              if (request?.respondedByName != null) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'Answered by ${request!.respondedByName}',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
              if (request != null) ...[
                const SizedBox(height: AppSpacing.sm),
                _DetailRow(
                  label: 'Requested',
                  value: '${request.quantity} x ${request.roomTypeRequested ?? "—"}',
                ),
                _DetailRow(
                  label: 'Stay',
                  value: '${Formatters.date(request.checkInDate)} → '
                      '${Formatters.date(request.checkOutDate)}',
                ),
                const _DetailRow(label: 'Source', value: 'Reservation'),
                if (request.respondedAt != null)
                  _DetailRow(
                    label: 'Answered',
                    value: Formatters.dateTime(request.respondedAt),
                  ),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xxs),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }
}
