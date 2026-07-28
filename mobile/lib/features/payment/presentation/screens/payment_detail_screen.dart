import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

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
              // VietQR for an unpaid transfer — the same thing the web app shows, so a
              // rep with only the phone can put the code in front of the customer.
              // Hidden once paid/cancelled: a stale QR invites a duplicate transfer.
              if (payment.qrCodeUrl != null &&
                  payment.qrCodeUrl!.trim().isNotEmpty &&
                  payment.status == PaymentStatus.pending) ...[
                const SizedBox(height: AppSpacing.md),
                _QrCard(url: payment.qrCodeUrl!, amount: payment.amount),
              ],
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

/// VietQR for an unpaid transfer, mirroring the web payment screen.
///
/// The URL is generated server-side by `GeneratePaymentRequestUseCase` and points at
/// img.vietqr.io with the amount and the payment id in the memo, so the bank sweep
/// (`PaymentCheckScheduler`) can match the incoming transfer back to this payment.
/// Nothing is computed here — the widget only renders what the backend issued.
class _QrCard extends StatelessWidget {
  const _QrCard({required this.url, required this.amount});

  final String url;
  final double amount;

  Future<void> _open(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final uri = Uri.tryParse(url);
    if (uri == null || !await launchUrl(uri, mode: LaunchMode.externalApplication)) {
      messenger.showSnackBar(
        const SnackBar(content: Text('Could not open the QR image.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return SectionCard(
      title: 'Transfer QR',
      icon: Icons.qr_code_2_rounded,
      child: Column(
        children: [
          Text(
            'Show this to the customer. The transfer is matched back to this payment '
            'automatically once the bank clears it.',
            style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
          ),
          const SizedBox(height: AppSpacing.md),
          ClipRRect(
            borderRadius: BorderRadius.circular(AppRadii.md),
            child: CachedNetworkImage(
              imageUrl: url,
              width: 220,
              height: 220,
              fit: BoxFit.contain,
              placeholder: (_, _) => const SizedBox(
                width: 220,
                height: 220,
                child: Center(child: CircularProgressIndicator()),
              ),
              // The QR is served by an external host, so a failure here is a network
              // problem rather than a bad payment — say so instead of showing a broken box.
              errorWidget: (_, _, _) => Container(
                width: 220,
                height: 220,
                alignment: Alignment.center,
                color: scheme.surfaceContainerHighest,
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.md),
                  child: Text(
                    'QR image could not be loaded. Check the connection, or open it in a '
                    'browser.',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.bodySmall,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            Formatters.money(amount),
            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: AppSpacing.sm),
          OutlinedButton.icon(
            onPressed: () => _open(context),
            icon: const Icon(Icons.open_in_new_rounded, size: AppIconSize.md),
            label: const Text('Open QR image'),
          ),
        ],
      ),
    );
  }
}
