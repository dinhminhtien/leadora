import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../deal/data/deal_models.dart';
import '../../../deal/presentation/providers/deal_providers.dart';
import '../../data/quotation_models.dart';
import '../providers/quotation_providers.dart';

/// Which operation the form performs. The fields are the same either way, so one screen
/// serves both — the same shape as [CustomerFormMode] elsewhere in the app.
enum QuotationFormMode {
  /// UC-14.1 — a new quotation against a deal. Lands as DRAFT.
  create,

  /// UC-14.5 — a new version of an existing quotation. Needs a change reason.
  revise,
}

/// Fetches the quotation a revision is based on, then hands it to [QuotationFormScreen].
///
/// Split out so the form itself stays synchronous: pre-filling every field from an
/// `AsyncValue` inside `initState` is what makes these forms lose edits on rebuild.
class ReviseQuotationLoader extends ConsumerWidget {
  const ReviseQuotationLoader({super.key, required this.quotationId});

  final String quotationId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(quotationDetailProvider(quotationId));
    return Scaffold(
      appBar: AppBar(title: const Text('Revise quotation')),
      body: AsyncValueView<Quotation>(
        value: async,
        onRetry: () => ref.invalidate(quotationDetailProvider(quotationId)),
        loading: const DetailSkeleton(),
        // The AppBar comes from the form in this mode, so it is dropped here.
        data: (quotation) => QuotationFormScreen(
          mode: QuotationFormMode.revise,
          quotation: quotation,
          embedded: true,
        ),
      ),
    );
  }
}

/// Create or revise a quotation.
///
/// Kept option-for-option with the web form so a rep sees the same room types, payment
/// policies and pricing maths on either client. A created quotation always lands as DRAFT:
/// Submit (UC-14.2) is a separate, explicit step, and it is Submit that decides APPROVED vs
/// PENDING_APPROVAL from the discount. A revision re-enters the same approval routing.
class QuotationFormScreen extends ConsumerStatefulWidget {
  const QuotationFormScreen({
    super.key,
    required this.mode,
    this.initialDealId,
    this.quotation,
    this.embedded = false,
  }) : assert(
         mode == QuotationFormMode.create || quotation != null,
         'Revise needs the quotation being revised',
       );

  final QuotationFormMode mode;

  /// Pre-selects a deal when opened from that deal's workspace. Create only.
  final String? initialDealId;

  /// The quotation being revised. Required in [QuotationFormMode.revise].
  final Quotation? quotation;

  /// True when a parent already supplies the `Scaffold`/`AppBar` (the revise loader does).
  final bool embedded;

  @override
  ConsumerState<QuotationFormScreen> createState() => _QuotationFormScreenState();
}

/// Same catalogue the web form offers. Free text on the backend
/// (`QuotationEntity.roomType`), so this is a convenience list, not an enum.
const _roomTypes = <String>[
  'Deluxe Suite',
  'Superior Room',
  'Standard Queen',
  'Executive Suite',
  'Ocean View Room',
  'Banquet Hall',
  'Grand Ballroom Suite',
];

/// Wire value → label, matching the web `PAYMENT_POLICIES` list exactly.
const _paymentPolicies = <String, String>{
  'full_upfront': 'Full Payment Upfront',
  '50_deposit': '50% Deposit on Booking',
  'pay_on_arrival': 'Pay on Arrival',
};

/// Discount above this needs a manager, per the backend's
/// `app.quotation.discount-threshold` default. Shown as a warning only — the backend
/// remains the authority on where Submit routes.
const _discountApprovalThreshold = 10;

class _QuotationFormScreenState extends ConsumerState<QuotationFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _rooms;
  late final TextEditingController _pricePerNight;
  late final TextEditingController _discount;
  late final TextEditingController _notes;
  final _changeReason = TextEditingController();

  String? _dealId;
  String? _roomType;
  String _paymentPolicy = 'full_upfront';
  DateTime? _checkIn;
  DateTime? _checkOut;
  DateTime? _validUntil;

  bool _submitting = false;
  bool _autovalidate = false;

  bool get _isRevise => widget.mode == QuotationFormMode.revise;

  @override
  void initState() {
    super.initState();
    final q = widget.quotation;

    if (_isRevise && q != null) {
      // Start from what the customer was last quoted, so the rep changes only what moved.
      _rooms = TextEditingController(text: '${q.numberOfRooms ?? 1}');
      _pricePerNight = TextEditingController(
        text: q.pricePerNight == null ? '' : _plain(q.pricePerNight!),
      );
      _discount = TextEditingController(
        text: _plain(q.discountPercent ?? 0),
      );
      _notes = TextEditingController();
      _dealId = q.dealId;
      // The room type may be free text the catalogue does not list — only preselect a
      // dropdown value that exists, otherwise the field would show blank but validate.
      _roomType = _roomTypes.contains(q.roomType) ? q.roomType : null;
      _checkIn = q.checkInDate;
      _checkOut = q.checkOutDate;
      _validUntil = q.validUntil;
    } else {
      _rooms = TextEditingController(text: '1');
      _pricePerNight = TextEditingController();
      _discount = TextEditingController(text: '0');
      _notes = TextEditingController();
      _dealId = widget.initialDealId;
    }

    // Fill any gap the source quotation left, and give create a sensible starting window
    // so the common case is two taps, not six.
    final tomorrow = DateTime.now().add(const Duration(days: 1));
    _checkIn ??= DateTime(tomorrow.year, tomorrow.month, tomorrow.day);
    if (_checkOut == null || !_checkOut!.isAfter(_checkIn!)) {
      _checkOut = _checkIn!.add(const Duration(days: 1));
    }
    _validUntil ??= _checkIn!.subtract(const Duration(days: 1));
  }

  /// Trims a trailing `.0` so a whole number does not land in the field as "40.0".
  static String _plain(num v) =>
      v == v.roundToDouble() ? v.toInt().toString() : v.toString();

  @override
  void dispose() {
    for (final c in [_rooms, _pricePerNight, _discount, _notes, _changeReason]) {
      c.dispose();
    }
    super.dispose();
  }

  int get _nights {
    if (_checkIn == null || _checkOut == null) return 0;
    final diff = _checkOut!.difference(_checkIn!).inDays;
    return diff > 0 ? diff : 0;
  }

  int get _roomCount => int.tryParse(_rooms.text.trim()) ?? 0;
  double get _price => double.tryParse(_pricePerNight.text.trim()) ?? 0;
  double get _discountPct => double.tryParse(_discount.text.trim()) ?? 0;

  double get _subtotal => _price * _nights * _roomCount;
  double get _discountAmount => _subtotal * _discountPct / 100;
  double get _total => _subtotal - _discountAmount;

  bool get _needsApproval => _discountPct > _discountApprovalThreshold;

  Future<void> _pickDate({
    required DateTime? current,
    required DateTime first,
    required ValueChanged<DateTime> onPicked,
  }) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: current ?? first,
      firstDate: first,
      lastDate: DateTime(DateTime.now().year + 3),
    );
    if (picked != null) setState(() => onPicked(picked));
  }

  String? _validateRooms(String? v) {
    final parsed = int.tryParse(v?.trim() ?? '');
    if (parsed == null) return 'Enter a whole number';
    if (parsed < 1) return 'At least 1 room';
    return null;
  }

  String? _validatePrice(String? v) {
    final parsed = double.tryParse(v?.trim() ?? '');
    if (parsed == null) return 'Enter a number';
    if (parsed <= 0) return 'Must be greater than 0';
    return null;
  }

  String? _validateDiscount(String? v) {
    final parsed = double.tryParse(v?.trim() ?? '');
    if (parsed == null) return 'Enter a number';
    if (parsed < 0) return 'Cannot be negative';
    if (parsed > 100) return 'Cannot exceed 100%';
    return null;
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();

    // Dropdown and date state live outside the Form, so they are checked by hand. A
    // revision inherits its deal, so only create requires one to be chosen.
    final formOk = _formKey.currentState!.validate();
    final missing =
        (!_isRevise && _dealId == null) || _roomType == null || _validUntil == null;
    if (!formOk || missing || _nights <= 0) {
      setState(() => _autovalidate = true);
      if (missing || _nights <= 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _nights <= 0
                  ? 'Check-out must be at least one night after check-in.'
                  : _isRevise
                  ? 'Select a room type and a validity date.'
                  : 'Select a deal, a room type and a validity date.',
            ),
          ),
        );
      }
      return;
    }

    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    final actions = ref.read(quotationActionsProvider);

    try {
      final Quotation quotation;
      final String message;

      if (_isRevise) {
        quotation = await actions.revise(
          widget.quotation!.id,
          ReviseQuotationPayload(
            roomType: _roomType!,
            checkInDate: _checkIn!,
            checkOutDate: _checkOut!,
            numberOfRooms: _roomCount,
            pricePerNight: _price,
            discountPercent: _discountPct,
            paymentPolicy: _paymentPolicy,
            validUntil: _validUntil!,
            changeReason: _changeReason.text,
            notes: _notes.text,
          ),
        );
        message = 'Revision saved — version ${quotation.version ?? ""}'.trim();
      } else {
        quotation = await actions.create(
          CreateQuotationPayload(
            dealId: _dealId!,
            roomType: _roomType!,
            checkInDate: _checkIn!,
            checkOutDate: _checkOut!,
            numberOfRooms: _roomCount,
            pricePerNight: _price,
            discountPercent: _discountPct,
            paymentPolicy: _paymentPolicy,
            validUntil: _validUntil!,
            notes: _notes.text,
          ),
        );
        message = 'Quotation ${quotation.quoteNo} created as draft';
      }

      messenger.showSnackBar(SnackBar(content: Text(message)));
      router.pop();
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    final form = Form(
      key: _formKey,
      autovalidateMode: _autovalidate
          ? AutovalidateMode.onUserInteraction
          : AutovalidateMode.disabled,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          if (_isRevise) _reviseHeader(theme) else _dealPicker(theme, scheme),
          const SizedBox(height: AppSpacing.md),
          ..._stayAndPricing(theme, scheme),
        ],
      ),
    );

    if (widget.embedded) return SafeArea(child: form);
    return Scaffold(
      appBar: AppBar(title: const Text('New quotation')),
      body: SafeArea(child: form),
    );
  }

  /// What is being revised, and why. Read-only apart from the reason, which the backend
  /// requires as the audit trail for the version bump.
  Widget _reviseHeader(ThemeData theme) {
    final q = widget.quotation!;
    return SectionCard(
      title: 'Revising ${q.quoteNo}',
      icon: Icons.history_rounded,
      trailing: StatusChip(tone: q.status.tone, rawStatus: q.status.wire, dense: true),
      child: Column(
        children: [
          InfoRow(label: 'Current version', value: 'v${q.version ?? 1}'),
          InfoRow(label: 'Current total', value: Formatters.money(q.totalAmount)),
          const SizedBox(height: AppSpacing.lg),
          TextFormField(
            controller: _changeReason,
            maxLines: 2,
            decoration: const InputDecoration(
              labelText: 'Reason for change *',
              hintText: 'e.g. customer asked for a lower rate',
              alignLabelWithHint: true,
            ),
            validator: (v) => (v?.trim().isEmpty ?? true)
                ? 'The backend records why a quotation was revised'
                : null,
          ),
        ],
      ),
    );
  }

  Widget _dealPicker(ThemeData theme, ColorScheme scheme) {
    final dealsAsync = ref.watch(dealListProvider);
    return SectionCard(
                title: 'Deal',
                icon: Icons.work_outline_rounded,
                child: dealsAsync.when(
                  loading: () => const Padding(
                    padding: EdgeInsets.symmetric(vertical: AppSpacing.sm),
                    child: LinearProgressIndicator(),
                  ),
                  error: (e, _) => Text(
                    e is AppException ? e.message : 'Could not load deals.',
                    style: theme.textTheme.bodySmall?.copyWith(color: scheme.error),
                  ),
                  data: (deals) => deals.isEmpty
                      ? Text(
                          'No deals available. Create a deal first — a quotation always '
                          'belongs to one.',
                          style: theme.textTheme.bodySmall,
                        )
                      : DropdownButtonFormField<String>(
                          initialValue: deals.any((d) => d.id == _dealId) ? _dealId : null,
                          isExpanded: true,
                          decoration: const InputDecoration(
                            labelText: 'Deal *',
                            prefixIcon: Icon(Icons.work_outline_rounded),
                          ),
                          items: [
                            for (final Deal d in deals)
                              DropdownMenuItem(
                                value: d.id,
                                child: Text(d.title, overflow: TextOverflow.ellipsis),
                              ),
                          ],
                          onChanged: (v) => setState(() => _dealId = v),
                          validator: (v) => v == null ? 'Select a deal' : null,
                        ),
                ),
    );
  }

  /// Everything below the header: the stay, the pricing, the live total and the save
  /// button. Identical in both modes, so it is built once.
  List<Widget> _stayAndPricing(ThemeData theme, ColorScheme scheme) {
    return [
              SectionCard(
                title: 'Stay',
                icon: Icons.hotel_outlined,
                child: Column(
                  children: [
                    DropdownButtonFormField<String>(
                      initialValue: _roomType,
                      isExpanded: true,
                      decoration: const InputDecoration(
                        labelText: 'Room type *',
                        prefixIcon: Icon(Icons.bed_outlined),
                      ),
                      items: [
                        for (final rt in _roomTypes)
                          DropdownMenuItem(value: rt, child: Text(rt)),
                      ],
                      onChanged: (v) => setState(() => _roomType = v),
                      validator: (v) => v == null ? 'Select a room type' : null,
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    _DateField(
                      label: 'Check-in *',
                      value: _checkIn,
                      onTap: () => _pickDate(
                        current: _checkIn,
                        first: DateTime.now(),
                        onPicked: (d) {
                          _checkIn = d;
                          // Keep the window valid: a check-out on or before check-in is
                          // rejected by the backend.
                          if (_checkOut == null || !_checkOut!.isAfter(d)) {
                            _checkOut = d.add(const Duration(days: 1));
                          }
                        },
                      ),
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    _DateField(
                      label: 'Check-out *',
                      value: _checkOut,
                      onTap: () => _pickDate(
                        current: _checkOut,
                        first: (_checkIn ?? DateTime.now()).add(const Duration(days: 1)),
                        onPicked: (d) => _checkOut = d,
                      ),
                      helper: _nights > 0 ? '$_nights night${_nights == 1 ? "" : "s"}' : null,
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    TextFormField(
                      controller: _rooms,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Number of rooms *',
                        prefixIcon: Icon(Icons.meeting_room_outlined),
                      ),
                      validator: _validateRooms,
                      onChanged: (_) => setState(() {}),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.md),

              SectionCard(
                title: 'Pricing',
                icon: Icons.payments_outlined,
                child: Column(
                  children: [
                    TextFormField(
                      controller: _pricePerNight,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Price per night (VND) *',
                        prefixIcon: Icon(Icons.attach_money_rounded),
                      ),
                      validator: _validatePrice,
                      onChanged: (_) => setState(() {}),
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    TextFormField(
                      controller: _discount,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      textInputAction: TextInputAction.next,
                      decoration: InputDecoration(
                        labelText: 'Discount (%) *',
                        prefixIcon: const Icon(Icons.percent_rounded),
                        helperText: _needsApproval
                            ? 'Above $_discountApprovalThreshold% — Submit will route this '
                                  'to a manager for approval'
                            : null,
                        helperMaxLines: 2,
                        helperStyle: _needsApproval
                            ? TextStyle(color: scheme.tertiary, fontWeight: FontWeight.w600)
                            : null,
                      ),
                      validator: _validateDiscount,
                      onChanged: (_) => setState(() {}),
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    DropdownButtonFormField<String>(
                      initialValue: _paymentPolicy,
                      isExpanded: true,
                      decoration: const InputDecoration(
                        labelText: 'Payment policy *',
                        prefixIcon: Icon(Icons.policy_outlined),
                      ),
                      items: [
                        for (final entry in _paymentPolicies.entries)
                          DropdownMenuItem(value: entry.key, child: Text(entry.value)),
                      ],
                      onChanged: (v) =>
                          setState(() => _paymentPolicy = v ?? _paymentPolicy),
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    _DateField(
                      label: 'Valid until *',
                      value: _validUntil,
                      onTap: () => _pickDate(
                        current: _validUntil,
                        first: DateTime.now(),
                        onPicked: (d) => _validUntil = d,
                      ),
                      helper: 'After this date the scheduler auto-expires the quotation',
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.md),

              // Live total, so the rep sees what the customer will see before saving.
              SectionCard(
                title: 'Total',
                icon: Icons.calculate_outlined,
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Subtotal',
                      value: '${Formatters.money(_subtotal)}'
                          '${_nights > 0 ? "  ($_roomCount x $_nights night${_nights == 1 ? "" : "s"})" : ""}',
                    ),
                    InfoRow(
                      label: 'Discount',
                      value: _discountPct > 0
                          ? '- ${Formatters.money(_discountAmount)}  (${_discountPct.toStringAsFixed(0)}%)'
                          : '—',
                    ),
                    const Divider(height: AppSpacing.xl),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            'Total',
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                        Text(
                          Formatters.money(_total),
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.md),

              SectionCard(
                title: 'Notes',
                icon: Icons.sticky_note_2_outlined,
                child: TextFormField(
                  controller: _notes,
                  maxLines: 3,
                  decoration: const InputDecoration(
                    labelText: 'Internal notes (optional)',
                    alignLabelWithHint: true,
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.xl),

              FilledButton.icon(
                onPressed: _submitting ? null : _submit,
                icon: _submitting
                    ? const SizedBox(
                        width: AppIconSize.md,
                        height: AppIconSize.md,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Icon(_isRevise ? Icons.history_rounded : Icons.save_outlined),
                label: Text(
                  _submitting
                      ? 'Saving…'
                      : _isRevise
                      ? 'Save revision'
                      : 'Save as draft',
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Text(
                _isRevise
                    ? 'A revision bumps the version and re-enters approval: above '
                          '$_discountApprovalThreshold% discount it needs a manager, '
                          'otherwise it is approved outright.'
                    : 'Saved as a draft. Submit it afterwards to send it for approval or '
                          'get it approved outright.',
                textAlign: TextAlign.center,
                style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
              ),
              const SizedBox(height: AppSpacing.xl),
    ];
  }
}

/// A read-only field that opens a date picker — dates are chosen, never typed.
class _DateField extends StatelessWidget {
  const _DateField({
    required this.label,
    required this.value,
    required this.onTap,
    this.helper,
  });

  final String label;
  final DateTime? value;
  final VoidCallback onTap;
  final String? helper;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadii.sm),
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: const Icon(Icons.calendar_today_rounded),
          helperText: helper,
          helperMaxLines: 2,
        ),
        child: Text(value == null ? 'Select a date' : Formatters.date(value)),
      ),
    );
  }
}
