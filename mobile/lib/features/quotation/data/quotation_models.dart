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
