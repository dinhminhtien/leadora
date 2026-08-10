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
import '../../../deal/presentation/widgets/deal_picker_sheet.dart';
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

/// Editable state for one room-type row (UC-14.1/14.5, BR-23: multiple room types +
/// quantity per quotation). Not a `Quotation` model — just the controllers backing a
/// single row of the form.
class _RoomLineState {
  _RoomLineState({this.roomType, String rooms = '1', String price = ''})
    : roomsController = TextEditingController(text: rooms),
      priceController = TextEditingController(text: price);

  String? roomType;
  final TextEditingController roomsController;
  final TextEditingController priceController;

  void dispose() {
    roomsController.dispose();
    priceController.dispose();
  }
}

class _QuotationFormScreenState extends ConsumerState<QuotationFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _discount;
  late final TextEditingController _notes;
  final _changeReason = TextEditingController();

  /// One or more room types, each with its own quantity and rate.
  final List<_RoomLineState> _roomLines = [];

  String? _dealId;
<<<<<<< Updated upstream

  /// The picked deal, kept for display only — [_dealId] stays the value that is validated
  /// and submitted. Null when the form was opened with an `initialDealId` and the deal
  /// itself has not been fetched; the picker field falls back to `dealDetailProvider`.
  Deal? _selectedDeal;

  String? _roomType;
=======
>>>>>>> Stashed changes
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

    _discount = TextEditingController(text: '0');
    _notes = TextEditingController();

    if (_isRevise && q != null) {
      // Start from what the customer was last quoted, so the rep changes only what moved.
      _dealId = q.dealId;
      _applyTemplateValues(q);
    } else {
      _dealId = widget.initialDealId;
      _roomLines.add(_RoomLineState());
      if (_dealId != null) {
        // Best-effort: this deal may already have an earlier quotation — seed the same
        // fields from it once the list resolves. See _prefillFromDealHistory.
        _prefillFromDealHistory(_dealId!);
      }
    }

    // Fill any gap the source quotation left, and give create a sensible starting window
    // so the common case is two taps, not six.
    _normalizeDates();
  }

  /// Trims a trailing `.0` so a whole number does not land in the field as "40.0".
  static String _plain(num v) =>
      v == v.roundToDouble() ? v.toInt().toString() : v.toString();

  /// Rebuilds [_roomLines] from [source]'s per-room-type breakdown — the same field set
  /// a revision pre-fills from the quotation being revised, reused so a fresh create for a
  /// deal that was already quoted starts from that quote instead of blank. Falls back to
  /// the single-line legacy shape (`roomType`/`numberOfRooms`/`pricePerNight`) for
  /// quotations the backend hasn't attached a breakdown to. Leaves `_dealId` untouched.
  void _applyTemplateValues(Quotation source) {
    for (final line in _roomLines) {
      line.dispose();
    }
    _roomLines.clear();

    final lines = source.roomLines;
    if (lines != null && lines.isNotEmpty) {
      for (final l in lines) {
        _roomLines.add(
          _RoomLineState(
            roomType: _roomTypes.contains(l.roomType) ? l.roomType : null,
            rooms: '${l.numberOfRooms}',
            price: _plain(l.pricePerNight),
          ),
        );
      }
    } else {
      // The room type may be free text the catalogue does not list — only preselect a
      // dropdown value that exists, otherwise the field would show blank but validate.
      _roomLines.add(
        _RoomLineState(
          roomType: _roomTypes.contains(source.roomType) ? source.roomType : null,
          rooms: '${source.numberOfRooms ?? 1}',
          price: source.pricePerNight == null ? '' : _plain(source.pricePerNight!),
        ),
      );
    }

    _discount.text = _plain(source.discountPercent ?? 0);
    _checkIn = source.checkInDate;
    _checkOut = source.checkOutDate;
    _validUntil = source.validUntil;
  }

  /// Fills any gap left after a template/revise copy, and gives a from-scratch create a
  /// sensible starting window, so the common case is two taps, not six.
  void _normalizeDates() {
    final tomorrow = DateTime.now().add(const Duration(days: 1));
    _checkIn ??= DateTime(tomorrow.year, tomorrow.month, tomorrow.day);
    if (_checkOut == null || !_checkOut!.isAfter(_checkIn!)) {
      _checkOut = _checkIn!.add(const Duration(days: 1));
    }
    _validUntil ??= _checkIn!.subtract(const Duration(days: 1));
  }

  /// Best-effort: when the picked deal already has an earlier quotation, seed the stay
  /// and pricing fields from its most recent one — re-quoting the same lead is then a
  /// tweak, not a re-type. Everything stays fully editable afterward; this only sets the
  /// starting values, and only if the deal selection hasn't changed again by the time the
  /// list resolves.
  Future<void> _prefillFromDealHistory(String dealId) async {
    final List<Quotation> quotations;
    try {
      quotations = await ref.read(quotationListProvider.future);
    } catch (_) {
      return;
    }
    if (!mounted || _dealId != dealId) return;

    final forDeal = quotations.where((q) => q.dealId == dealId).toList()
      ..sort(
        (a, b) => (b.createdAt ?? DateTime(0)).compareTo(a.createdAt ?? DateTime(0)),
      );
    if (forDeal.isEmpty) return;

    setState(() {
      _applyTemplateValues(forDeal.first);
      _normalizeDates();
    });
  }

  @override
  void dispose() {
    for (final line in _roomLines) {
      line.dispose();
    }
    for (final c in [_discount, _notes, _changeReason]) {
      c.dispose();
    }
    super.dispose();
  }

  int get _nights {
    if (_checkIn == null || _checkOut == null) return 0;
    final diff = _checkOut!.difference(_checkIn!).inDays;
    return diff > 0 ? diff : 0;
  }

  double _lineSubtotal(_RoomLineState line) {
    final rooms = int.tryParse(line.roomsController.text.trim()) ?? 0;
    final price = double.tryParse(line.priceController.text.trim()) ?? 0;
    return price * _nights * rooms;
  }

  int get _totalRooms => _roomLines.fold(
    0,
    (sum, l) => sum + (int.tryParse(l.roomsController.text.trim()) ?? 0),
  );

  double get _discountPct => double.tryParse(_discount.text.trim()) ?? 0;

  double get _subtotal => _roomLines.fold(0.0, (sum, l) => sum + _lineSubtotal(l));
  double get _discountAmount => _subtotal * _discountPct / 100;
  double get _total => _subtotal - _discountAmount;

  bool get _needsApproval => _discountPct > _discountApprovalThreshold;

  bool get _hasDuplicateRoomTypes {
    final types = _roomLines.map((l) => l.roomType).whereType<String>().toList();
    return types.toSet().length != types.length;
  }

  void _addRoomLine() {
    setState(() => _roomLines.add(_RoomLineState()));
  }

  void _removeRoomLine(int index) {
    if (_roomLines.length == 1) return;
    setState(() => _roomLines.removeAt(index).dispose());
  }

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
    final missingRoomType = _roomLines.any((l) => l.roomType == null);
    final missing =
        (!_isRevise && _dealId == null) || missingRoomType || _validUntil == null;
    if (!formOk || missing || _nights <= 0 || _hasDuplicateRoomTypes) {
      setState(() => _autovalidate = true);
      if (missing || _nights <= 0 || _hasDuplicateRoomTypes) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _nights <= 0
                  ? 'Check-out must be at least one night after check-in.'
                  : _hasDuplicateRoomTypes
                  ? 'Each room type can only be added once — adjust the quantity instead.'
                  : _isRevise
                  ? 'Select a room type for every row and a validity date.'
                  : 'Select a deal, a room type for every row, and a validity date.',
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

    final roomLines = _roomLines
        .map(
          (l) => RoomLineRequest(
            roomType: l.roomType!,
            numberOfRooms: int.parse(l.roomsController.text.trim()),
            pricePerNight: double.parse(l.priceController.text.trim()),
          ),
        )
        .toList();

    try {
      final Quotation quotation;
      final String message;

      if (_isRevise) {
        quotation = await actions.revise(
          widget.quotation!.id,
          ReviseQuotationPayload(
            roomLines: roomLines,
            checkInDate: _checkIn!,
            checkOutDate: _checkOut!,
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
            roomLines: roomLines,
            checkInDate: _checkIn!,
            checkOutDate: _checkOut!,
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

  /// Deal selection (UC-14.1).
  ///
  /// A tap target that opens [showQuotableDealPicker] rather than a drop-down of every
  /// deal: the old menu grew without bound and, because mobile applied no eligibility
  /// filter, offered deals the backend rejects. The sheet asks `GET /deals/quotable` for a
  /// page at a time and this screen filters nothing.
  Widget _dealPicker(ThemeData theme, ColorScheme scheme) {
    // Reached from a deal's workspace: the id is known but the deal is not, so resolve
    // its name for display. Falls back to the id's absence, never blocks the form.
    final resolved =
        _selectedDeal ??
        (_dealId == null
            ? null
            : ref.watch(dealDetailProvider(_dealId!)).valueOrNull);

    final showError = _autovalidate && _dealId == null;

    return SectionCard(
      title: 'Deal',
      icon: Icons.work_outline_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: _pickDeal,
            borderRadius: BorderRadius.circular(AppRadii.md),
            child: InputDecorator(
              decoration: InputDecoration(
                labelText: 'Deal *',
                prefixIcon: const Icon(Icons.work_outline_rounded),
                suffixIcon: const Icon(Icons.search_rounded),
                errorText: showError ? 'Select a deal' : null,
              ),
              isEmpty: false,
              child: resolved == null
                  ? Text(
                      _dealId == null ? 'Search and select a deal' : 'Loading deal…',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    )
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          resolved.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodyMedium,
                        ),
                        if ((resolved.contactName ?? '').isNotEmpty)
                          Text(
                            resolved.contactName!,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: scheme.onSurfaceVariant,
                            ),
                          ),
                      ],
                    ),
            ),
          ),
        ],
      ),
    );
  }

<<<<<<< Updated upstream
  Future<void> _pickDeal() async {
    final picked = await showQuotableDealPicker(context, selectedDealId: _dealId);
    if (picked == null || !mounted) return;
    setState(() {
      _dealId = picked.id;
      _selectedDeal = picked;
    });
    _prefillFromDealHistory(picked.id);
  }

  /// Everything below the header: the stay, the pricing, the live total and the save
=======
  /// Everything below the header: the stay, the room types, the live total and the save
>>>>>>> Stashed changes
  /// button. Identical in both modes, so it is built once.
  List<Widget> _stayAndPricing(ThemeData theme, ColorScheme scheme) {
    return [
              SectionCard(
                title: 'Stay',
                icon: Icons.hotel_outlined,
                child: Column(
                  children: [
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
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.md),

              // One card per room type — each with its own quantity and rate (BR-23).
              SectionCard(
                title: 'Room types',
                icon: Icons.meeting_room_outlined,
                child: Column(
                  children: [
                    for (var i = 0; i < _roomLines.length; i++) ...[
                      if (i > 0) const SizedBox(height: AppSpacing.lg),
                      _roomLineRow(i),
                    ],
                    const SizedBox(height: AppSpacing.lg),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: OutlinedButton.icon(
                        onPressed: _addRoomLine,
                        icon: const Icon(Icons.add_rounded),
                        label: const Text('Add room type'),
                      ),
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
                    for (final line in _roomLines)
                      if (line.roomType != null)
                        Padding(
                          padding: const EdgeInsets.only(bottom: AppSpacing.xs),
                          child: InfoRow(
                            label:
                                '${line.roomType} × '
                                '${line.roomsController.text.trim().isEmpty ? 0 : line.roomsController.text.trim()}',
                            value: Formatters.money(_lineSubtotal(line)),
                          ),
                        ),
                    InfoRow(
                      label: 'Subtotal',
                      value: '${Formatters.money(_subtotal)}'
                          '${_nights > 0 ? "  ($_totalRooms rooms x $_nights night${_nights == 1 ? "" : "s"})" : ""}',
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

  /// One room-type row: room type dropdown, quantity, rate, and — once a second row
  /// exists — a way to remove it.
  Widget _roomLineRow(int index) {
    final line = _roomLines[index];
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
        borderRadius: BorderRadius.circular(AppRadii.sm),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<String>(
                  initialValue: line.roomType,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: 'Room type *',
                    prefixIcon: Icon(Icons.bed_outlined),
                  ),
                  items: [
                    for (final rt in _roomTypes)
                      DropdownMenuItem(value: rt, child: Text(rt)),
                  ],
                  onChanged: (v) => setState(() => line.roomType = v),
                  validator: (v) => v == null ? 'Select a room type' : null,
                ),
              ),
              if (_roomLines.length > 1) ...[
                const SizedBox(width: AppSpacing.sm),
                IconButton(
                  onPressed: () => _removeRoomLine(index),
                  icon: const Icon(Icons.delete_outline_rounded),
                  tooltip: 'Remove room type',
                ),
              ],
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: TextFormField(
                  controller: line.roomsController,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    labelText: 'Quantity *',
                    prefixIcon: Icon(Icons.meeting_room_outlined),
                  ),
                  validator: _validateRooms,
                  onChanged: (_) => setState(() {}),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: TextFormField(
                  controller: line.priceController,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    labelText: 'Price/night (VND) *',
                    prefixIcon: Icon(Icons.attach_money_rounded),
                  ),
                  validator: _validatePrice,
                  onChanged: (_) => setState(() {}),
                ),
              ),
            ],
          ),
        ],
      ),
    );
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
