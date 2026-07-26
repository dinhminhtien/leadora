import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../../interaction/presentation/widgets/interaction_summary_card.dart';
import '../../../room_request/presentation/widgets/room_confirmation_card.dart';
import '../../data/quotation_models.dart';
import '../../data/quotation_repository.dart';
import '../providers/quotation_providers.dart';
import '../widgets/quotation_action_sheets.dart';

/// View Quotation Status plus the full write flow the web app has: UC-14.2 Submit,
/// UC-14.4 Send, UC-14.6 Track Customer Response, UC-14.7 Convert to booking and
/// UC-14.8 Close.
///
/// [RoomConfirmationCard] sits above the actions as one condition on the quotation, not a
/// gate: the rep can see whether the Reservation team confirmed the rooms and ask them from
/// here, but Send and Convert stay available either way.
class QuotationDetailScreen extends ConsumerWidget {
  const QuotationDetailScreen({super.key, required this.quotationId});

  final String quotationId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(quotationDetailProvider(quotationId));

    return Scaffold(
      appBar: AppBar(title: const Text('Quotation detail')),
      body: AsyncValueView<Quotation>(
        value: async,
        onRetry: () => ref.invalidate(quotationDetailProvider(quotationId)),
        loading: const DetailSkeleton(),
        data: (quotation) => RefreshIndicator(
          onRefresh: () async =>
              ref.invalidate(quotationDetailProvider(quotationId)),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.xxxl),
            children: [
              _Header(quotation: quotation),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Customer',
                icon: Icons.person_outline,
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Name',
                      value: quotation.contactName,
                      icon: Icons.badge_outlined,
                    ),
                    InfoRow(
                      label: 'Email',
                      value: quotation.email,
                      icon: Icons.mail_outline,
                    ),
                    InfoRow(
                      label: 'Phone',
                      value: quotation.phone,
                      icon: Icons.phone_outlined,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SectionCard(
                title: 'Stay',
                icon: Icons.hotel_outlined,
                child: Column(
                  children: [
                    InfoRow(label: 'Room type', value: quotation.roomType),
                    InfoRow(
                      label: 'Check-in',
                      value: Formatters.date(quotation.checkInDate),
                    ),
                    InfoRow(
                      label: 'Check-out',
                      value: Formatters.date(quotation.checkOutDate),
                    ),
                    InfoRow(
                      label: 'Valid until',
                      value: Formatters.date(quotation.validUntil),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SectionCard(
                title: 'Pricing',
                icon: Icons.payments_outlined,
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Subtotal',
                      value: Formatters.money(quotation.subtotal),
                    ),
                    if ((quotation.discountPercent ?? 0) > 0)
                      InfoRow(
                        label: 'Discount',
                        value:
                            '${quotation.discountPercent}% (-${Formatters.money(quotation.discountAmount)})',
                      ),
                    InfoRow(
                      label: 'Total',
                      value: Formatters.money(quotation.totalAmount),
                    ),
                  ],
                ),
              ),
              if (quotation.dealId != null) ...[
                const SizedBox(height: 12),
                SectionCard(
                  title: 'Related deal',
                  icon: Icons.handshake_outlined,
                  child: ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      quotation.dealName?.trim().isNotEmpty == true
                          ? quotation.dealName!
                          : 'View deal',
                    ),
                    trailing: const Icon(
                      Icons.arrow_forward_ios_rounded,
                      size: 16,
                    ),
                    onTap: () =>
                        context.push(Routes.dealDetailPath(quotation.dealId!)),
                  ),
                ),
              ],
              if (quotation.notes != null &&
                  quotation.notes!.trim().isNotEmpty) ...[
                const SizedBox(height: 12),
                SectionCard(
                  title: 'Notes',
                  icon: Icons.sticky_note_2_outlined,
                  child: Text(
                    quotation.notes!,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
              ],
              if (quotation.customerId != null) ...[
                const SizedBox(height: 12),
                InteractionSummaryCard(
                  linkedType: 'customer',
                  linkedId: quotation.customerId!,
                  linkedName: quotation.contactName,
                ),
              ],
              // Rooms gate Send and Convert, so the state is shown before those buttons.
              // Hidden on statuses where neither action is reachable any more.
              if (_needsRoomConfirmation(quotation.status)) ...[
                const SizedBox(height: 12),
                RoomConfirmationCard(
                  quotationId: quotation.id,
                  roomType: quotation.roomType,
                  checkInDate: quotation.checkInDate,
                  checkOutDate: quotation.checkOutDate,
                  defaultQuantity: quotation.numberOfRooms ?? 1,
                ),
              ],
              const SizedBox(height: 20),
              // UC-14.2 — a DRAFT has to be submitted before it can go anywhere. The
              // backend decides from the discount whether that lands on APPROVED or
              // PENDING_APPROVAL, so this button does not promise either outcome.
              if (quotation.status == QuotationStatus.draft)
                FilledButton.icon(
                  onPressed: () => _submitForApproval(context, ref, quotation),
                  icon: const Icon(Icons.send_outlined),
                  label: const Text('Submit quotation'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(50),
                  ),
                ),
              // UC-14.4 — only APPROVED can be sent (backend enforces it too).
              if (quotation.status == QuotationStatus.approved) ...[
                const SizedBox(height: AppSpacing.sm),
                FilledButton.icon(
                  onPressed: () => _send(context, ref, quotation),
                  icon: const Icon(Icons.mail_outline_rounded),
                  label: const Text('Send to customer'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(50),
                  ),
                ),
              ],
              // UC-14.6 — what the customer said. Labelled the same as the web row action,
              // which says "Update Response" once one is already on record.
              if (quotation.status.canTrackCustomerResponse) ...[
                const SizedBox(height: AppSpacing.sm),
                FilledButton.icon(
                  onPressed: () => _showResponseSheet(context, ref, quotation),
                  icon: const Icon(Icons.reply_rounded),
                  label: Text(
                    quotation.status == QuotationStatus.interested
                        ? 'Update response'
                        : 'Record response',
                  ),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(50),
                  ),
                ),
              ],
              // UC-14.7 — only an ACCEPTED quotation becomes a booking.
              if (quotation.status == QuotationStatus.accepted) ...[
                const SizedBox(height: AppSpacing.sm),
                FilledButton.icon(
                  onPressed: () => _convert(context, ref, quotation),
                  icon: const Icon(Icons.event_available_outlined),
                  label: const Text('Convert to booking'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(50),
                  ),
                ),
              ],
              // UC-14.5 — a new version. Primary route out of REJECTED / PENDING_REVISION,
              // and available while the quotation is still open so a rep can re-price after
              // the customer pushes back. Matches the web action matrix.
              if (_canRevise(quotation.status)) ...[
                const SizedBox(height: AppSpacing.sm),
                _reviseButton(context, quotation),
              ],
              // UC-14.8 — closing is only meaningful while the quotation is still live.
              if (_canClose(quotation.status)) ...[
                const SizedBox(height: AppSpacing.sm),
                OutlinedButton.icon(
                  onPressed: () => _close(context, ref, quotation),
                  icon: const Icon(Icons.archive_outlined),
                  label: const Text('Close quotation'),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size.fromHeight(50),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  /// Statuses from which Send or Convert is still reachable, so the room state matters.
  static bool _needsRoomConfirmation(QuotationStatus status) => const {
    QuotationStatus.draft,
    QuotationStatus.pendingApproval,
    QuotationStatus.approved,
    QuotationStatus.sent,
    QuotationStatus.interested,
    QuotationStatus.accepted,
    QuotationStatus.pendingRevision,
  }.contains(status);

  /// Terminal statuses cannot be closed again (the backend rejects it).
  static bool _canClose(QuotationStatus status) => !const {
    QuotationStatus.converted,
    QuotationStatus.closed,
    QuotationStatus.expired,
  }.contains(status);

  /// Mirrors `ReviseQuotationUseCase.REVISABLE_STATUSES`. PENDING_APPROVAL is excluded:
  /// a manager is looking at that version right now.
  static bool _canRevise(QuotationStatus status) => const {
    QuotationStatus.draft,
    QuotationStatus.approved,
    QuotationStatus.sent,
    QuotationStatus.interested,
    QuotationStatus.accepted,
    QuotationStatus.rejected,
    QuotationStatus.pendingRevision,
  }.contains(status);

  /// Revising is the primary way out of a rejection, and a secondary option otherwise —
  /// styled to match, the same split the web row actions make.
  Widget _reviseButton(BuildContext context, Quotation quotation) {
    final isPrimaryRoute =
        quotation.status == QuotationStatus.rejected ||
        quotation.status == QuotationStatus.pendingRevision;
    void onPressed() => context.push(Routes.quotationRevisePath(quotation.id));
    const icon = Icon(Icons.call_split_rounded);
    const label = Text('Revise quotation');
    const style = ButtonStyle(
      minimumSize: WidgetStatePropertyAll(Size.fromHeight(50)),
    );

    return isPrimaryRoute
        ? FilledButton.icon(
            onPressed: onPressed,
            icon: icon,
            label: label,
            style: style,
          )
        : OutlinedButton.icon(
            onPressed: onPressed,
            icon: icon,
            label: label,
            style: style,
          );
  }

  /// UC-14.4 — send to the customer. A `ROOM_*` conflict here is the room gate refusing;
  /// the message already tells the rep what to do, so it is surfaced verbatim.
  Future<void> _send(BuildContext context, WidgetRef ref, Quotation quotation) async {
    final payload = await showSendQuotationSheet(context, quotation);
    if (payload == null || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(quotationActionsProvider).send(quotation.id, payload);
      messenger.showSnackBar(
        const SnackBar(content: Text('Quotation sent to the customer')),
      );
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  /// UC-14.7 — ACCEPTED quotation becomes a PENDING booking awaiting Reservation.
  Future<void> _convert(BuildContext context, WidgetRef ref, Quotation quotation) async {
    final payload = await showConvertToBookingSheet(context, quotation);
    if (payload == null || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      final booking = await ref
          .read(quotationActionsProvider)
          .convertToBooking(quotation.id, payload);
      messenger.showSnackBar(
        SnackBar(
          content: Text('Booking ${booking.bookingCode} created, awaiting confirmation'),
        ),
      );
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  /// UC-14.8 — close a quotation that will not proceed.
  Future<void> _close(BuildContext context, WidgetRef ref, Quotation quotation) async {
    final payload = await showCloseQuotationSheet(context);
    if (payload == null || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(quotationActionsProvider).close(quotation.id, payload);
      messenger.showSnackBar(const SnackBar(content: Text('Quotation closed')));
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  /// UC-14.2 — submit a DRAFT. Confirms first because the discount decides whether this
  /// goes straight to APPROVED or waits on a manager, and the rep should know which.
  Future<void> _submitForApproval(
    BuildContext context,
    WidgetRef ref,
    Quotation quotation,
  ) async {
    final overThreshold = (quotation.discountPercent ?? 0) > 10;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Submit quotation?'),
        content: Text(
          overThreshold
              ? 'The discount is above 10%, so this goes to a manager for approval '
                    'before it can be sent.'
              : 'The discount is within your authority, so this will be approved '
                    'immediately and can then be sent.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Submit'),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      final updated = await ref.read(quotationActionsProvider).submit(quotation.id);
      messenger.showSnackBar(
        SnackBar(content: Text('Quotation is now ${updated.status.wire.replaceAll("_", " ")}')),
      );
    } on AppException catch (e) {
      // NO_MANAGER_AVAILABLE lands here when approval is needed but no manager exists.
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _showResponseSheet(
    BuildContext context,
    WidgetRef ref,
    Quotation quotation,
  ) async {
    final selected = await showModalBottomSheet<CustomerResponseType>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(AppSpacing.xl, AppSpacing.xs, AppSpacing.xl, AppSpacing.md),
              child: Text('Customer response'),
            ),
            for (final r in CustomerResponseType.values)
              ListTile(
                onTap: () => Navigator.of(context).pop(r),
                leading: Icon(_iconFor(r)),
                title: Text(_labelFor(r)),
                trailing: const Icon(Icons.arrow_forward_rounded, size: 18),
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );

    if (selected == null) return;
    if (!context.mounted) return;

    final details = await _askResponseDetails(context, selected);
    if (details == null) return;
    if (!context.mounted) return;

    final user = ref.read(currentUserProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(quotationRepositoryProvider)
          .trackCustomerResponse(
            quotationId,
            TrackCustomerResponsePayload(
              customerResponse: selected,
              notes: details.notes,
              lostReason: details.lostReason,
              recordedByName: user?.name,
              recordedByRole: (user?.roles.isNotEmpty ?? false)
                  ? user!.roles.first
                  : null,
            ),
          );
      ref.invalidate(quotationDetailProvider(quotationId));
      messenger.showSnackBar(
        SnackBar(content: Text('Response recorded: ${_labelFor(selected)}')),
      );
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  /// Second step of Track Customer Response — collects free-text notes, and a
  /// mandatory reason when the customer rejected the quotation.
  Future<({String? notes, String? lostReason})?> _askResponseDetails(
    BuildContext context,
    CustomerResponseType type,
  ) {
    final notesController = TextEditingController();
    final reasonController = TextEditingController();
    final needsReason = type == CustomerResponseType.rejected;
    return showDialog<({String? notes, String? lostReason})>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_labelFor(type)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (needsReason) ...[
              TextField(
                controller: reasonController,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Reason',
                  hintText: 'Why was it rejected?',
                ),
              ),
              const SizedBox(height: 12),
            ],
            TextField(
              controller: notesController,
              autofocus: !needsReason,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Notes (optional)'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              if (needsReason && reasonController.text.trim().isEmpty) return;
              Navigator.pop(context, (
                notes: notesController.text.trim().isEmpty
                    ? null
                    : notesController.text.trim(),
                lostReason: reasonController.text.trim().isEmpty
                    ? null
                    : reasonController.text.trim(),
              ));
            },
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
  }

  static String _labelFor(CustomerResponseType r) => switch (r) {
    CustomerResponseType.accepted => 'Accepted',
    CustomerResponseType.rejected => 'Rejected',
    CustomerResponseType.interested => 'Interested',
    CustomerResponseType.needRevision => 'Needs revision',
  };

  static IconData _iconFor(CustomerResponseType r) => switch (r) {
    CustomerResponseType.accepted => Icons.check_circle_outline,
    CustomerResponseType.rejected => Icons.cancel_outlined,
    CustomerResponseType.interested => Icons.star_outline_rounded,
    CustomerResponseType.needRevision => Icons.edit_outlined,
  };
}

class _Header extends StatelessWidget {
  const _Header({required this.quotation});

  final Quotation quotation;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                quotation.quoteNo,
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              StatusChip(
                tone: quotation.status.tone,
                rawStatus: quotation.status.wire,
              ),
            ],
          ),
        ),
      ],
    );
  }
}
