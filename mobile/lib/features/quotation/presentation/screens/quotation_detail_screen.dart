import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../../interaction/presentation/widgets/interaction_summary_card.dart';
import '../../data/quotation_models.dart';
import '../../data/quotation_repository.dart';
import '../providers/quotation_providers.dart';

/// View Quotation Status + UC-14.6 Track Customer Response.
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
        data: (quotation) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(quotationDetailProvider(quotationId)),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              _Header(quotation: quotation),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Customer',
                icon: Icons.person_outline,
                child: Column(
                  children: [
                    InfoRow(label: 'Name', value: quotation.contactName, icon: Icons.badge_outlined),
                    InfoRow(label: 'Email', value: quotation.email, icon: Icons.mail_outline),
                    InfoRow(label: 'Phone', value: quotation.phone, icon: Icons.phone_outlined),
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
                    InfoRow(label: 'Check-in', value: Formatters.date(quotation.checkInDate)),
                    InfoRow(label: 'Check-out', value: Formatters.date(quotation.checkOutDate)),
                    InfoRow(label: 'Valid until', value: Formatters.date(quotation.validUntil)),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SectionCard(
                title: 'Pricing',
                icon: Icons.payments_outlined,
                child: Column(
                  children: [
                    InfoRow(label: 'Subtotal', value: Formatters.money(quotation.subtotal)),
                    if ((quotation.discountPercent ?? 0) > 0)
                      InfoRow(
                        label: 'Discount',
                        value: '${quotation.discountPercent}% (-${Formatters.money(quotation.discountAmount)})',
                      ),
                    InfoRow(label: 'Total', value: Formatters.money(quotation.totalAmount)),
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
                    title: Text(quotation.dealName?.trim().isNotEmpty == true
                        ? quotation.dealName!
                        : 'View deal'),
                    trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 16),
                    onTap: () => context.push(Routes.dealDetailPath(quotation.dealId!)),
                  ),
                ),
              ],
              if (quotation.notes != null && quotation.notes!.trim().isNotEmpty) ...[
                const SizedBox(height: 12),
                SectionCard(
                  title: 'Notes',
                  icon: Icons.sticky_note_2_outlined,
                  child: Text(quotation.notes!, style: Theme.of(context).textTheme.bodyMedium),
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
              const SizedBox(height: 20),
              if (quotation.status.canTrackCustomerResponse)
                FilledButton.icon(
                  onPressed: () => _showResponseSheet(context, ref, quotation),
                  icon: const Icon(Icons.reply_rounded),
                  label: const Text('Track customer response'),
                  style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(50)),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showResponseSheet(BuildContext context, WidgetRef ref, Quotation quotation) async {
    final selected = await showModalBottomSheet<CustomerResponseType>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 4, 20, 12),
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
      await ref.read(quotationRepositoryProvider).trackCustomerResponse(
            quotationId,
            TrackCustomerResponsePayload(
              customerResponse: selected,
              notes: details.notes,
              lostReason: details.lostReason,
              recordedByName: user?.name,
              recordedByRole: (user?.roles.isNotEmpty ?? false) ? user!.roles.first : null,
            ),
          );
      ref.invalidate(quotationDetailProvider(quotationId));
      messenger.showSnackBar(SnackBar(content: Text('Response recorded: ${_labelFor(selected)}')));
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
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(
            onPressed: () {
              if (needsReason && reasonController.text.trim().isEmpty) return;
              Navigator.pop(context, (
                notes: notesController.text.trim().isEmpty ? null : notesController.text.trim(),
                lostReason:
                    reasonController.text.trim().isEmpty ? null : reasonController.text.trim(),
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
              Text(quotation.quoteNo,
                  style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              StatusChip(tone: quotation.status.tone, rawStatus: quotation.status.wire),
            ],
          ),
        ),
      ],
    );
  }
}
