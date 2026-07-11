import '../../../shared/widgets/status_chip.dart';

/// Mirrors backend enum `PaymentStatus`.
enum PaymentStatus {
  pending('PENDING', 'Pending'),
  paid('PAID', 'Paid'),
  failed('FAILED', 'Failed'),
  cancelled('CANCELLED', 'Cancelled'),
  expired('EXPIRED', 'Expired');

  const PaymentStatus(this.wire, this.label);
  final String wire;
  final String label;

  /// Null for an unrecognized value — a future backend status must degrade to
  /// its raw string, never be coerced into "Pending".
  static PaymentStatus? fromWire(String? raw) {
    if (raw == null) return null;
    for (final s in PaymentStatus.values) {
      if (s.wire == raw) return s;
    }
    return null;
  }

  /// Only a pending payment can be marked paid or cancelled — every other
  /// state is terminal. Mirrors `UpdatePaymentStatusUseCase`.
  bool get isTerminal => this != PaymentStatus.pending;

  StatusTone get tone => switch (this) {
    PaymentStatus.pending => StatusTone.warning,
    PaymentStatus.paid => StatusTone.success,
    PaymentStatus.failed => StatusTone.danger,
    PaymentStatus.cancelled => StatusTone.neutral,
    PaymentStatus.expired => StatusTone.neutral,
  };
}

/// Mirrors backend enum `PaymentType`.
enum PaymentType {
  deposit('DEPOSIT', 'Deposit'),
  fullPayment('FULL_PAYMENT', 'Full payment');

  const PaymentType(this.wire, this.label);
  final String wire;
  final String label;

  static PaymentType? fromWire(String? raw) {
    if (raw == null) return null;
    for (final t in PaymentType.values) {
      if (t.wire == raw) return t;
    }
    return null;
  }
}

/// Dart mirror of backend `PaymentResponse`.
class Payment {
  const Payment({
    required this.paymentId,
    required this.amount,
    this.bookingId,
    this.bookingCode,
    this.customerId,
    this.customerName,
    this.createdByName,
    this.paymentMethod,
    this.gatewayProvider,
    this.gatewayTransactionId,
    this.paymentType,
    this.status,
    this.statusRaw,
    this.dueDate,
    this.paidAt,
    this.qrCodeUrl,
    this.notes,
    this.createdAt,
    this.updatedAt,
  });

  final String paymentId;
  final double amount;
  final String? bookingId;
  final String? bookingCode;
  final String? customerId;
  final String? customerName;
  final String? createdByName;
  final String? paymentMethod;
  final String? gatewayProvider;
  final String? gatewayTransactionId;
  final PaymentType? paymentType;

  /// Null when the backend sends a status this build does not know.
  final PaymentStatus? status;

  /// The raw wire string, kept so an unknown status can still be displayed.
  final String? statusRaw;

  final DateTime? dueDate;
  final DateTime? paidAt;
  final String? qrCodeUrl;
  final String? notes;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  String get displayStatus => status?.label ?? statusRaw ?? '—';
  StatusTone get statusTone => status?.tone ?? StatusTone.neutral;

  /// A payment past its due date that nobody has paid or closed.
  bool get isOverdue {
    final due = dueDate;
    if (due == null || status != PaymentStatus.pending) return false;
    return due.isBefore(DateTime.now());
  }

  factory Payment.fromJson(Map<String, dynamic> json) {
    DateTime? parseDate(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;

    final statusRaw = json['status'] as String?;

    return Payment(
      paymentId: json['paymentId'] as String,
      amount: (json['amount'] as num?)?.toDouble() ?? 0,
      bookingId: json['bookingId'] as String?,
      bookingCode: json['bookingCode'] as String?,
      customerId: json['customerId'] as String?,
      customerName: json['customerName'] as String?,
      createdByName: json['createdByName'] as String?,
      paymentMethod: json['paymentMethod'] as String?,
      gatewayProvider: json['gatewayProvider'] as String?,
      gatewayTransactionId: json['gatewayTransactionId'] as String?,
      paymentType: PaymentType.fromWire(json['paymentType'] as String?),
      status: PaymentStatus.fromWire(statusRaw),
      statusRaw: statusRaw,
      dueDate: parseDate(json['dueDate']),
      paidAt: parseDate(json['paidAt']),
      qrCodeUrl: json['qrCodeUrl'] as String?,
      notes: json['notes'] as String?,
      createdAt: parseDate(json['createdAt']),
      updatedAt: parseDate(json['updatedAt']),
    );
  }
}

/// How the payment list is ordered. `sortBy` maps to a backend column name.
enum PaymentSort {
  newest('createdAt', 'desc', 'Newest'),
  oldest('createdAt', 'asc', 'Oldest'),
  dueSoon('dueDate', 'asc', 'Due soonest'),
  amountHigh('amount', 'desc', 'Amount: high → low'),
  amountLow('amount', 'asc', 'Amount: low → high');

  const PaymentSort(this.sortBy, this.sortDir, this.label);
  final String sortBy;
  final String sortDir;
  final String label;
}

/// Query params for `GET /payments`.
class PaymentFilters {
  const PaymentFilters({
    this.search,
    this.status,
    this.paymentType,
    this.sort = PaymentSort.newest,
  });

  final String? search;
  final PaymentStatus? status;
  final PaymentType? paymentType;
  final PaymentSort sort;

  int get activeCount => (status != null ? 1 : 0) + (paymentType != null ? 1 : 0);

  PaymentFilters copyWith({
    Object? search = _sentinel,
    Object? status = _sentinel,
    Object? paymentType = _sentinel,
    PaymentSort? sort,
  }) {
    return PaymentFilters(
      search: search == _sentinel ? this.search : search as String?,
      status: status == _sentinel ? this.status : status as PaymentStatus?,
      paymentType: paymentType == _sentinel
          ? this.paymentType
          : paymentType as PaymentType?,
      sort: sort ?? this.sort,
    );
  }

  Map<String, dynamic> toQuery() => {
    if (search != null && search!.isNotEmpty) 'search': search,
    'status': ?status?.wire,
    'paymentType': ?paymentType?.wire,
    'sortBy': sort.sortBy,
    'sortDir': sort.sortDir,
  };

  static const Object _sentinel = Object();
}

/// Body for `POST /payments` (backend `GeneratePaymentRequest`).
class GeneratePaymentPayload {
  const GeneratePaymentPayload({
    required this.bookingId,
    required this.amount,
    required this.paymentType,
    this.paymentMethod,
    this.notes,
    this.dueDate,
  });

  final String bookingId;
  final double amount;
  final PaymentType paymentType;
  final String? paymentMethod;
  final String? notes;
  final DateTime? dueDate;

  /// `dueDate` maps to a Java `LocalDate` — a plain calendar day, never an
  /// instant, or a late-evening UTC+7 date rolls back a day server-side.
  static String _localDate(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-'
      '${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';

  Map<String, dynamic> toJson() => {
    'bookingId': bookingId,
    'amount': amount,
    'paymentType': paymentType.wire,
    'paymentMethod': ?paymentMethod,
    'notes': ?notes,
    if (dueDate != null) 'dueDate': _localDate(dueDate!),
  };
}
