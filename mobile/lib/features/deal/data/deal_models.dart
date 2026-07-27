import '../../../shared/widgets/status_chip.dart';

/// Deal status — mirrors backend `DealResponse.status` ("active" | "won" |
/// "lost", see `DealMapper.mapStatusToString`).
enum DealStatus {
  active('active'),
  won('won'),
  lost('lost');

  const DealStatus(this.wire);
  final String wire;

  static DealStatus fromWire(String? raw) => DealStatus.values.firstWhere(
    (s) => s.wire == raw,
    orElse: () => DealStatus.active,
  );

  StatusTone get tone => switch (this) {
    DealStatus.active => StatusTone.brand,
    DealStatus.won => StatusTone.success,
    DealStatus.lost => StatusTone.danger,
  };
}

/// Pipeline stage — mirrors backend enum `DealPipelineStage`, carried on the
/// wire as `DealResponse.stageCode`.
///
/// The sibling `DealResponse.stage` field is a lossy display label (CLOSED_WON
/// and CLOSED_LOST both serialize as "Confirmed"), so it must never drive logic.
/// Tabs, funnel ordering and the Kanban board key off this enum instead.
enum DealStage {
  inquiry('INQUIRY', 'Inquiry'),
  qualification('QUALIFICATION', 'Qualification'),
  quotationSent('QUOTATION_SENT', 'Proposal'),
  negotiation('NEGOTIATION', 'Negotiation'),
  pendingConfirmation('PENDING_CONFIRMATION', 'Contract'),
  bookingConfirmed('BOOKING_CONFIRMED', 'Confirmed'),
  closedWon('CLOSED_WON', 'Won'),
  closedLost('CLOSED_LOST', 'Lost');

  const DealStage(this.wire, this.label);

  final String wire;
  final String label;

  /// Returns `null` for an unrecognized value so a future backend stage renders
  /// as its raw label rather than being silently coerced into "New".
  static DealStage? fromWire(String? raw) {
    if (raw == null) return null;
    for (final s in DealStage.values) {
      if (s.wire == raw) return s;
    }
    return null;
  }

  /// Position in the sales funnel. Both terminal stages share the last slot.
  int get order => switch (this) {
    DealStage.inquiry => 0,
    DealStage.qualification => 1,
    DealStage.quotationSent => 2,
    DealStage.negotiation => 3,
    DealStage.pendingConfirmation => 4,
    DealStage.bookingConfirmed => 5,
    DealStage.closedWon => 6,
    DealStage.closedLost => 6,
  };

  bool get isTerminal =>
      this == DealStage.closedWon || this == DealStage.closedLost;

  /// The next stage a user can advance to, or `null` once terminal.
  DealStage? get next => switch (this) {
    DealStage.inquiry => DealStage.qualification,
    DealStage.qualification => DealStage.quotationSent,
    DealStage.quotationSent => DealStage.negotiation,
    DealStage.negotiation => DealStage.pendingConfirmation,
    DealStage.pendingConfirmation => DealStage.bookingConfirmed,
    DealStage.bookingConfirmed => DealStage.closedWon,
    DealStage.closedWon || DealStage.closedLost => null,
  };

  StatusTone get tone => switch (this) {
    DealStage.inquiry => StatusTone.neutral,
    DealStage.qualification => StatusTone.info,
    DealStage.quotationSent => StatusTone.brand,
    DealStage.negotiation => StatusTone.warning,
    DealStage.pendingConfirmation => StatusTone.brand,
    DealStage.bookingConfirmed => StatusTone.success,
    DealStage.closedWon => StatusTone.success,
    DealStage.closedLost => StatusTone.danger,
  };
}

/// Dart mirror of backend `DealResponse`.
class Deal {
  const Deal({
    required this.id,
    required this.title,
    required this.status,
    this.contactName,
    this.email,
    this.phone,
    this.value,
    this.probability,
    this.stage,
    this.stageLabel,
    this.owner,
    this.expectedClose,
    this.createdAt,
    this.notes,
  });

  final String id;
  final String title;
  final DealStatus status;

  /// Authoritative stage (`stageCode`). Null when the backend sends a stage this
  /// build does not know about — fall back to [stageLabel] for display.
  final DealStage? stage;

  /// Backend's human-readable label (`stage`). Display-only.
  final String? stageLabel;

  final String? contactName;
  final String? email;
  final String? phone;
  final double? value;
  final int? probability;
  final String? owner;
  final DateTime? expectedClose;
  final DateTime? createdAt;
  final String? notes;

  /// What to render in a chip: the known stage's label, else the backend's raw
  /// label, else a dash.
  String get displayStage => stage?.label ?? stageLabel ?? '—';

  StatusTone get stageTone => stage?.tone ?? StatusTone.neutral;

  factory Deal.fromJson(Map<String, dynamic> json) {
    DateTime? parseDate(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;

    return Deal(
      id: json['id'] as String,
      title: json['title'] as String? ?? 'Untitled deal',
      status: DealStatus.fromWire(json['status'] as String?),
      stage: DealStage.fromWire(json['stageCode'] as String?),
      stageLabel: json['stage'] as String?,
      contactName: json['contactName'] as String?,
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      value: (json['value'] as num?)?.toDouble(),
      probability: json['probability'] as int?,
      owner: json['owner'] as String?,
      expectedClose: parseDate(json['expectedClose']),
      createdAt: parseDate(json['createdAt']),
      notes: json['notes'] as String?,
    );
  }
}

/// Body for `POST /deals` and `PUT /deals/{id}` (backend `DealRequest`).
///
/// PUT is a partial update: a null field means "unchanged". `title` and
/// `contactName` are `@NotBlank` server-side, so they are always sent.
class DealPayload {
  const DealPayload({
    required this.title,
    required this.contactName,
    this.email,
    this.phone,
    this.value,
    this.stage,
    this.status,
    this.expectedClose,
    this.notes,
    this.owner,
  });

  final String title;
  final String contactName;
  final String? email;
  final String? phone;
  final double? value;
  final DealStage? stage;
  final DealStatus? status;
  final DateTime? expectedClose;
  final String? notes;
  final String? owner;

  /// `expectedClose` maps to a Java `LocalDate`, so it is sent as a plain
  /// calendar day with no offset — never an instant.
  static String _localDate(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-'
      '${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';

  Map<String, dynamic> toJson() => {
    'title': title,
    'contactName': contactName,
    if (email != null) 'email': email,
    if (phone != null) 'phone': phone,
    if (value != null) 'value': value,
    // Wire enum, not the display label — the backend accepts both but only the
    // enum round-trips (see DealMapper).
    if (stage != null) 'stage': stage!.wire,
    if (status != null) 'status': status!.wire,
    if (expectedClose != null) 'expectedClose': _localDate(expectedClose!),
    if (notes != null) 'notes': notes,
    if (owner != null) 'owner': owner,
  };
}

/// Dart mirror of backend `DealWorkflowSummaryResponse` — where the deal stands in the
/// Sales lifecycle, resolved server-side from the live quotation/booking/payment chain.
///
/// Wire-format trap: `GetDealWorkflowSummaryUseCase` serializes both enums with `.name()`,
/// so [dealStatusRaw] is `OPEN | WON | LOST` and [pipelineStageRaw] is a [DealStage] wire
/// value. That is **not** the same as `DealResponse.status`, which `DealMapper` lowercases
/// to `active | won | lost` — do not reuse [DealStatus.fromWire] on this payload.
class DealWorkflowSummary {
  const DealWorkflowSummary({
    required this.dealId,
    required this.hasPaidPayment,
    this.dealStatusRaw,
    this.pipelineStageRaw,
    this.activeQuotationId,
    this.activeQuotationStatus,
    this.activeBookingId,
    this.activeBookingStatus,
    this.currentPaymentStatus,
  });

  final String dealId;
  final bool hasPaidPayment;

  /// `OPEN | WON | LOST`.
  final String? dealStatusRaw;
  final String? pipelineStageRaw;
  final String? activeQuotationId;
  final String? activeQuotationStatus;
  final String? activeBookingId;
  final String? activeBookingStatus;
  final String? currentPaymentStatus;

  factory DealWorkflowSummary.fromJson(Map<String, dynamic> json) =>
      DealWorkflowSummary(
        dealId: json['dealId'] as String,
        hasPaidPayment: json['hasPaidPayment'] as bool? ?? false,
        dealStatusRaw: json['dealStatus'] as String?,
        pipelineStageRaw: json['pipelineStage'] as String?,
        activeQuotationId: json['activeQuotationId'] as String?,
        activeQuotationStatus: json['activeQuotationStatus'] as String?,
        activeBookingId: json['activeBookingId'] as String?,
        activeBookingStatus: json['activeBookingStatus'] as String?,
        currentPaymentStatus: json['currentPaymentStatus'] as String?,
      );

  /// Parsed stage, or `null` for a stage this build does not know.
  DealStage? get stage => DealStage.fromWire(pipelineStageRaw?.toUpperCase());

  String get displayStage => stage?.label ?? pipelineStageRaw ?? '—';

  bool get isWon => dealStatusRaw?.toUpperCase() == 'WON';
  bool get isLost => dealStatusRaw?.toUpperCase() == 'LOST';

  /// Nothing is "in progress" on a deal that has already been decided.
  bool get isClosed => isWon || isLost;
}
