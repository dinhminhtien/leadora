import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/payment_models.dart';
import '../providers/payment_providers.dart';

/// UC-21.3 detail + UC-21.4 status update + UC-21.5 cancel.
///
/// Both actions are hidden once the payment leaves PENDING: the backend rejects
/// them ("Payment has already been processed"), so showing the buttons would
/// only produce an error the user cannot act on.
class PaymentDetailScreen extends ConsumerWidget {
  const PaymentDetailScreen({super.key, required this.paymentId});

  final String paymentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(paymentDetailProvider(paymentId));
    final payment = async.valueOrNull;
    final actionable = payment?.status == PaymentStatus.pending;

    return Scaffold(
      appBar: AppBar(title: const Text('Payment detail')),
      bottomNavigationBar: actionable
          ? _StickyActions(
              onMarkPaid: () => _markPaid(context, ref, payment!),
              onCancel: () => _cancel(context, ref, payment!),
            )
          : null,
      body: AsyncValueView<Payment>(
        value: async,
        onRetry: () => ref.invalidate(paymentDetailProvider(paymentId)),
        loading: const DetailSkeleton(),
        data: (payment) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(paymentDetailProvider(paymentId)),
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
                Formatters.money(payment.amount),
                style: Theme.of(
                  context,
                ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: AppSpacing.md),
              Wrap(
                spacing: AppSpacing.sm,
                runSpacing: AppSpacing.sm,
                children: [
                  StatusChip(
                    tone: payment.statusTone,
                    label: payment.displayStatus,
                  ),
                  if (payment.paymentType != null)
                    StatusChip(
                      tone: StatusTone.info,
                      label: payment.paymentType!.label,
                    ),
                  if (payment.isOverdue)
                    const StatusChip(
                      tone: StatusTone.danger,
                      label: 'Overdue',
                      icon: Icons.warning_amber_rounded,
                    ),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
              SectionCard(
                title: 'Booking',
                icon: Icons.hotel_outlined,
                child: Column(
                  children: [
                    InfoRow(label: 'Booking code', value: payment.bookingCode),
                    InfoRow(label: 'Customer', value: payment.customerName),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              SectionCard(
                title: 'Payment',
                icon: Icons.payments_outlined,
                child: Column(
                  children: [
                    InfoRow(label: 'Method', value: payment.paymentMethod),
                    InfoRow(label: 'Gateway', value: payment.gatewayProvider),
                    InfoRow(
                      label: 'Transaction',
                      value: payment.gatewayTransactionId,
                    ),
                    InfoRow(
                      label: 'Due date',
                      value: payment.dueDate == null
                          ? null
                          : Formatters.date(payment.dueDate),
                    ),
                    InfoRow(
                      label: 'Paid at',
                      value: payment.paidAt == null
                          ? null
                          : Formatters.dateTime(payment.paidAt),
                    ),
                  ],
                ),
              ),
              if (payment.notes != null && payment.notes!.trim().isNotEmpty) ...[
                const SizedBox(height: AppSpacing.md),
                SectionCard(
                  title: 'Notes',
                  icon: Icons.sticky_note_2_outlined,
                  child: Text(payment.notes!),
                ),
              ],
              const SizedBox(height: AppSpacing.md),
              SectionCard(
                title: 'Audit',
                icon: Icons.history_rounded,
                child: Column(
                  children: [
                    InfoRow(label: 'Created by', value: payment.createdByName),
                    InfoRow(
                      label: 'Created',
                      value: Formatters.dateTime(payment.createdAt),
                    ),
                    InfoRow(
                      label: 'Updated',
                      value: Formatters.dateTime(payment.updatedAt),
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

  Future<void> _markPaid(
    BuildContext context,
    WidgetRef ref,
    Payment payment,
  ) async {
    // BR-29: the backend rejects PAID without a verification note, so collect
    // one here rather than letting the request fail.
    final note = await showDialog<String>(
      context: context,
      builder: (_) => const _VerificationNoteDialog(),
    );
    if (note == null || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(paymentActionsProvider)
          .updateStatus(
            payment.paymentId,
            PaymentStatus.paid,
            verificationNote: note,
          );
      messenger.showSnackBar(const SnackBar(content: Text('Payment marked paid')));
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _cancel(
    BuildContext context,
    WidgetRef ref,
    Payment payment,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Cancel payment request'),
        content: const Text(
          'The customer will no longer be able to pay against this request. '
          'This cannot be undone.',
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
    if (confirmed != true || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(paymentActionsProvider).cancel(payment.paymentId);
      messenger.showSnackBar(
        const SnackBar(content: Text('Payment request cancelled')),
      );
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }
}

/// Collects the mandatory verification note before a payment is marked paid.
class _VerificationNoteDialog extends StatefulWidget {
  const _VerificationNoteDialog();

  @override
  State<_VerificationNoteDialog> createState() =>
      _VerificationNoteDialogState();
}

class _VerificationNoteDialogState extends State<_VerificationNoteDialog> {
  final _controller = TextEditingController();
  final _formKey = GlobalKey<FormState>();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Mark as paid'),
      content: Form(
        key: _formKey,
        child: TextFormField(
          controller: _controller,
          autofocus: true,
          maxLines: 3,
          decoration: const InputDecoration(
            labelText: 'Verification note *',
            hintText: 'How was this payment verified?',
            alignLabelWithHint: true,
          ),
          validator: (v) => (v?.trim().isEmpty ?? true)
              ? 'A verification note is required'
              : null,
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () {
            if (_formKey.currentState!.validate()) {
              Navigator.of(context).pop(_controller.text.trim());
            }
          },
          child: const Text('Confirm'),
        ),
      ],
    );
  }
}

/// Matches the 52dp height the theme gives every button, keeping the icon-only
/// cancel square and flush with the primary action beside it.
const double _cancelButtonSize = 52;

class _StickyActions extends StatelessWidget {
  const _StickyActions({required this.onMarkPaid, required this.onCancel});

  final VoidCallback onMarkPaid;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: scheme.surface,
        border: Border(
          top: BorderSide(color: scheme.outlineVariant.withValues(alpha: 0.6)),
        ),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.lg,
            AppSpacing.md,
            AppSpacing.lg,
            AppSpacing.md,
          ),
          child: Row(
            children: [
              // Icon-only, same reason as the task detail bar: a labelled
              // secondary button wraps at 320dp and breaks the shared baseline.
              Tooltip(
                message: 'Cancel request',
                child: OutlinedButton(
                  onPressed: onCancel,
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size.square(_cancelButtonSize),
                    fixedSize: const Size.square(_cancelButtonSize),
                    padding: EdgeInsets.zero,
                    foregroundColor: scheme.error,
                    side: BorderSide(
                      color: scheme.error.withValues(alpha: 0.4),
                    ),
                  ),
                  child: const Icon(
                    Icons.cancel_outlined,
                    size: AppIconSize.lg,
                    semanticLabel: 'Cancel request',
                  ),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: FilledButton.icon(
                  onPressed: onMarkPaid,
                  icon: const Icon(
                    Icons.check_circle_outline_rounded,
                    size: AppIconSize.lg,
                  ),
                  label: const Text(
                    'Mark as paid',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
