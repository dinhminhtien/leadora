import '../../../shared/widgets/status_chip.dart';

/// Lead lifecycle — mirrors backend `LeadStatus`.
enum LeadStatus {
  neww('NEW'),
  contacted('CONTACTED'),
  qualified('QUALIFIED'),
  converted('CONVERTED'),
  lost('LOST');

  const LeadStatus(this.wire);
  final String wire;

  static LeadStatus fromWire(String? raw) => LeadStatus.values.firstWhere(
        (s) => s.wire == raw,
        orElse: () => LeadStatus.neww,
      );

  StatusTone get tone => switch (this) {
        LeadStatus.neww => StatusTone.info,
        LeadStatus.contacted => StatusTone.brand,
        LeadStatus.qualified => StatusTone.warning,
        LeadStatus.converted => StatusTone.success,
        LeadStatus.lost => StatusTone.danger,
      };

  /// Terminal states cannot change (CONVERTED only via the conversion flow).
  bool get isTerminal => this == LeadStatus.converted || this == LeadStatus.lost;

  /// Valid next statuses from [this], mirroring the backend transition rules:
  /// leads advance one stage at a time (NEW → CONTACTED → QUALIFIED) and any
  /// active lead can be marked LOST. CONVERTED is reachable only via conversion.
  List<LeadStatus> get allowedTransitions => switch (this) {
        LeadStatus.neww => const [LeadStatus.contacted, LeadStatus.lost],
        LeadStatus.contacted => const [LeadStatus.qualified, LeadStatus.lost],
        LeadStatus.qualified => const [LeadStatus.lost],
        LeadStatus.converted => const [],
        LeadStatus.lost => const [],
      };
}

/// Dart mirror of backend `LeadResponse`.
class Lead {
  const Lead({
    required this.leadId,
    required this.fullName,
    required this.status,
    this.email,
    this.phone,
    this.companyName,
    this.address,
    this.isCorporate = false,
    this.source,
    this.notes,
    this.convertedAt,
    this.customerId,
    this.assignedUserId,
    this.assignedUserName,
    this.createdByName,
    this.createdAt,
    this.updatedAt,
  });

  final String leadId;
  final String fullName;
  final LeadStatus status;
  final String? email;
  final String? phone;
  final String? companyName;
  final String? address;
  final bool isCorporate;
  final String? source;
  final String? notes;
  final DateTime? convertedAt;
  final String? customerId;
  final String? assignedUserId;
  final String? assignedUserName;
  final String? createdByName;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  bool get isConverted => status == LeadStatus.converted;

  factory Lead.fromJson(Map<String, dynamic> json) {
    DateTime? parse(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;
    return Lead(
      leadId: json['leadId'] as String,
      fullName: json['fullName'] as String? ?? 'Unnamed lead',
      status: LeadStatus.fromWire(json['status'] as String?),
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      companyName: json['companyName'] as String?,
      address: json['address'] as String?,
      isCorporate: json['isCorporate'] as bool? ?? false,
      source: json['source'] as String?,
      notes: json['notes'] as String?,
      convertedAt: parse(json['convertedAt']),
      customerId: json['customerId'] as String?,
      assignedUserId: json['assignedUserId'] as String?,
      assignedUserName: json['assignedUserName'] as String?,
      createdByName: json['createdByName'] as String?,
      createdAt: parse(json['createdAt']),
      updatedAt: parse(json['updatedAt']),
    );
  }
}

/// Payload for UC-24.2 Create Quick Lead. Only non-null fields are sent.
class CreateLeadPayload {
  const CreateLeadPayload({
    required this.fullName,
    this.email,
    this.phone,
    this.companyName,
    this.address,
    this.isCorporate,
    this.source,
    this.notes,
  });

  final String fullName;
  final String? email;
  final String? phone;
  final String? companyName;
  final String? address;
  final bool? isCorporate;
  final String? source;
  final String? notes;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{'fullName': fullName};
    void put(String k, Object? v) {
      if (v != null && !(v is String && v.trim().isEmpty)) map[k] = v;
    }

    put('email', email);
    put('phone', phone);
    put('companyName', companyName);
    put('address', address);
    put('isCorporate', isCorporate);
    put('source', source);
    put('notes', notes);
    return map;
  }
}
