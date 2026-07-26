import '../../../shared/widgets/status_chip.dart';

/// Where a handover is in the Sales → Front Office chain — mirrors backend
/// `HandoverStatus`. Wire values are UPPERCASE (`ArrivalHandoverResponse.status` is a
/// plain String on the DTO).
enum HandoverStatus {
  submitted('SUBMITTED', 'Submitted'),
  acknowledged('ACKNOWLEDGED', 'Acknowledged'),
  ready('READY', 'Ready');

  const HandoverStatus(this.wire, this.label);
  final String wire;
  final String label;

  static HandoverStatus? fromWire(String? raw) {
    if (raw == null) return null;
    final upper = raw.toUpperCase();
    for (final s in values) {
      if (s.wire == upper) return s;
    }
    // Unknown future status degrades to null rather than guessing — the raw string is
    // kept on the model so it can still be shown.
    return null;
  }

  StatusTone get tone => switch (this) {
    HandoverStatus.submitted => StatusTone.info,
    HandoverStatus.acknowledged => StatusTone.warning,
    HandoverStatus.ready => StatusTone.success,
  };
}

/// How far the Front Office has got with preparing the arrival — mirrors backend
/// `ReadinessStatus`.
enum ReadinessStatus {
  pendingReview('PENDING_REVIEW', 'Pending review'),
  reviewed('REVIEWED', 'Reviewed'),
  readyForArrival('READY_FOR_ARRIVAL', 'Ready for arrival'),
  needClarification('NEED_CLARIFICATION', 'Needs clarification');

  const ReadinessStatus(this.wire, this.label);
  final String wire;
  final String label;

  static ReadinessStatus? fromWire(String? raw) {
    if (raw == null) return null;
    final upper = raw.toUpperCase();
    for (final s in values) {
      if (s.wire == upper) return s;
    }
    return null;
  }

  StatusTone get tone => switch (this) {
    ReadinessStatus.pendingReview => StatusTone.neutral,
    ReadinessStatus.reviewed => StatusTone.info,
    ReadinessStatus.readyForArrival => StatusTone.success,
    ReadinessStatus.needClarification => StatusTone.danger,
  };
}

/// One room on the handover — the breakdown the arrival desk needs.
class HandoverRoomLine {
  const HandoverRoomLine({
    this.productName,
    this.roomNumber,
    this.quantity,
    this.nights,
    this.inventoryStatus,
  });

  final String? productName;
  final String? roomNumber;
  final int? quantity;
  final int? nights;
  final String? inventoryStatus;

  factory HandoverRoomLine.fromJson(Map<String, dynamic> json) => HandoverRoomLine(
    productName: json['productName'] as String?,
    roomNumber: json['roomNumber'] as String?,
    quantity: (json['quantity'] as num?)?.toInt(),
    nights: (json['nights'] as num?)?.toInt(),
    inventoryStatus: json['inventoryStatus'] as String?,
  );
}

/// Dart mirror of backend `ArrivalHandoverResponse`, as served by `/operational-handovers`
/// — the Sales-facing side of the chain. The arrival desk's own endpoints return the same
/// DTO, so this model stays valid if the desk ever gets a mobile surface.
class Handover {
  const Handover({
    required this.handoverId,
    this.bookingId,
    this.bookingCode,
    this.customerName,
    this.customerPhone,
    this.checkInDate,
    this.checkOutDate,
    this.roomSummary,
    this.rooms = const [],
    this.specialRequests,
    this.roomPreferences,
    this.vipNotes,
    this.operationalNotes,
    this.paymentReference,
    this.status,
    this.statusRaw,
    this.readinessStatus,
    this.readinessStatusRaw,
    this.clarificationNote,
    this.submittedAt,
    this.acknowledgedAt,
    this.updatedByName,
    this.createdAt,
    this.updatedAt,
  });

  final String handoverId;
  final String? bookingId;
  final String? bookingCode;
  final String? customerName;
  final String? customerPhone;

  /// Arrival date.
  final DateTime? checkInDate;
  final DateTime? checkOutDate;

  /// Compact one-liner the backend builds for list rows.
  final String? roomSummary;
  final List<HandoverRoomLine> rooms;

  final String? specialRequests;
  final String? roomPreferences;
  final String? vipNotes;
  final String? operationalNotes;
  final String? paymentReference;

  final HandoverStatus? status;

  /// Raw wire value, kept so an unrecognised status can still be displayed.
  final String? statusRaw;
  final ReadinessStatus? readinessStatus;
  final String? readinessStatusRaw;
  final String? clarificationNote;

  final DateTime? submittedAt;
  final DateTime? acknowledgedAt;
  final String? updatedByName;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  static DateTime? _date(Object? raw) {
    if (raw is! String || raw.isEmpty) return null;
    return DateTime.tryParse(raw);
  }

  factory Handover.fromJson(Map<String, dynamic> json) {
    final statusRaw = json['status'] as String?;
    final readinessRaw = json['readinessStatus'] as String?;
    return Handover(
      handoverId: json['handoverId'] as String,
      bookingId: json['bookingId'] as String?,
      bookingCode: json['bookingCode'] as String?,
      customerName: json['customerName'] as String?,
      customerPhone: json['customerPhone'] as String?,
      checkInDate: _date(json['checkInDate']),
      checkOutDate: _date(json['checkOutDate']),
      roomSummary: json['roomSummary'] as String?,
      rooms: (json['rooms'] as List?)
              ?.map((e) => HandoverRoomLine.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const [],
      specialRequests: json['specialRequests'] as String?,
      roomPreferences: json['roomPreferences'] as String?,
      vipNotes: json['vipNotes'] as String?,
      operationalNotes: json['operationalNotes'] as String?,
      paymentReference: json['paymentReference'] as String?,
      status: HandoverStatus.fromWire(statusRaw),
      statusRaw: statusRaw,
      readinessStatus: ReadinessStatus.fromWire(readinessRaw),
      readinessStatusRaw: readinessRaw,
      clarificationNote: json['clarificationNote'] as String?,
      submittedAt: _date(json['submittedAt']),
      acknowledgedAt: _date(json['acknowledgedAt']),
      updatedByName: json['updatedByName'] as String?,
      createdAt: _date(json['createdAt']),
      updatedAt: _date(json['updatedAt']),
    );
  }

  String get displayStatus => status?.label ?? statusRaw ?? '—';
  String get displayReadiness => readinessStatus?.label ?? readinessStatusRaw ?? '—';
  StatusTone get statusTone => status?.tone ?? StatusTone.neutral;
  StatusTone get readinessTone => readinessStatus?.tone ?? StatusTone.neutral;

  /// True when the arrival is today or already past — the desk's urgent bucket.
  bool get isArrivingToday {
    final d = checkInDate;
    if (d == null) return false;
    final now = DateTime.now();
    return !d.isAfter(DateTime(now.year, now.month, now.day));
  }
}

/// UC-22 — Sales/Reservation hands a confirmed booking to the Front Office.
class CreateHandoverPayload {
  const CreateHandoverPayload({
    required this.bookingId,
    required this.status,
    this.specialRequests,
    this.roomPreferences,
    this.vipNotes,
    this.operationalNotes,
  });

  final String bookingId;

  /// `SUBMITTED` sends it to the desk; the backend also accepts a draft status.
  final HandoverStatus status;
  final String? specialRequests;
  final String? roomPreferences;
  final String? vipNotes;
  final String? operationalNotes;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{'bookingId': bookingId, 'status': status.wire};
    void put(String k, String? v) {
      if (v != null && v.trim().isNotEmpty) map[k] = v.trim();
    }

    put('specialRequests', specialRequests);
    put('roomPreferences', roomPreferences);
    put('vipNotes', vipNotes);
    put('operationalNotes', operationalNotes);
    return map;
  }
}
