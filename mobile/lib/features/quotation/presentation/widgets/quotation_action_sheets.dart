/// Bottom sheets for the quotation write actions that mobile was missing: Send (UC-14.4),
/// Convert to booking (UC-14.7) and Close (UC-14.8).
///
/// Each collects input and returns the payload; the caller performs the request so that
/// the room gate's `ROOM_*` conflicts are handled alongside the screen's other errors.
library;

import 'package:flutter/material.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../data/quotation_models.dart';

const _emailPattern = r'^[\w.\-+]+@([\w\-]+\.)+[\w\-]{2,}$';

// ─────────────────────────────────────────────────────────────────────────────
// UC-14.4 — Send to customer
// ─────────────────────────────────────────────────────────────────────────────

Future<SendQuotationPayload?> showSendQuotationSheet(
  BuildContext context,
  Quotation quotation,
) {
  return showModalBottomSheet<SendQuotationPayload>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _SendSheet(quotation: quotation),
  );
}

class _SendSheet extends StatefulWidget {
  const _SendSheet({required this.quotation});

  final Quotation quotation;

  @override
  State<_SendSheet> createState() => _SendSheetState();
}

class _SendSheetState extends State<_SendSheet> {
  final _formKey = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.quotation.contactName ?? '');
  late final _email = TextEditingController(text: widget.quotation.email ?? '');
  late final _phone = TextEditingController(text: widget.quotation.phone ?? '');
  late final _message = TextEditingController(
    text: 'Dear ${widget.quotation.contactName ?? "customer"},\n\n'
        'Please find our room quotation attached. We look forward to welcoming you.\n\n'
        'Kind regards,\nLeadora Hotels Sales Team',
  );

  /// Wire values the backend accepts on `sendMethod`.
  String _method = 'EMAIL';

  @override
  void dispose() {
    for (final c in [_name, _email, _phone, _message]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: EdgeInsets.only(
        left: AppSpacing.lg,
        right: AppSpacing.lg,
        bottom: MediaQuery.viewInsetsOf(context).bottom + AppSpacing.lg,
      ),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Send quotation',
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: AppSpacing.lg),
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'EMAIL', label: Text('Email'), icon: Icon(Icons.mail_outline)),
                  ButtonSegment(
                    value: 'WHATSAPP',
                    label: Text('WhatsApp'),
                    icon: Icon(Icons.chat_bubble_outline),
                  ),
                  ButtonSegment(value: 'PDF', label: Text('PDF'), icon: Icon(Icons.picture_as_pdf_outlined)),
                ],
                selected: {_method},
                onSelectionChanged: (v) => setState(() => _method = v.first),
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _name,
                decoration: const InputDecoration(
                  labelText: 'Recipient name *',
                  prefixIcon: Icon(Icons.person_outline_rounded),
                ),
                validator: (v) =>
                    (v?.trim().isEmpty ?? true) ? 'Recipient name is required' : null,
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                decoration: InputDecoration(
                  labelText: _method == 'EMAIL' ? 'Recipient email *' : 'Recipient email',
                  prefixIcon: const Icon(Icons.alternate_email_rounded),
                ),
                // The backend rejects EMAIL with no address (INVALID_RECIPIENT_CONTACT).
                validator: (v) {
                  final email = v?.trim() ?? '';
                  if (_method != 'EMAIL') return null;
                  if (email.isEmpty) return 'Email is required to send by email';
                  return RegExp(_emailPattern).hasMatch(email) ? null : 'Enter a valid email';
                },
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _phone,
                keyboardType: TextInputType.phone,
                decoration: InputDecoration(
                  labelText: _method == 'WHATSAPP' ? 'Phone *' : 'Phone',
                  prefixIcon: const Icon(Icons.phone_outlined),
                ),
                validator: (v) => _method == 'WHATSAPP' && (v?.trim().isEmpty ?? true)
                    ? 'Phone is required for WhatsApp'
                    : null,
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _message,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: 'Message',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: AppSpacing.xl),
              FilledButton.icon(
                onPressed: () {
                  if (!_formKey.currentState!.validate()) return;
                  Navigator.of(context).pop(
                    SendQuotationPayload(
                      sendMethod: _method,
                      recipientName: _name.text,
                      recipientEmail: _email.text,
                      recipientPhone: _phone.text,
                      personalMessage: _message.text,
                    ),
                  );
                },
                icon: const Icon(Icons.send_rounded),
                label: const Text('Send to customer'),
              ),
              const SizedBox(height: AppSpacing.sm),
            ],
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// UC-14.7 — Convert to booking
// ─────────────────────────────────────────────────────────────────────────────

Future<ConvertToBookingPayload?> showConvertToBookingSheet(
  BuildContext context,
  Quotation quotation,
) {
  return showModalBottomSheet<ConvertToBookingPayload>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _ConvertSheet(quotation: quotation),
  );
}

class _ConvertSheet extends StatefulWidget {
  const _ConvertSheet({required this.quotation});

  final Quotation quotation;

  @override
  State<_ConvertSheet> createState() => _ConvertSheetState();
}

class _ConvertSheetState extends State<_ConvertSheet> {
  final _formKey = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.quotation.contactName ?? '');
  late final _email = TextEditingController(text: widget.quotation.email ?? '');
  late final _phone = TextEditingController(text: widget.quotation.phone ?? '');
  final _special = TextEditingController();

  @override
  void dispose() {
    for (final c in [_name, _email, _phone, _special]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final q = widget.quotation;

    return Padding(
      padding: EdgeInsets.only(
        left: AppSpacing.lg,
        right: AppSpacing.lg,
        bottom: MediaQuery.viewInsetsOf(context).bottom + AppSpacing.lg,
      ),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Convert to booking',
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(
                // Dates and room type are taken from the quotation: the room confirmation
                // was given for exactly these, and changing them here would invalidate it.
                '${q.roomType ?? "—"} · ${Formatters.date(q.checkInDate)} → '
                '${Formatters.date(q.checkOutDate)}',
                style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _name,
                decoration: const InputDecoration(
                  labelText: 'Contact name *',
                  prefixIcon: Icon(Icons.person_outline_rounded),
                ),
                validator: (v) =>
                    (v?.trim().isEmpty ?? true) ? 'Contact name is required' : null,
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(
                  labelText: 'Email *',
                  prefixIcon: Icon(Icons.alternate_email_rounded),
                ),
                validator: (v) {
                  final email = v?.trim() ?? '';
                  if (email.isEmpty) return 'Email is required (BR-23)';
                  return RegExp(_emailPattern).hasMatch(email) ? null : 'Enter a valid email';
                },
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _phone,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(
                  labelText: 'Phone *',
                  prefixIcon: Icon(Icons.phone_outlined),
                ),
                validator: (v) =>
                    (v?.trim().isEmpty ?? true) ? 'Phone is required (BR-23)' : null,
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _special,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: 'Special requests (optional)',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: AppSpacing.xl),
              FilledButton.icon(
                onPressed: () {
                  if (!_formKey.currentState!.validate()) return;
                  if (q.checkInDate == null || q.checkOutDate == null) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('This quotation has no stay dates to book.'),
                      ),
                    );
                    return;
                  }
                  Navigator.of(context).pop(
                    ConvertToBookingPayload(
                      contactName: _name.text,
                      email: _email.text,
                      phone: _phone.text,
                      roomType: q.roomType ?? '',
                      checkInDate: q.checkInDate!,
                      checkOutDate: q.checkOutDate!,
                      specialRequests: _special.text,
                    ),
                  );
                },
                icon: const Icon(Icons.event_available_outlined),
                label: const Text('Create booking'),
              ),
              const SizedBox(height: AppSpacing.sm),
            ],
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// UC-14.8 — Close
// ─────────────────────────────────────────────────────────────────────────────

Future<CloseQuotationPayload?> showCloseQuotationSheet(BuildContext context) {
  return showModalBottomSheet<CloseQuotationPayload>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => const _CloseSheet(),
  );
}

class _CloseSheet extends StatefulWidget {
  const _CloseSheet();

  @override
  State<_CloseSheet> createState() => _CloseSheetState();
}

class _CloseSheetState extends State<_CloseSheet> {
  final _reason = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: EdgeInsets.only(
        left: AppSpacing.lg,
        right: AppSpacing.lg,
        bottom: MediaQuery.viewInsetsOf(context).bottom + AppSpacing.lg,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Close quotation',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Closing is final — the quotation can no longer be sent or converted.',
              style: theme.textTheme.bodySmall,
            ),
            const SizedBox(height: AppSpacing.lg),
            TextField(
              controller: _reason,
              maxLines: 3,
              onChanged: (_) {
                if (_error != null) setState(() => _error = null);
              },
              decoration: InputDecoration(
                labelText: 'Reason *',
                hintText: 'e.g. Customer booked elsewhere',
                errorText: _error,
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: AppSpacing.xl),
            FilledButton.icon(
              onPressed: () {
                // The backend records this as the closure audit trail, so it is required.
                if (_reason.text.trim().isEmpty) {
                  setState(() => _error = 'A reason is required to close a quotation');
                  return;
                }
                Navigator.of(context).pop(CloseQuotationPayload(reason: _reason.text));
              },
              icon: const Icon(Icons.archive_outlined),
              label: const Text('Close quotation'),
            ),
            const SizedBox(height: AppSpacing.sm),
          ],
        ),
      ),
    );
  }
}
