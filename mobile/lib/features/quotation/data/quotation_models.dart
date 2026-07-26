import '../../../shared/widgets/status_chip.dart';

/// Quotation lifecycle — mirrors backend `QuotationStatus`, wire values are
/// the lowercased enum name (see `QuotationResponse.from`).
enum QuotationStatus {
  draft('draft'),
  pendingApproval('pending_approval'),
  approved('approved'),
  sent('sent'),
  interested('interested'),
  accepted('accepted'),
  rejected('rejected'),
  pendingRevision('pending_revision'),
  expired('expired'),
  closed('closed'),
  converted('converted');

  const QuotationStatus(this.wire);
  final String wire;

  static QuotationStatus fromWire(String? raw) => QuotationStatus.values
      .firstWhere((s) => s.wire == raw, orElse: () => QuotationStatus.draft);

  StatusTone get tone => switch (this) {
    QuotationStatus.draft => StatusTone.neutral,
    QuotationStatus.pendingApproval => StatusTone.warning,
    QuotationStatus.approved => StatusTone.brand,
    QuotationStatus.sent => StatusTone.info,
    QuotationStatus.interested => StatusTone.info,
    QuotationStatus.accepted => StatusTone.success,
    QuotationStatus.rejected => StatusTone.danger,
    QuotationStatus.pendingRevision => StatusTone.warning,
    QuotationStatus.expired => StatusTone.neutral,
    QuotationStatus.closed => StatusTone.neutral,
    QuotationStatus.converted => StatusTone.success,
  };

  /// UC-14.6 PRE-1 — customer response can only be recorded while the
  /// quotation is out with the customer (mirrors TrackCustomerResponseUseCase).
  bool get canTrackCustomerResponse =>
      this == QuotationStatus.sent || this == QuotationStatus.interested;
}

/// Dart mirror of backend `QuotationResponse`.
class Quotation {
  const Quotation({
    required this.id,
    required this.quoteNo,
    required this.status,
    this.dealId,
    this.dealName,
    this.customerId,
    this.contactName,
    this.email,
    this.phone,
    this.roomType,
    this.checkInDate,
    this.checkOutDate,
    this.nights,
    this.numberOfRooms,
    this.pricePerNight,
    this.subtotal,
    this.discountPercent,
    this.discountAmount,
    this.totalAmount,
    this.validUntil,
    this.notes,
    this.version,
    this.createdAt,
  });

  final String id;
  final String quoteNo;
  final QuotationStatus status;
  final String? dealId;
  final String? dealName;
  final String? customerId;
  final String? contactName;
  final String? email;
  final String? phone;
  final String? roomType;
  final DateTime? checkInDate;
  final DateTime? checkOutDate;
  final int? nights;
  final int? numberOfRooms;
  final double? pricePerNight;
  final double? subtotal;
  final double? discountPercent;
  final double? discountAmount;
  final double? totalAmount;
  final DateTime? validUntil;
  final String? notes;
  final int? version;
  final DateTime? createdAt;

  factory Quotation.fromJson(Map<String, dynamic> json) {
    DateTime? parseDate(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;
    double? parseNum(Object? v) => v is num ? v.toDouble() : null;

    return Quotation(
      id: json['id'] as String,
      quoteNo: json['quoteNo'] as String? ?? '',
      status: QuotationStatus.fromWire(json['status'] as String?),
      dealId: json['dealId'] as String?,
      dealName: json['dealName'] as String?,
      customerId: json['customerId'] as String?,
      contactName: json['contactName'] as String?,
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      roomType: json['roomType'] as String?,
      checkInDate: parseDate(json['checkInDate']),
      checkOutDate: parseDate(json['checkOutDate']),
      nights: json['nights'] as int?,
      numberOfRooms: json['numberOfRooms'] as int?,
      pricePerNight: parseNum(json['pricePerNight']),
      subtotal: parseNum(json['subtotal']),
      discountPercent: parseNum(json['discountPercent']),
      discountAmount: parseNum(json['discountAmount']),
      totalAmount: parseNum(json['totalAmount'] ?? json['amount']),
      validUntil: parseDate(json['validUntil']),
      notes: json['notes'] as String?,
      version: json['version'] as int?,
      createdAt: parseDate(json['createdAt']),
    );
  }
}

/// UC-14.6 customer response options — wire values match backend's
/// `TrackCustomerResponseRequest.customerResponse` (ACCEPTED | REJECTED |
/// INTERESTED | NEED_REVISION).
enum CustomerResponseType {
  accepted('ACCEPTED'),
  rejected('REJECTED'),
  interested('INTERESTED'),
  needRevision('NEED_REVISION');

  const CustomerResponseType(this.wire);
  final String wire;
}

/// Payload for UC-14.6 Track Customer Response.
class TrackCustomerResponsePayload {
  const TrackCustomerResponsePayload({
    required this.customerResponse,
    this.lostReason,
    this.notes,
    this.recordedByName,
    this.recordedByRole,
  });

  final CustomerResponseType customerResponse;
  final String? lostReason;
  final String? notes;
  final String? recordedByName;
  final String? recordedByRole;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{'customerResponse': customerResponse.wire};
    void put(String k, Object? v) {
      if (v != null && !(v is String && v.trim().isEmpty)) map[k] = v;
    }

    put('lostReason', lostReason);
    put('notes', notes);
    put('recordedByName', recordedByName);
    put('recordedByRole', recordedByRole);
    return map;
  }
}

/// Shared helper for the payloads below: the backend treats an absent field and a
/// blank string differently on some endpoints, so blanks are dropped rather than sent.
void _putIfPresent(Map<String, dynamic> map, String key, Object? value) {
  if (value == null) return;
  if (value is String && value.trim().isEmpty) return;
  map[key] = value is String ? value.trim() : value;
}

/// `yyyy-MM-dd` — the wire shape for every LocalDate field on the quotation endpoints.
String _isoDate(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')}';

/// UC-14.1 — create a quotation against a deal. Mirrors web `CreateQuotationPayload`.
///
/// Always lands as DRAFT: the status is resolved later by an explicit Submit, which
/// routes to APPROVED or PENDING_APPROVAL depending on the discount threshold.
class CreateQuotationPayload {
  const CreateQuotationPayload({
    required this.dealId,
    required this.roomType,
    required this.checkInDate,
    required this.checkOutDate,
    required this.numberOfRooms,
    required this.pricePerNight,
    required this.discountPercent,
    required this.paymentPolicy,
    required this.validUntil,
    this.notes,
  });

  final String dealId;
  final String roomType;
  final DateTime checkInDate;
  final DateTime checkOutDate;
  final int numberOfRooms;
  final num pricePerNight;
  final num discountPercent;
  final String paymentPolicy;
  final DateTime validUntil;
  final String? notes;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'dealId': dealId,
      'roomType': roomType.trim(),
      'checkInDate': _isoDate(checkInDate),
      'checkOutDate': _isoDate(checkOutDate),
      'numberOfRooms': numberOfRooms,
      'pricePerNight': pricePerNight,
      'discountPercent': discountPercent,
      'paymentPolicy': paymentPolicy,
      'validUntil': _isoDate(validUntil),
    };
    _putIfPresent(map, 'notes', notes);
    return map;
  }
}

/// UC-14.2 — submit a DRAFT for approval. Discount over the configured threshold goes
/// to PENDING_APPROVAL, otherwise straight to APPROVED.
class SubmitQuotationPayload {
  const SubmitQuotationPayload({this.submittedByName, this.submittedByRole});

  final String? submittedByName;
  final String? submittedByRole;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{};
    _putIfPresent(map, 'submittedByName', submittedByName);
    _putIfPresent(map, 'submittedByRole', submittedByRole);
    return map;
  }
}

/// UC-14.4 — send an APPROVED quotation to the customer.
///
/// Blocked server-side unless the Reservation team has confirmed the rooms
/// (`RoomConfirmationGate`): a 409 here carries a `ROOM_*` error code.
class SendQuotationPayload {
  const SendQuotationPayload({
    required this.sendMethod,
    required this.recipientName,
    this.recipientEmail,
    this.recipientPhone,
    this.personalMessage,
  });

  /// `EMAIL` | `WHATSAPP` | `PDF`. EMAIL requires [recipientEmail].
  final String sendMethod;
  final String recipientName;
  final String? recipientEmail;
  final String? recipientPhone;
  final String? personalMessage;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'sendMethod': sendMethod,
      'recipientName': recipientName.trim(),
    };
    _putIfPresent(map, 'recipientEmail', recipientEmail);
    _putIfPresent(map, 'recipientPhone', recipientPhone);
    _putIfPresent(map, 'personalMessage', personalMessage);
    return map;
  }
}

/// UC-14.5 — a new version of an existing quotation; the parent becomes SUPERSEDED.
class ReviseQuotationPayload {
  const ReviseQuotationPayload({
    required this.roomType,
    required this.checkInDate,
    required this.checkOutDate,
    required this.numberOfRooms,
    required this.pricePerNight,
    required this.discountPercent,
    required this.paymentPolicy,
    required this.validUntil,
    required this.changeReason,
    this.notes,
  });

  final String roomType;
  final DateTime checkInDate;
  final DateTime checkOutDate;
  final int numberOfRooms;
  final num pricePerNight;
  final num discountPercent;
  final String paymentPolicy;
  final DateTime validUntil;

  /// Required by the backend — the audit trail for why the price/dates changed.
  final String changeReason;
  final String? notes;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'roomType': roomType.trim(),
      'checkInDate': _isoDate(checkInDate),
      'checkOutDate': _isoDate(checkOutDate),
      'numberOfRooms': numberOfRooms,
      'pricePerNight': pricePerNight,
      'discountPercent': discountPercent,
      'paymentPolicy': paymentPolicy,
      'validUntil': _isoDate(validUntil),
      'changeReason': changeReason.trim(),
    };
    _putIfPresent(map, 'notes', notes);
    return map;
  }
}

/// UC-14.7 — turn an ACCEPTED quotation into a PENDING booking.
///
/// Also gated on a confirmed room, checked against the dates actually being booked.
class ConvertToBookingPayload {
  const ConvertToBookingPayload({
    required this.contactName,
    required this.email,
    required this.phone,
    required this.roomType,
    required this.checkInDate,
    required this.checkOutDate,
    this.specialRequests,
  });

  final String contactName;
  final String email;
  final String phone;
  final String roomType;
  final DateTime checkInDate;
  final DateTime checkOutDate;
  final String? specialRequests;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'contactName': contactName.trim(),
      'email': email.trim(),
      'phone': phone.trim(),
      'roomType': roomType.trim(),
      'checkInDate': _isoDate(checkInDate),
      'checkOutDate': _isoDate(checkOutDate),
    };
    _putIfPresent(map, 'specialRequests', specialRequests);
    return map;
  }
}

/// UC-14.8 — close a quotation that will not proceed. [reason] is the audit trail.
class CloseQuotationPayload {
  const CloseQuotationPayload({required this.reason, this.closedByName, this.closedByRole});

  final String reason;
  final String? closedByName;
  final String? closedByRole;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{'reason': reason.trim()};
    _putIfPresent(map, 'closedByName', closedByName);
    _putIfPresent(map, 'closedByRole', closedByRole);
    return map;
  }
}
