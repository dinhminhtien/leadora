import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/room_request_models.dart';
import '../../data/room_request_repository.dart';
import '../providers/room_request_providers.dart';

/// The room-confirmation state of one quotation, plus the button to ask about it.
///
/// Mirrors the web `RoomConfirmationPanel`. Advisory: room confirmation is a condition
/// recorded against a quotation, not a precondition for sending or converting it. The rep
/// sees whether the Reservation team has confirmed the rooms and decides for themselves —
/// [onUsableChange] is for wording, never for disabling an action.
class RoomConfirmationCard extends ConsumerStatefulWidget {
  const RoomConfirmationCard({
    super.key,
    required this.quotationId,
    required this.roomType,
    required this.checkInDate,
    required this.checkOutDate,
    this.defaultQuantity = 1,
    this.onUsableChange,
  });

  final String quotationId;
  final String? roomType;
  final DateTime? checkInDate;
  final DateTime? checkOutDate;
  final int defaultQuantity;

  /// Fired whenever the confirmation becomes usable / unusable — for wording, not gating.
  final ValueChanged<bool>? onUsableChange;

  @override
  ConsumerState<RoomConfirmationCard> createState() => _RoomConfirmationCardState();
}

class _RoomConfirmationCardState extends ConsumerState<RoomConfirmationCard> {
  bool _asking = false;
  bool _cancelling = false;
  String? _error;
  late int _quantity = widget.defaultQuantity;
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

  Future<void> _ask() async {
    setState(() {
      _asking = true;
      _error = null;
    });
    try {
      await ref.read(roomRequestRepositoryProvider).create(
        CreateRoomRequestPayload(quotationId: widget.quotationId, quantity: _quantity),
      );
      ref.invalidate(roomRequestsByQuotationProvider(widget.quotationId));
    } on AppException catch (e) {
      // NO_RESERVATION_STAFF and QUOTATION_DATES_MISSING surface here in plain words.
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _asking = false);
    }
  }

  /// UC-26.4 — withdraw a request the Reservation team has not answered yet.
  ///
  /// Confirmed first because it is visible to another team: cancelling pulls the request
  /// out of their inbox. A3 — dismissing the sheet leaves the request untouched.
  Future<void> _cancel(RoomRequest request) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Cancel this room request?'),
        content: const Text(
          'The Reservation team will stop seeing it in their inbox. The request stays '
          'in the history, and you can ask again at any time.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Keep it'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Cancel request'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _cancelling = true;
      _error = null;
    });
    try {
      await ref.read(roomRequestRepositoryProvider).cancel(request.requestId);
      ref.invalidate(roomRequestsByQuotationProvider(widget.quotationId));
    } on AppException catch (e) {
      // ROOM_REQUEST_ALREADY_PROCESSED when Reservation answered first.
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _cancelling = false);
    }
  }

  ({StatusTone tone, String title, String detail}) _describe(RoomRequest? request) {
    if (request == null) {
      return (
        tone: StatusTone.neutral,
        title: 'Rooms not requested yet',
        detail: 'The Reservation team has not been asked. Ask them so the customer gets a '
            'quotation you can honour.',
      );
    }
    if (request.status == RoomRequestStatus.pending) {
      return (
        tone: StatusTone.warning,
        title: 'Waiting on Reservation',
        detail: 'Asked for ${request.quantity} x ${request.roomTypeRequested ?? "room"}. '
            'They are checking the hotel system now.',
      );
    }
    if (request.status == RoomRequestStatus.rejected) {
      return (
        tone: StatusTone.danger,
        title: 'Reservation could not confirm',
        detail: request.reservationNote?.trim().isNotEmpty == true
            ? request.reservationNote!
            : 'No rooms for these dates. Revise the quotation and ask again.',
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
              detail: 'Held until ${Formatters.dateTime(request.heldUntil)}. Ask again for '
                  'a fresh confirmation.',
            )
          : (
              tone: StatusTone.danger,
              title: 'Confirmation no longer matches',
              detail: 'The room type or dates changed after Reservation confirmed them. '
                  'Ask again for the current details.',
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
      title: 'Room confirmation',
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

          final canAsk = !usable && request?.status != RoomRequestStatus.pending;

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
              if (canAsk) ...[
                const SizedBox(height: AppSpacing.md),
                // Stepper and button stack rather than share a row: the theme sizes filled
                // buttons full-width (`Size.fromHeight`), which is an infinite width
                // constraint inside a Row, and both would not fit at 320dp / scale 1.3.
                Row(
                  children: [
                    Text('Rooms', style: theme.textTheme.labelMedium),
                    const SizedBox(width: AppSpacing.sm),
                    IconButton(
                      onPressed: _quantity > 1
                          ? () => setState(() => _quantity--)
                          : null,
                      icon: const Icon(Icons.remove_circle_outline_rounded),
                      visualDensity: VisualDensity.compact,
                    ),
                    Text(
                      '$_quantity',
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    IconButton(
                      onPressed: () => setState(() => _quantity++),
                      icon: const Icon(Icons.add_circle_outline_rounded),
                      visualDensity: VisualDensity.compact,
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.sm),
                FilledButton.tonalIcon(
                  onPressed: _asking ? null : _ask,
                  icon: const Icon(Icons.send_rounded, size: AppIconSize.md),
                  label: Text(_asking ? 'Sending…' : 'Ask Reservation'),
                ),
              ],
              if (request != null && request.status == RoomRequestStatus.pending) ...[
                const SizedBox(height: AppSpacing.sm),
                TextButton.icon(
                  onPressed: _cancelling ? null : () => _cancel(request),
                  icon: const Icon(Icons.cancel_outlined, size: AppIconSize.md),
                  label: Text(_cancelling ? 'Cancelling…' : 'Cancel request'),
                  style: TextButton.styleFrom(foregroundColor: scheme.error),
                ),
              ],
              if (_error != null) ...[
                const SizedBox(height: AppSpacing.sm),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(AppSpacing.sm),
                  decoration: BoxDecoration(
                    color: scheme.errorContainer,
                    borderRadius: BorderRadius.circular(AppRadii.sm),
                  ),
                  child: Text(
                    _error!,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: scheme.onErrorContainer,
                    ),
                  ),
                ),
              ],
            ],
          );
        },
      ),
    );
  }
}
