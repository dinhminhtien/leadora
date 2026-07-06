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

/// List ownership scope — only meaningful for a SALES caller; the backend
/// ignores it for MANAGER/ADMIN (they always see all leads).
enum LeadScope {
  assigned('assigned', 'Assigned to me'),
  created('created', 'Created by me');

  const LeadScope(this.wire, this.label);
  final String wire;
  final String label;
}

/// Sort presets exposed by the backend (`sortBy` whitelist: createdAt,
/// fullName, status — status is always pipeline-priority high→low).
enum LeadSort {
  newestFirst('createdAt', 'desc', 'Newest first'),
  oldestFirst('createdAt', 'asc', 'Oldest first'),
  nameAz('fullName', 'asc', 'Name A–Z'),
  statusPriority('status', 'desc', 'Status priority');

  const LeadSort(this.sortBy, this.sortDir, this.label);
  final String sortBy;
  final String sortDir;
  final String label;
}

/// Immutable filter set for the lead list (UC-24.15 Search & Filter).
/// Mirrors every query param `GET /leads` accepts.
class LeadFilters {
  const LeadFilters({
    this.search,
    this.status,
    this.source,
    this.isCorporate,
    this.dateFrom,
    this.dateTo,
    this.sort = LeadSort.newestFirst,
    this.scope = LeadScope.assigned,
  });

  final String? search;
  final LeadStatus? status;
  final String? source;

  /// null = both, true = corporate only, false = individual only.
  final bool? isCorporate;

  /// Inclusive created-date window (calendar days, formatted yyyy-MM-dd).
  final DateTime? dateFrom;
  final DateTime? dateTo;
  final LeadSort sort;
  final LeadScope scope;

  /// How many *advanced* filters differ from their defaults — drives the
  /// badge on the filter button. Search and the status chips are visible
  /// inline, so they are not counted here.
  int get activeAdvancedCount {
    var n = 0;
    if (source != null && source!.trim().isNotEmpty) n++;
    if (isCorporate != null) n++;
    if (dateFrom != null || dateTo != null) n++;
    if (sort != LeadSort.newestFirst) n++;
    if (scope != LeadScope.assigned) n++;
    return n;
  }

  static const _sentinel = Object();

  LeadFilters copyWith({
    Object? search = _sentinel,
    Object? status = _sentinel,
    Object? source = _sentinel,
    Object? isCorporate = _sentinel,
    Object? dateFrom = _sentinel,
    Object? dateTo = _sentinel,
    LeadSort? sort,
    LeadScope? scope,
  }) {
    return LeadFilters(
      search: search == _sentinel ? this.search : search as String?,
      status: status == _sentinel ? this.status : status as LeadStatus?,
      source: source == _sentinel ? this.source : source as String?,
      isCorporate:
          isCorporate == _sentinel ? this.isCorporate : isCorporate as bool?,
      dateFrom: dateFrom == _sentinel ? this.dateFrom : dateFrom as DateTime?,
      dateTo: dateTo == _sentinel ? this.dateTo : dateTo as DateTime?,
      sort: sort ?? this.sort,
      scope: scope ?? this.scope,
    );
  }

  /// Reset the advanced filters, keeping the inline ones (search + status).
  LeadFilters resetAdvanced() => LeadFilters(search: search, status: status);

  static String _day(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-'
      '${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';

  /// Query params for `GET /leads`. Defaults are omitted; the backend fills
  /// them in (sortBy=createdAt desc, scope=assigned).
  Map<String, dynamic> toQuery() {
    return {
      if (search != null && search!.trim().isNotEmpty) 'search': search!.trim(),
      if (status != null) 'status': status!.wire,
      if (source != null && source!.trim().isNotEmpty) 'source': source!.trim(),
      if (isCorporate != null) 'isCorporate': isCorporate,
      if (dateFrom != null) 'dateFrom': _day(dateFrom!),
      if (dateTo != null) 'dateTo': _day(dateTo!),
      if (sort != LeadSort.newestFirst) ...{
        'sortBy': sort.sortBy,
        'sortDir': sort.sortDir,
      },
      if (scope != LeadScope.assigned) 'scope': scope.wire,
    };
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
