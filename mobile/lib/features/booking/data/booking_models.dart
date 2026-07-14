import '../../../shared/widgets/status_chip.dart';

/// Mirrors backend enum `BookingStatus`.
enum BookingStatus {
  pending('PENDING', 'Pending'),
  confirmed('CONFIRMED', 'Confirmed'),
  checkedIn('CHECKED_IN', 'Checked in'),
  checkedOut('CHECKED_OUT', 'Checked out'),
  cancelled('CANCELLED', 'Cancelled'),
  noShow('NO_SHOW', 'No show'),
  rejected('REJECTED', 'Rejected');

  const BookingStatus(this.wire, this.label);
  final String wire;
  final String label;

  /// Null for an unrecognized value — degrade to the raw string, never coerce.
  static BookingStatus? fromWire(String? raw) {
    if (raw == null) return null;
    for (final s in BookingStatus.values) {
      if (s.wire == raw) return s;
    }
    return null;
  }

  /// A cancelled or checked-out booking rejects payment updates server-side
  /// (BR-44), so the UI must not offer them.
  bool get blocksPayment =>
      this == BookingStatus.cancelled || this == BookingStatus.checkedOut;

  StatusTone get tone => switch (this) {
    BookingStatus.pending => StatusTone.warning,
    BookingStatus.confirmed => StatusTone.success,
    BookingStatus.checkedIn => StatusTone.brand,
    BookingStatus.checkedOut => StatusTone.neutral,
    BookingStatus.cancelled => StatusTone.neutral,
    BookingStatus.noShow => StatusTone.danger,
    BookingStatus.rejected => StatusTone.danger,
  };
}

/// One room line on a booking — mirrors `BookingDetailResponse`.
class BookingLine {
  const BookingLine({
    required this.bookingDetailId,
    this.productName,
    this.roomNumber,
    this.quantity,
    this.unitPrice,
    this.nights,
    this.lineTotal,
  });

  final String bookingDetailId;
  final String? productName;
  final String? roomNumber;
  final int? quantity;
  final double? unitPrice;
  final int? nights;
  final double? lineTotal;

  factory BookingLine.fromJson(Map<String, dynamic> json) => BookingLine(
    bookingDetailId: json['bookingDetailId'] as String,
    productName: json['productName'] as String?,
    roomNumber: json['roomNumber'] as String?,
    quantity: json['quantity'] as int?,
    unitPrice: (json['unitPrice'] as num?)?.toDouble(),
    nights: json['nights'] as int?,
    lineTotal: (json['lineTotal'] as num?)?.toDouble(),
  );
}

/// Dart mirror of backend `BookingResponse`.
class Booking {
  const Booking({
    required this.bookingId,
    this.bookingCode,
    this.quotationId,
    this.customerId,
    this.customerName,
    this.assignedUserName,
    this.checkInDate,
    this.checkOutDate,
    this.status,
    this.statusRaw,
    this.specialRequests,
    this.rejectionReason,
    this.totalAmount,
    this.lines = const [],
    this.createdAt,
    this.updatedAt,
  });

  final String bookingId;
  final String? bookingCode;
  final String? quotationId;
  final String? customerId;
  final String? customerName;
  final String? assignedUserName;

  /// Calendar days, not instants — the backend field is a Java `LocalDate`.
  final DateTime? checkInDate;
  final DateTime? checkOutDate;

  final BookingStatus? status;
  final String? statusRaw;
  final String? specialRequests;
  final String? rejectionReason;
  final double? totalAmount;
  final List<BookingLine> lines;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  String get displayStatus => status?.label ?? statusRaw ?? '—';
  StatusTone get statusTone => status?.tone ?? StatusTone.neutral;

  /// Nights between check-in and check-out, or null when either is missing.
  int? get nights {
    final inDate = checkInDate;
    final outDate = checkOutDate;
    if (inDate == null || outDate == null) return null;
    return outDate.difference(inDate).inDays;
  }

  factory Booking.fromJson(Map<String, dynamic> json) {
    DateTime? parseDate(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;

    final statusRaw = json['status'] as String?;
    final rawLines = json['details'];

    return Booking(
      bookingId: json['bookingId'] as String,
      bookingCode: json['bookingCode'] as String?,
      quotationId: json['quotationId'] as String?,
      customerId: json['customerId'] as String?,
      customerName: json['customerName'] as String?,
      assignedUserName: json['assignedUserName'] as String?,
      checkInDate: parseDate(json['checkInDate']),
      checkOutDate: parseDate(json['checkOutDate']),
      status: BookingStatus.fromWire(statusRaw),
      statusRaw: statusRaw,
      specialRequests: json['specialRequests'] as String?,
      rejectionReason: json['rejectionReason'] as String?,
      totalAmount: (json['totalAmount'] as num?)?.toDouble(),
      lines: rawLines is List
          ? rawLines
                .map((e) => BookingLine.fromJson(e as Map<String, dynamic>))
                .toList(growable: false)
          : const [],
      createdAt: parseDate(json['createdAt']),
      updatedAt: parseDate(json['updatedAt']),
    );
  }
}

/// Query params for `GET /bookings`.
class BookingFilters {
  const BookingFilters({this.search, this.status});

  final String? search;
  final BookingStatus? status;

  BookingFilters copyWith({
    Object? search = _sentinel,
    Object? status = _sentinel,
  }) {
    return BookingFilters(
      search: search == _sentinel ? this.search : search as String?,
      status: status == _sentinel ? this.status : status as BookingStatus?,
    );
  }

  Map<String, dynamic> toQuery() => {
    if (search != null && search!.isNotEmpty) 'search': search,
    'status': ?status?.wire,
  };

  static const Object _sentinel = Object();
}
