import '../../../shared/widgets/status_chip.dart';

/// Lifecycle of a room-availability request — mirrors backend `RoomRequestStatus`.
///
/// Wire values are UPPERCASE: `RoomRequestResponse` serializes the enum directly, the
/// same as `BookingResponse.status`. Note this differs from `Quotation.status`, which
/// `QuotationResponse` lowercases — a quirk of that endpoint only, not the convention.
enum RoomRequestStatus {
  pending('PENDING'),
  confirmed('CONFIRMED'),
  rejected('REJECTED'),
  superseded('SUPERSEDED'),

  /// Sales withdrew the question before Reservation answered it (UC-26.4).
  cancelled('CANCELLED');

  const RoomRequestStatus(this.wire);
  final String wire;

  static RoomRequestStatus fromWire(String? raw) => RoomRequestStatus.values.firstWhere(
    (s) => s.wire == raw?.toUpperCase(),
    orElse: () => RoomRequestStatus.pending,
  );

  /// True for states that are neither a live question nor a usable answer. Mirrors
  /// `RoomRequestStatus.notSpeakingForQuotation()` on the backend.
  bool get speaksForQuotation =>
      this != RoomRequestStatus.superseded && this != RoomRequestStatus.cancelled;

  StatusTone get tone => switch (this) {
    RoomRequestStatus.pending => StatusTone.warning,
    RoomRequestStatus.confirmed => StatusTone.success,
    RoomRequestStatus.rejected => StatusTone.danger,
    RoomRequestStatus.superseded => StatusTone.neutral,
    RoomRequestStatus.cancelled => StatusTone.neutral,
  };
}

/// Dart mirror of backend `RoomRequestResponse`.
///
/// [heldUntil] is the Reservation team's **commitment**, recorded verbatim — the actual
/// room hold lives in the hotel's PMS, never here.
class RoomRequest {
  const RoomRequest({
    required this.requestId,
    required this.quotationId,
    required this.quantity,
    required this.status,
    this.quoteNo,
    this.customerName,
    this.roomTypeRequested,
    this.checkInDate,
    this.checkOutDate,
    this.reservationNote,
    this.heldUntil,
    this.requestedByName,
    this.respondedByName,
    this.respondedAt,
    this.createdAt,
  });

  final String requestId;
  final String quotationId;
  final String? quoteNo;
  final String? customerName;
  final String? roomTypeRequested;
  final DateTime? checkInDate;
  final DateTime? checkOutDate;
  final int quantity;
  final RoomRequestStatus status;
  final String? reservationNote;
  final DateTime? heldUntil;
  final String? requestedByName;
  final String? respondedByName;
  final DateTime? respondedAt;
  final DateTime? createdAt;

  static DateTime? _parseDate(Object? raw) {
    if (raw is! String || raw.isEmpty) return null;
    return DateTime.tryParse(raw);
  }

  factory RoomRequest.fromJson(Map<String, dynamic> json) {
    return RoomRequest(
      requestId: json['requestId'] as String,
      quotationId: json['quotationId'] as String? ?? '',
      quoteNo: json['quoteNo'] as String?,
      customerName: json['customerName'] as String?,
      roomTypeRequested: json['roomTypeRequested'] as String?,
      checkInDate: _parseDate(json['checkInDate']),
      checkOutDate: _parseDate(json['checkOutDate']),
      quantity: (json['quantity'] as num?)?.toInt() ?? 1,
      status: RoomRequestStatus.fromWire(json['status'] as String?),
      reservationNote: json['reservationNote'] as String?,
      heldUntil: _parseDate(json['heldUntil']),
      requestedByName: json['requestedByName'] as String?,
      respondedByName: json['respondedByName'] as String?,
      respondedAt: _parseDate(json['respondedAt']),
      createdAt: _parseDate(json['createdAt']),
    );
  }

  /// True when the hold deadline has passed. A null [heldUntil] means "confirmed with no
  /// deadline given" and never expires — matching `RoomConfirmationGate.holdExpired`.
  bool get isHoldExpired => heldUntil != null && heldUntil!.isBefore(DateTime.now());

  /// Whether this answer still covers [roomType] / [checkIn] / [checkOut].
  ///
  /// Mirrors the backend's staleness check so the app can explain the block before the
  /// user triggers a 409. Quantity is excluded on purpose: it is not stored on the
  /// quotation, so it cannot drift independently of the request that captured it.
  bool coversQuotation({
    required String? roomType,
    required DateTime? checkIn,
    required DateTime? checkOut,
  }) {
    if (status != RoomRequestStatus.confirmed || isHoldExpired) return false;

    bool sameDay(DateTime? a, DateTime? b) {
      if (a == null || b == null) return a == null && b == null;
      return a.year == b.year && a.month == b.month && a.day == b.day;
    }

    final sameRoomType =
        (roomTypeRequested ?? '').trim().toLowerCase() == (roomType ?? '').trim().toLowerCase();

    return sameRoomType && sameDay(checkInDate, checkIn) && sameDay(checkOutDate, checkOut);
  }
}

/// Sales asks the Reservation team. Room type and dates come from the quotation
/// server-side, so only the quantity is sent.
class CreateRoomRequestPayload {
  const CreateRoomRequestPayload({required this.quotationId, required this.quantity});

  final String quotationId;
  final int quantity;

  Map<String, dynamic> toJson() => {'quotationId': quotationId, 'quantity': quantity};
}


/// The request that currently speaks for a quotation: the newest one that is neither
/// superseded nor cancelled. The API returns them newest-first, mirroring
/// `RoomConfirmationReader.currentRequest`.
///
/// Skipping cancelled rows is what lets an earlier confirmation resurface after Sales
/// withdraws a follow-up question, instead of the withdrawn row masking it.
RoomRequest? currentRoomRequest(List<RoomRequest> requests) {
  for (final r in requests) {
    if (r.status.speaksForQuotation) return r;
  }
  return null;
}
