/// Bottom sheets for the quotation write actions that mobile was missing: Send (UC-14.4),
/// Convert to booking (UC-14.7), Close (UC-14.8), Processing Quotations (UC-14.3) and
/// Generate Reports (UC-14.2).
///
/// Each collects input and returns the payload; the caller performs the request so that
/// any error response is handled alongside the screen's other errors.
library;

import 'package:flutter/material.dart';

import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../data/quotation_models.dart';

/// Same catalogue the web offers on the create/revise form and the discount-report
/// filter (`ROOM_TYPE_OPTIONS` on web) — free text on the backend, so this is a
/// convenience list, not an enum.
const kQuotationRoomTypes = <String>[
  'Deluxe Suite',
  'Superior Room',
  'Standard Queen',
  'Executive Suite',
  'Ocean View Room',
  'Banquet Hall',
  'Grand Ballroom Suite',
];

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

// ─────────────────────────────────────────────────────────────────────────────
// UC-14.3 — Processing Quotations (approve / reject / request changes)
// ─────────────────────────────────────────────────────────────────────────────

Future<ProcessApprovalPayload?> showProcessApprovalSheet(
  BuildContext context,
  Quotation quotation,
) {
  return showModalBottomSheet<ProcessApprovalPayload>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _ProcessApprovalSheet(quotation: quotation),
  );
}

class _ProcessApprovalSheet extends StatefulWidget {
  const _ProcessApprovalSheet({required this.quotation});

  final Quotation quotation;

  @override
  State<_ProcessApprovalSheet> createState() => _ProcessApprovalSheetState();
}

class _ProcessApprovalSheetState extends State<_ProcessApprovalSheet> {
  final _notes = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  /// E4 (web `ApprovalModal`) — approving with missing room/date data is disabled;
  /// Reject / Request changes stay available either way.
  bool get _isDataMissing =>
      widget.quotation.roomType == null ||
      widget.quotation.checkInDate == null ||
      widget.quotation.checkOutDate == null;

  void _decide(ApprovalDecision decision) {
    final needsNotes = decision != ApprovalDecision.approve;
    if (needsNotes && _notes.text.trim().isEmpty) {
      setState(() => _error = 'Add a note explaining your decision.');
      return;
    }
    Navigator.of(context).pop(
      ProcessApprovalPayload(
        action: decision,
        notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
      ),
    );
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
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Review ${q.quoteNo}',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              '${q.contactName ?? "No contact"} · ${q.discountPercent ?? 0}% discount · '
              '${Formatters.money(q.totalAmount)}',
              style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
            ),
            if (_isDataMissing) ...[
              const SizedBox(height: AppSpacing.lg),
              Container(
                padding: const EdgeInsets.all(AppSpacing.md),
                decoration: BoxDecoration(
                  color: scheme.tertiaryContainer,
                  borderRadius: BorderRadius.circular(AppRadii.sm),
                ),
                child: Row(
                  children: [
                    Icon(Icons.warning_amber_rounded, color: scheme.onTertiaryContainer, size: 18),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: Text(
                        'Room type or stay dates are missing (E4) — Approve is disabled '
                        'until the quotation is complete. Reject or Request changes instead.',
                        style: theme.textTheme.bodySmall?.copyWith(color: scheme.onTertiaryContainer),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: AppSpacing.lg),
            TextField(
              controller: _notes,
              maxLines: 3,
              onChanged: (_) {
                if (_error != null) setState(() => _error = null);
              },
              decoration: InputDecoration(
                labelText: 'Notes',
                hintText: 'Required for Reject or Request changes',
                errorText: _error,
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            FilledButton.icon(
              onPressed: _isDataMissing ? null : () => _decide(ApprovalDecision.approve),
              icon: const Icon(Icons.check_circle_outline_rounded),
              label: const Text('Approve'),
            ),
            const SizedBox(height: AppSpacing.sm),
            OutlinedButton.icon(
              onPressed: () => _decide(ApprovalDecision.requestChanges),
              icon: const Icon(Icons.replay_rounded),
              label: const Text('Request changes'),
            ),
            const SizedBox(height: AppSpacing.sm),
            OutlinedButton.icon(
              onPressed: () => _decide(ApprovalDecision.reject),
              style: OutlinedButton.styleFrom(
                foregroundColor: scheme.error,
                side: BorderSide(color: scheme.error),
              ),
              icon: const Icon(Icons.cancel_outlined),
              label: const Text('Reject'),
            ),
            const SizedBox(height: AppSpacing.sm),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// UC-14.2 Generate Reports — discount report audit log
// ─────────────────────────────────────────────────────────────────────────────

/// Collects the filters, computes the result set client-side (same rule as web's
/// `DiscountReportTab`: discount strictly greater than the threshold, matched against
/// [Quotation.validUntil] for the date range and [Quotation.roomType]), then hands the
/// built payload to [onGenerate] to persist the audit log. The sheet never touches the
/// network itself — same convention as the other sheets in this file.
Future<void> showDiscountReportSheet(
  BuildContext context, {
  required List<Quotation> quotations,
  required String generatedByName,
  String? generatedByRole,
  required Future<ReportLog> Function(SaveReportLogPayload payload) onGenerate,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _DiscountReportSheet(
      quotations: quotations,
      generatedByName: generatedByName,
      generatedByRole: generatedByRole,
      onGenerate: onGenerate,
    ),
  );
}

class _DiscountReportSheet extends StatefulWidget {
  const _DiscountReportSheet({
    required this.quotations,
    required this.generatedByName,
    required this.generatedByRole,
    required this.onGenerate,
  });

  final List<Quotation> quotations;
  final String generatedByName;
  final String? generatedByRole;
  final Future<ReportLog> Function(SaveReportLogPayload payload) onGenerate;

  @override
  State<_DiscountReportSheet> createState() => _DiscountReportSheetState();
}

class _DiscountReportSheetState extends State<_DiscountReportSheet> {
  final _threshold = TextEditingController(text: '10');
  DateTime? _dateFrom;
  DateTime? _dateTo;
  String? _roomType;

  bool _saving = false;
  int? _resultCount;
  String? _confirmation;
  String? _error;

  @override
  void dispose() {
    _threshold.dispose();
    super.dispose();
  }

  Future<void> _pickDate({required bool isFrom}) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: (isFrom ? _dateFrom : _dateTo) ?? DateTime.now(),
      firstDate: DateTime(DateTime.now().year - 3),
      lastDate: DateTime(DateTime.now().year + 3),
    );
    if (picked == null) return;
    setState(() => isFrom ? _dateFrom = picked : _dateTo = picked);
  }

  List<Quotation> _computeResults(num threshold) {
    return widget.quotations.where((q) {
      final discount = q.discountPercent ?? 0;
      if (discount <= threshold) return false;
      if (_roomType != null && q.roomType != _roomType) return false;
      final issued = q.validUntil;
      if (_dateFrom != null && (issued == null || issued.isBefore(_dateFrom!))) return false;
      if (_dateTo != null && (issued == null || issued.isAfter(_dateTo!))) return false;
      return true;
    }).toList();
  }

  Future<void> _generate() async {
    final threshold = num.tryParse(_threshold.text.trim()) ?? 0;
    final results = _computeResults(threshold);

    setState(() {
      _saving = true;
      _resultCount = results.length;
      _confirmation = null;
      _error = null;
    });

    try {
      final dateRange = _dateFrom != null || _dateTo != null
          ? ', date range applied'
          : '';
      final log = await widget.onGenerate(
        SaveReportLogPayload(
          generatedByName: widget.generatedByName,
          generatedByRole: widget.generatedByRole,
          filterDateFrom: _dateFrom,
          filterDateTo: _dateTo,
          filterRoomType: _roomType,
          filterDiscountThreshold: threshold,
          resultCount: results.length,
          action: 'GENERATE_DISCOUNT_REPORT',
          result: results.isNotEmpty ? 'SUCCESS' : 'NO_DATA',
          reason: 'Discount threshold: $threshold%$dateRange',
        ),
      );
      if (!mounted) return;
      final logIdShort = log.logId.length >= 8
          ? log.logId.substring(0, 8).toUpperCase()
          : log.logId;
      setState(() {
        _confirmation =
            '${results.length} quote(s) found above $threshold% discount. '
            'Log #$logIdShort saved.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = 'Report generated — ${results.length} quote(s) found. '
            '(Audit log could not be saved.)';
      });
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

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
              'Generate discount report',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Filters quotations already loaded on this device and records an audit '
              'log of the report (BR-37). Sales staff above the threshold notify a '
              'manager automatically.',
              style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
            ),
            const SizedBox(height: AppSpacing.lg),
            Row(
              children: [
                Expanded(
                  child: _DateChip(
                    label: 'From',
                    value: _dateFrom,
                    onTap: () => _pickDate(isFrom: true),
                    onClear: _dateFrom == null ? null : () => setState(() => _dateFrom = null),
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: _DateChip(
                    label: 'To',
                    value: _dateTo,
                    onTap: () => _pickDate(isFrom: false),
                    onClear: _dateTo == null ? null : () => setState(() => _dateTo = null),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            DropdownButtonFormField<String?>(
              initialValue: _roomType,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'Room type',
                prefixIcon: Icon(Icons.bed_outlined),
              ),
              items: [
                const DropdownMenuItem<String?>(value: null, child: Text('All room types')),
                for (final rt in kQuotationRoomTypes)
                  DropdownMenuItem<String?>(value: rt, child: Text(rt)),
              ],
              onChanged: (v) => setState(() => _roomType = v),
            ),
            const SizedBox(height: AppSpacing.lg),
            TextField(
              controller: _threshold,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(
                labelText: 'Min discount threshold (%)',
                prefixIcon: Icon(Icons.percent_rounded),
                helperText: 'Only quotations with a discount above this are included',
                helperMaxLines: 2,
              ),
            ),
            const SizedBox(height: AppSpacing.xl),
            FilledButton.icon(
              onPressed: _saving ? null : _generate,
              icon: _saving
                  ? const SizedBox(
                      width: AppIconSize.md,
                      height: AppIconSize.md,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.summarize_outlined),
              label: Text(_saving ? 'Generating…' : 'Generate report'),
            ),
            if (_resultCount != null && !_saving) ...[
              const SizedBox(height: AppSpacing.md),
              Text(
                _confirmation ?? _error ?? '',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: _error != null ? scheme.error : scheme.primary,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
            const SizedBox(height: AppSpacing.sm),
          ],
        ),
      ),
    );
  }
}

class _DateChip extends StatelessWidget {
  const _DateChip({
    required this.label,
    required this.value,
    required this.onTap,
    this.onClear,
  });

  final String label;
  final DateTime? value;
  final VoidCallback onTap;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadii.sm),
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: const Icon(Icons.calendar_today_rounded, size: 18),
          suffixIcon: onClear == null
              ? null
              : IconButton(
                  icon: const Icon(Icons.close_rounded, size: 16),
                  onPressed: onClear,
                ),
          isDense: true,
        ),
        child: Text(value == null ? 'Any' : Formatters.date(value)),
      ),
    );
  }
}
