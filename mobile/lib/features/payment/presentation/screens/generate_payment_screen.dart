import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_search_field.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../booking/data/booking_models.dart';
import '../../../booking/presentation/providers/booking_providers.dart';
import '../../data/payment_models.dart';
import '../providers/payment_providers.dart';

/// UC-21.1 — Generate a payment request against a booking.
///
/// The booking is mandatory server-side, so it is picked first: without a
/// bookingId there is nothing to submit.
class GeneratePaymentScreen extends ConsumerStatefulWidget {
  const GeneratePaymentScreen({super.key});

  @override
  ConsumerState<GeneratePaymentScreen> createState() =>
      _GeneratePaymentScreenState();
}

class _GeneratePaymentScreenState extends ConsumerState<GeneratePaymentScreen> {
  final _formKey = GlobalKey<FormState>();
  final _amount = TextEditingController();
  final _method = TextEditingController();
  final _notes = TextEditingController();

  Booking? _booking;
  PaymentType _type = PaymentType.deposit;
  DateTime? _dueDate;
  bool _submitting = false;
  bool _autovalidate = false;

  @override
  void dispose() {
    for (final c in [_amount, _method, _notes]) {
      c.dispose();
    }
    super.dispose();
  }

  String? _validateAmount(String? v) {
    final raw = v?.trim() ?? '';
    if (raw.isEmpty) return 'Amount is required';
    final parsed = double.tryParse(raw);
    if (parsed == null) return 'Enter a number';
    // Mirrors the backend's @Positive constraint.
    if (parsed <= 0) return 'Amount must be greater than zero';
    return null;
  }

  Future<void> _pickBooking() async {
    final picked = await showModalBottomSheet<Booking>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const _BookingPickerSheet(),
    );
    if (picked != null) setState(() => _booking = picked);
  }

  Future<void> _pickDueDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _dueDate ?? now,
      firstDate: now,
      lastDate: DateTime(now.year + 2),
    );
    if (picked != null) setState(() => _dueDate = picked);
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    final booking = _booking;
    if (booking == null) {
      setState(() => _autovalidate = true);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Pick a booking first')),
      );
      return;
    }
    if (!_formKey.currentState!.validate()) {
      setState(() => _autovalidate = true);
      return;
    }

    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);

    String? trimmedOrNull(TextEditingController c) {
      final text = c.text.trim();
      return text.isEmpty ? null : text;
    }

    try {
      await ref
          .read(paymentActionsProvider)
          .generate(
            GeneratePaymentPayload(
              bookingId: booking.bookingId,
              amount: double.parse(_amount.text.trim()),
              paymentType: _type,
              paymentMethod: trimmedOrNull(_method),
              notes: trimmedOrNull(_notes),
              dueDate: _dueDate,
            ),
          );
      messenger.showSnackBar(
        const SnackBar(content: Text('Payment request created')),
      );
      router.pop();
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final booking = _booking;
    final bookingBlocks = booking?.status?.blocksPayment ?? false;

    return Scaffold(
      appBar: AppBar(title: const Text('New payment request')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          autovalidateMode: _autovalidate
              ? AutovalidateMode.onUserInteraction
              : AutovalidateMode.disabled,
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            children: [
              InkWell(
                onTap: _pickBooking,
                borderRadius: BorderRadius.circular(AppRadii.sm),
                child: InputDecorator(
                  decoration: InputDecoration(
                    labelText: 'Booking *',
                    prefixIcon: const Icon(Icons.hotel_outlined),
                    errorText: _autovalidate && booking == null
                        ? 'Pick a booking'
                        : null,
                  ),
                  child: Text(
                    booking == null
                        ? 'Tap to choose'
                        : '${booking.bookingCode ?? 'Booking'} · ${booking.customerName ?? ''}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: booking == null
                        ? theme.textTheme.bodyLarge?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          )
                        : theme.textTheme.bodyLarge,
                  ),
                ),
              ),
              // BR-44: the backend rejects payments on a cancelled or
              // checked-out booking, so say so before the request is sent.
              if (bookingBlocks) ...[
                const SizedBox(height: AppSpacing.sm),
                Text(
                  'This booking is ${booking!.displayStatus.toLowerCase()} — '
                  'payments cannot be raised against it.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.error,
                  ),
                ),
              ],
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _amount,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Amount *',
                  prefixIcon: Icon(Icons.payments_outlined),
                ),
                validator: _validateAmount,
              ),
              const SizedBox(height: AppSpacing.lg),
              Text(
                'Payment type',
                style: theme.textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Wrap(
                spacing: AppSpacing.sm,
                children: [
                  for (final t in PaymentType.values)
                    ChoiceChip(
                      label: Text(t.label),
                      selected: _type == t,
                      onSelected: (_) => setState(() => _type = t),
                    ),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _method,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Payment method',
                  prefixIcon: Icon(Icons.credit_card_outlined),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              InkWell(
                onTap: _pickDueDate,
                borderRadius: BorderRadius.circular(AppRadii.sm),
                child: InputDecorator(
                  decoration: const InputDecoration(
                    labelText: 'Due date',
                    prefixIcon: Icon(Icons.event_outlined),
                  ),
                  child: Text(
                    _dueDate == null ? 'Not set' : Formatters.date(_dueDate),
                    style: _dueDate == null
                        ? theme.textTheme.bodyLarge?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          )
                        : theme.textTheme.bodyLarge,
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _notes,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: 'Notes',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: AppSpacing.xxl),
              FilledButton.icon(
                onPressed: _submitting || bookingBlocks ? null : _submit,
                icon: _submitting
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.check_rounded),
                label: Text(_submitting ? 'Creating…' : 'Create request'),
              ),
              const SizedBox(height: AppSpacing.lg),
            ],
          ),
        ),
      ),
    );
  }
}

/// Searchable booking picker. Returns the chosen [Booking] via `Navigator.pop`.
class _BookingPickerSheet extends ConsumerWidget {
  const _BookingPickerSheet();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(bookingPickerResultsProvider);

    return SafeArea(
      child: SizedBox(
        // Leaves the drag handle and the keyboard room on a 320dp phone.
        height: MediaQuery.sizeOf(context).height * 0.7,
        child: Column(
          children: [
            AppSearchField(
              hintText: 'Search booking code, customer…',
              onChanged: (term) =>
                  ref.read(bookingPickerSearchProvider.notifier).state = term,
            ),
            Expanded(
              child: AsyncValueView<List<Booking>>(
                value: async,
                onRetry: () => ref.invalidate(bookingPickerResultsProvider),
                isEmpty: (items) => items.isEmpty,
                empty: const EmptyState(
                  icon: Icons.hotel_outlined,
                  title: 'No bookings found',
                  message: 'Try a different search term.',
                ),
                data: (items) => ListView.separated(
                  padding: const EdgeInsets.only(bottom: AppSpacing.lg),
                  itemCount: items.length,
                  separatorBuilder: (_, _) => const Divider(height: 1),
                  itemBuilder: (context, index) {
                    final booking = items[index];
                    return ListTile(
                      minTileHeight: 48,
                      title: Text(booking.bookingCode ?? 'Booking'),
                      subtitle: Text(
                        booking.customerName ?? 'Unknown customer',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      trailing: StatusChip(
                        tone: booking.statusTone,
                        label: booking.displayStatus,
                        dense: true,
                      ),
                      onTap: () => Navigator.of(context).pop(booking),
                    );
                  },
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
