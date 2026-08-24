import 'package:flutter/material.dart';

import '../../../shared/widgets/status_chip.dart';

/// Customer classification — mirrors backend `CustomerType`.
enum CustomerType {
  individual('INDIVIDUAL', 'Individual'),
  corporate('CORPORATE', 'Corporate');

  const CustomerType(this.wire, this.label);
  final String wire;
  final String label;

  static CustomerType fromWire(String? raw) => CustomerType.values.firstWhere(
    (t) => t.wire == raw,
    orElse: () => CustomerType.individual,
  );

  bool get isCorporate => this == CustomerType.corporate;

  StatusTone get tone =>
      this == CustomerType.corporate ? StatusTone.brand : StatusTone.info;

  IconData get icon => this == CustomerType.corporate
      ? Icons.apartment_rounded
      : Icons.person_rounded;
}

/// Account state — mirrors backend `CustomerStatus`.
enum CustomerStatus {
  active('ACTIVE'),
  inactive('INACTIVE');

  const CustomerStatus(this.wire);
  final String wire;

  static CustomerStatus fromWire(String? raw) => CustomerStatus.values
      .firstWhere((s) => s.wire == raw, orElse: () => CustomerStatus.active);

  StatusTone get tone =>
      this == CustomerStatus.active ? StatusTone.success : StatusTone.neutral;
}

/// Dart mirror of backend `CustomerResponse`.
class Customer {
  const Customer({
    required this.customerId,
    required this.customerType,
    required this.fullName,
    required this.status,
    this.email,
    this.phone,
    this.companyName,
    this.taxCode,
    this.address,
    this.assignedUserId,
    this.assignedUserName,
    this.createdById,
    this.createdByName,
    this.leadId,
    this.createdAt,
    this.updatedAt,
  });

  final String customerId;
  final CustomerType customerType;
  final String fullName;
  final CustomerStatus status;
  final String? email;
  final String? phone;
  final String? companyName;
  final String? taxCode;
  final String? address;
  final String? assignedUserId;
  final String? assignedUserName;
  final String? createdById;
  final String? createdByName;
  final String? leadId;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  bool get isCorporate => customerType.isCorporate;

  static DateTime? _parse(Object? v) =>
      v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;

  factory Customer.fromJson(Map<String, dynamic> json) {
    return Customer(
      customerId: json['customerId'] as String,
      customerType: CustomerType.fromWire(json['customerType'] as String?),
      fullName: json['fullName'] as String? ?? 'Unnamed customer',
      status: CustomerStatus.fromWire(json['status'] as String?),
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      companyName: json['companyName'] as String?,
      taxCode: json['taxCode'] as String?,
      address: json['address'] as String?,
      assignedUserId: json['assignedUserId'] as String?,
      assignedUserName: json['assignedUserName'] as String?,
      createdById: json['createdById'] as String?,
      createdByName: json['createdByName'] as String?,
      leadId: json['leadId'] as String?,
      createdAt: _parse(json['createdAt']),
      updatedAt: _parse(json['updatedAt']),
    );
  }
}

/// Global customer counts — mirrors backend `GetCustomerStatsUseCase.CustomerStats`.
class CustomerStats {
  const CustomerStats({
    this.total = 0,
    this.active = 0,
    this.inactive = 0,
    this.individual = 0,
    this.corporate = 0,
  });

  final int total;
  final int active;
  final int inactive;
  final int individual;
  final int corporate;

  factory CustomerStats.fromJson(Map<String, dynamic> json) {
    int asInt(Object? v) => (v as num?)?.toInt() ?? 0;
    return CustomerStats(
      total: asInt(json['total']),
      active: asInt(json['active']),
      inactive: asInt(json['inactive']),
      individual: asInt(json['individual']),
      corporate: asInt(json['corporate']),
    );
  }
}

/// One entry in a customer's activity history — mirrors backend
/// `CustomerHistoryItem` (deals, bookings, quotations).
enum CustomerHistoryType {
  deal('DEAL', 'Deal', Icons.handshake_rounded, StatusTone.brand),
  booking(
    'BOOKING',
    'Booking',
    Icons.event_available_rounded,
    StatusTone.success,
  ),
  quotation(
    'QUOTATION',
    'Quotation',
    Icons.receipt_long_rounded,
    StatusTone.warning,
  );

  const CustomerHistoryType(this.wire, this.label, this.icon, this.tone);
  final String wire;
  final String label;
  final IconData icon;
  final StatusTone tone;

  static CustomerHistoryType fromWire(String? raw) => CustomerHistoryType.values
      .firstWhere((t) => t.wire == raw, orElse: () => CustomerHistoryType.deal);
}

class CustomerHistoryItem {
  const CustomerHistoryItem({
    required this.type,
    required this.id,
    required this.title,
    this.status,
    this.stage,
    this.amount,
    this.checkIn,
    this.checkOut,
    this.expectedClose,
    this.createdAt,
    this.notes,
  });

  final CustomerHistoryType type;
  final String id;
  final String title;
  final String? status;
  final String? stage;
  final num? amount;
  final String? checkIn;
  final String? checkOut;
  final String? expectedClose;
  final DateTime? createdAt;
  final String? notes;

  factory CustomerHistoryItem.fromJson(Map<String, dynamic> json) {
    return CustomerHistoryItem(
      type: CustomerHistoryType.fromWire(json['type'] as String?),
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '—',
      status: json['status'] as String?,
      stage: json['stage'] as String?,
      amount: json['amount'] as num?,
      checkIn: json['checkIn'] as String?,
      checkOut: json['checkOut'] as String?,
      expectedClose: json['expectedClose'] as String?,
      createdAt:
          json['createdAt'] is String &&
              (json['createdAt'] as String).isNotEmpty
          ? DateTime.tryParse(json['createdAt'] as String)
          : null,
      notes: json['notes'] as String?,
    );
  }
}

/// Body for `POST /customers` — mirrors backend `CreateCustomerRequest`.
class CreateCustomerPayload {
  const CreateCustomerPayload({
    required this.fullName,
    required this.customerType,
    this.email,
    this.phone,
    this.companyName,
    this.taxCode,
    this.address,
    this.assignedUserId,
  });

  final String fullName;
  final CustomerType customerType;
  final String? email;
  final String? phone;
  final String? companyName;
  final String? taxCode;
  final String? address;
  final String? assignedUserId;

  Map<String, dynamic> toJson() {
    String? clean(String? v) =>
        (v == null || v.trim().isEmpty) ? null : v.trim();
    return {
      'fullName': fullName.trim(),
      'customerType': customerType.wire,
      'email': ?clean(email),
      'phone': ?clean(phone),
      'companyName': ?clean(companyName),
      'taxCode': ?clean(taxCode),
      'address': ?clean(address),
      'assignedUserId': ?assignedUserId,
    };
  }
}

/// Body for `PUT /customers/{id}` — mirrors backend `UpdateCustomerRequest`.
/// The backend requires `fullName`; the rest are optional.
class UpdateCustomerPayload {
  const UpdateCustomerPayload({
    required this.fullName,
    this.customerType,
    this.email,
    this.phone,
    this.companyName,
    this.taxCode,
    this.address,
    this.status,
    this.assignedUserId,
  });

  final String fullName;
  final CustomerType? customerType;
  final String? email;
  final String? phone;
  final String? companyName;
  final String? taxCode;
  final String? address;
  final CustomerStatus? status;
  final String? assignedUserId;

  Map<String, dynamic> toJson() {
    String? clean(String? v) =>
        (v == null || v.trim().isEmpty) ? null : v.trim();
    return {
      'fullName': fullName.trim(),
      'customerType': ?customerType?.wire,
      'email': ?clean(email),
      'phone': ?clean(phone),
      'companyName': ?clean(companyName),
      'taxCode': ?clean(taxCode),
      'address': ?clean(address),
      'status': ?status?.wire,
      'assignedUserId': ?assignedUserId,
    };
  }
}

/// Validation rules and limits for Customer profile forms.
class CustomerFieldRules {
  const CustomerFieldRules._();

  static const int fullNameMax = 40;
  static const int emailMax = 40;
  static const int companyNameMax = 40;

  static final RegExp namePattern = RegExp(r"^[\p{L}\s'.-]{2,40}$", unicode: true);
  static final RegExp phonePattern = RegExp(r'^(0|\+84|84)?[0-9]{9,10}$|^\d{10,11}$');
  static final RegExp emailPattern = RegExp(
    r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$',
  );

  static String? validateFullName(String? v) {
    final value = v?.trim() ?? '';
    if (value.isEmpty) return 'Full name is required';
    if (value.length < 2 || value.length > fullNameMax) {
      return 'Full name must be between 2 and $fullNameMax characters';
    }
    if (!namePattern.hasMatch(value)) {
      return 'Full name can only contain letters, spaces, dots, hyphens, and apostrophes';
    }
    return null;
  }

  static String? validateEmail(String? v, {bool required = true}) {
    final value = v?.trim() ?? '';
    if (value.isEmpty) {
      return required ? 'Email is required' : null;
    }
    if (value.length > emailMax) {
      return 'Email must be at most $emailMax characters';
    }
    if (!emailPattern.hasMatch(value)) {
      return 'Please enter a valid email address';
    }
    return null;
  }

  static String normalizePhone(String? v) {
    final compact = (v ?? '').replaceAll(RegExp(r'[\s.\-()]'), '');
    return compact.startsWith('+84') ? '0${compact.substring(3)}' : compact;
  }

  static String? validatePhone(String? v, {bool required = true}) {
    final value = normalizePhone(v);
    if (value.isEmpty) {
      return required ? 'Phone number is required' : null;
    }
    return phonePattern.hasMatch(value)
        ? null
        : 'Phone number must be 10 or 11 digits';
  }

  static String? validateCompanyName(String? v, {required bool isCorporate}) {
    final value = v?.trim() ?? '';
    if (isCorporate && value.isEmpty) {
      return 'Company name is required for a corporate customer';
    }
    if (value.length > companyNameMax) {
      return 'Company name must be at most $companyNameMax characters';
    }
    return null;
  }
}
