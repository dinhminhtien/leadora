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
  bool get isTerminal =>
      this == LeadStatus.converted || this == LeadStatus.lost;

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
    this.interestedService,
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
  final String? interestedService;
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
      interestedService: json['interestedService'] as String?,
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

  /// Inclusive created-date window, picked as *local* calendar days. Sent to
  /// the backend as UTC instants (see [toQuery]) so the window matches the
  /// user's timezone instead of UTC calendar days.
  final DateTime? dateFrom;
  final DateTime? dateTo;
  final LeadSort sort;
  final LeadScope scope;

  /// How many *advanced* filters differ from their defaults — drives the
  /// badge on the filter button. Search, the status chips and the scope tabs
  /// are visible inline, so they are not counted here: a badge that counts a
  /// filter the user can already see reads as a second, hidden one.
  int get activeAdvancedCount {
    var n = 0;
    if (source != null && source!.trim().isNotEmpty) n++;
    if (isCorporate != null) n++;
    if (dateFrom != null || dateTo != null) n++;
    if (sort != LeadSort.newestFirst) n++;
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
      isCorporate: isCorporate == _sentinel
          ? this.isCorporate
          : isCorporate as bool?,
      dateFrom: dateFrom == _sentinel ? this.dateFrom : dateFrom as DateTime?,
      dateTo: dateTo == _sentinel ? this.dateTo : dateTo as DateTime?,
      sort: sort ?? this.sort,
      scope: scope ?? this.scope,
    );
  }

  /// Reset the advanced filters, keeping the inline ones — search, status and
  /// scope. Resetting the sheet must not move the user out of the list they are
  /// looking at: the tab would spring back to "Assigned to me" while they were
  /// clearing a date range.
  LeadFilters resetAdvanced() =>
      LeadFilters(search: search, status: status, scope: scope);

  /// Value equality so the controller can tell a filter change from a filter
  /// *re-selection*. Tapping the already-selected status chip, or Apply on a
  /// sheet nothing was changed in, otherwise cost a full round trip and a
  /// skeleton flash to redraw the identical list.
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is LeadFilters &&
          other.search == search &&
          other.status == status &&
          other.source == source &&
          other.isCorporate == isCorporate &&
          other.dateFrom == dateFrom &&
          other.dateTo == dateTo &&
          other.sort == sort &&
          other.scope == scope;

  @override
  int get hashCode => Object.hash(
    search,
    status,
    source,
    isCorporate,
    dateFrom,
    dateTo,
    sort,
    scope,
  );

  /// Local midnight of [d]'s calendar day, as a UTC ISO-8601 instant
  /// (`…Z` suffix). Deliberately *not* an offset form like `+07:00`: a raw
  /// `+` in a query string decodes to a space server-side and would make the
  /// backend silently drop the bound.
  static String utcStartOfLocalDay(DateTime d) =>
      DateTime(d.year, d.month, d.day).toUtc().toIso8601String();

  /// End of [d]'s local calendar day (23:59:59.999) as a UTC ISO-8601 instant.
  static String utcEndOfLocalDay(DateTime d) => DateTime(
    d.year,
    d.month,
    d.day,
    23,
    59,
    59,
    999,
  ).toUtc().toIso8601String();

  /// Query params for `GET /leads`. Defaults are omitted; the backend fills
  /// them in (sortBy=createdAt desc, scope=assigned).
  Map<String, dynamic> toQuery() {
    return {
      if (search != null && search!.trim().isNotEmpty) 'search': search!.trim(),
      if (status != null) 'status': status!.wire,
      if (source != null && source!.trim().isNotEmpty) 'source': source!.trim(),
      if (isCorporate != null) 'isCorporate': isCorporate,
      if (dateFrom != null) 'dateFrom': utcStartOfLocalDay(dateFrom!),
      if (dateTo != null) 'dateTo': utcEndOfLocalDay(dateTo!),
      if (sort != LeadSort.newestFirst) ...{
        'sortBy': sort.sortBy,
        'sortDir': sort.sortDir,
      },
      if (scope != LeadScope.assigned) 'scope': scope.wire,
    };
  }
}

/// Why a lead cannot advance right now, or null when it can.
///
/// Mirrors the two refusals `UpdateLeadUseCase` issues for a forward move, so
/// the UI can grey the control out and say why instead of letting the user pick
/// a status and read a 422 back:
///
/// * `LEAD_UNASSIGNED` — a lead nobody owns is a draft and cannot move at all;
/// * `LEAD_NOT_READY_FOR_FOLLOW_UP` — BR-05, enforced by
///   `assertQualifyingDetailsPresent` on the *resulting* record, which needs a
///   source and an interested service before a lead may sit in CONTACTED or
///   QUALIFIED.
///
/// Marking a lead LOST is never blocked by BR-05 — a junk lead has to be
/// closable immediately rather than after filling in details nobody will use —
/// but it *is* blocked by the assignment rule, which guards every transition.
class LeadStatusGate {
  const LeadStatusGate({required this.unassigned, required this.missing});

  /// BR-06 — no owner, so no transition of any kind.
  final bool unassigned;

  /// BR-05 fields still absent, in the wording the UI shows.
  final List<String> missing;

  factory LeadStatusGate.of(Lead lead) => LeadStatusGate(
    unassigned: lead.assignedUserId == null,
    missing: [
      if ((lead.source ?? '').trim().isEmpty) 'a source',
      if ((lead.interestedService ?? '').trim().isEmpty)
        'an interested service',
    ],
  );

  /// True when moving *forward* (to CONTACTED or QUALIFIED) would be refused.
  bool get forwardBlocked => unassigned || missing.isNotEmpty;

  /// True when even marking the lead LOST would be refused.
  bool get allBlocked => unassigned;

  /// The sentence to show under a disabled control, or null when nothing is in
  /// the way. Phrased to say what to do, not merely what is wrong.
  String? get reason {
    if (unassigned) {
      return 'This lead has no owner yet. A manager must assign it to a sales '
          'rep before its status can change.';
    }
    if (missing.isEmpty) return null;
    return 'To move this lead forward it needs ${_list(missing)}. '
        'Edit the lead to fill that in — you can still mark it Lost.';
  }

  static String _list(List<String> parts) => parts.length == 1
      ? parts.single
      : '${parts.sublist(0, parts.length - 1).join(', ')} and ${parts.last}';
}

/// Client-side mirror of the server rules a lead write must satisfy — the
/// column widths in `LeadFieldLimits`, the phone pattern, and the
/// phone-or-email requirement from `LeadContactPolicy.assertReachable`.
///
/// These are duplicated on purpose, not as a substitute for the server checks:
/// without them a too-long name or a mistyped phone is only refused after a
/// round trip, and the refusal arrives as `VALIDATION_ERROR` /
/// "Validation failed for request." with the useful part buried in a field map.
/// The messages are copied verbatim from the backend so the wording does not
/// change depending on which side caught the problem.
///
/// The limits track `leads` columns, which the backend runs against with
/// `ddl-auto: validate` — if one is ever widened, it changes here too.
class LeadFieldRules {
  const LeadFieldRules._();

  /// `leads.full_name` — VARCHAR(40) NOT NULL.
  static const int fullName = 40;

  /// `leads.email` — VARCHAR(40).
  static const int email = 40;

  /// `leads.company_name` — VARCHAR(40).
  static const int companyName = 40;

  /// `leads.source` — VARCHAR(40).
  static const int source = 40;

  /// `leads.interested_service` — VARCHAR(100).
  static const int interestedService = 100;

  /// 10 or 11 digits — mirrors `LeadFieldLimits.PHONE_PATTERN` (`^$|^\d{10,11}$`).
  /// Blank is handled by the caller and means "no phone", which is why the empty
  /// branch is not in the expression.
  ///
  /// Deliberately not the Vietnamese-mobile shape it used to be
  /// (`^(0[35789])\d{8}$`): that rejected landlines, eleven-digit numbers and
  /// anything a guest gives from abroad — none of which are wrong, they are just
  /// not mobile numbers. The cost is that `1234567890` now passes; the field is
  /// checked for shape, and whether the number reaches anyone is something only
  /// a call establishes.
  static final RegExp phonePattern = RegExp(r'^\d{10,11}$');

  static final RegExp _emailPattern = RegExp(
    r'^[\w.\-+]+@([\w\-]+\.)+[\w\-]{2,}$',
  );

  static String? validateFullName(String? v) {
    final value = v?.trim() ?? '';
    if (value.isEmpty) return 'Full name is required';
    if (value.length > fullName) {
      return 'Full name must be at most $fullName characters';
    }
    return null;
  }

  static String? validateEmail(String? v) {
    final value = v?.trim() ?? '';
    if (value.isEmpty) return null; // optional — see [validateReachable]
    if (!_emailPattern.hasMatch(value)) return 'Invalid email format';
    if (value.length > email) {
      return 'Email must be at most $email characters';
    }
    return null;
  }

  /// Strips the separators people type into a phone number and folds the
  /// international prefix onto the national one, because `+84 912 345 678` and
  /// `0912345678` are the same number and only the second one is storable.
  /// Rejecting the first as "not a valid Vietnamese number" would be true only
  /// of the formatting.
  static String normalizePhone(String? v) {
    final compact = (v ?? '').replaceAll(RegExp(r'[\s.\-()]'), '');
    return compact.startsWith('+84') ? '0${compact.substring(3)}' : compact;
  }

  static String? validatePhone(String? v) {
    final value = normalizePhone(v);
    if (value.isEmpty) return null; // optional — see [validateReachable]
    return phonePattern.hasMatch(value)
        ? null
        : 'Phone number must be 10 or 11 digits';
  }

  /// [isCorporate] leads must name their company (the server checks the same
  /// thing on the record it is about to save).
  static String? validateCompanyName(String? v, {required bool isCorporate}) {
    final value = v?.trim() ?? '';
    if (isCorporate && value.isEmpty) {
      return 'Company name is required for an organization lead';
    }
    if (value.length > companyName) {
      return 'Company name must be at most $companyName characters';
    }
    return null;
  }

  static String? validateSource(String? v) {
    final value = v?.trim() ?? '';
    return value.length > source
        ? 'Source must be at most $source characters'
        : null;
  }

  static String? validateInterestedService(String? v) {
    final value = v?.trim() ?? '';
    return value.length > interestedService
        ? 'Interested service must be at most $interestedService characters'
        : null;
  }

  /// A lead needs at least one way to reach the person — the client half of
  /// `LeadContactPolicy.assertReachable`. Cross-field, so it cannot live on
  /// either input's own validator; returns the message to show against both.
  static String? validateReachable({String? email, String? phone}) {
    final hasEmail = (email ?? '').trim().isNotEmpty;
    final hasPhone = (phone ?? '').trim().isNotEmpty;
    return hasEmail || hasPhone
        ? null
        : 'A lead needs a phone number or an email address so it can be '
              'followed up.';
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
    this.interestedService,
    this.notes,
  });

  final String fullName;
  final String? email;
  final String? phone;
  final String? companyName;
  final String? address;
  final bool? isCorporate;
  final String? source;
  final String? interestedService;
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
    put('interestedService', interestedService);
    put('notes', notes);
    return map;
  }
}

/// Payload for UC-8.4 Update Lead — PUT /leads/{id}.
///
/// **Null and blank mean different things here**, and the difference is the
/// whole reason this is not just [CreateLeadPayload]. The server reads a null
/// field as "leave it alone" and a blank one as "clear it" — so an edit form
/// must send every field it shows, blanks included, or clearing a wrong phone
/// number would be indistinguishable from not touching it.
///
/// [status] and [assignedUserId] are deliberately absent: on mobile a sales rep
/// edits the lead's details, while advancing it is the status flow's job (and
/// assigning it is a manager's).
class UpdateLeadPayload {
  const UpdateLeadPayload({
    required this.fullName,
    required this.email,
    required this.phone,
    required this.companyName,
    required this.address,
    required this.isCorporate,
    required this.source,
    required this.interestedService,
    required this.notes,
  });

  final String fullName;
  final String email;
  final String phone;
  final String companyName;
  final String address;
  final bool isCorporate;

  /// One of [kLeadSourceOptions], or null when the rep has not said.
  final String? source;
  final String interestedService;
  final String notes;

  /// Builds the lead this payload would produce, for checking rules that read
  /// the *resulting* record — BR-05's follow-up fields among them — before the
  /// server has to.
  Lead applyTo(Lead lead) => Lead(
    leadId: lead.leadId,
    fullName: fullName,
    status: lead.status,
    email: email.trim().isEmpty ? null : email.trim(),
    phone: phone.trim().isEmpty ? null : phone.trim(),
    companyName: companyName.trim().isEmpty ? null : companyName.trim(),
    address: address.trim().isEmpty ? null : address.trim(),
    isCorporate: isCorporate,
    source: source,
    interestedService: interestedService.trim().isEmpty
        ? null
        : interestedService.trim(),
    notes: notes.trim().isEmpty ? null : notes.trim(),
    convertedAt: lead.convertedAt,
    customerId: lead.customerId,
    assignedUserId: lead.assignedUserId,
    assignedUserName: lead.assignedUserName,
    createdByName: lead.createdByName,
    createdAt: lead.createdAt,
    updatedAt: lead.updatedAt,
  );

  /// Every field is sent, blanks included — see the class note. `source` is the
  /// exception: it is a closed list, and null there means "not specified".
  Map<String, dynamic> toJson() => {
    'fullName': fullName.trim(),
    'email': email.trim(),
    'phone': phone.trim(),
    'companyName': companyName.trim(),
    'address': address.trim(),
    'isCorporate': isCorporate,
    'source': source ?? '',
    'interestedService': interestedService.trim(),
    'notes': notes.trim(),
  };
}

/// Payload for UC-8.5 Convert Lead to Customer — POST /leads/{id}/convert.
///
/// The identity fields (name, email, phone, company, address) used to be sent here and are not
/// any more: the server builds the customer from the lead itself, so sending them was at best
/// redundant and at worst a way to create a customer that did not match the lead it came from.
/// [reason] carries a Sales Manager's approval when converting a lead that is not yet
/// QUALIFIED (BR-07).
class ConvertLeadPayload {
  const ConvertLeadPayload({this.customerType, this.taxCode, this.reason});

  /// 'INDIVIDUAL' or 'CORPORATE' — mirrors the backend `CustomerType` enum.
  /// Omit to inherit the lead's `isCorporate` flag.
  final String? customerType;

  /// The one customer field with no counterpart on the lead.
  final String? taxCode;
  final String? reason;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{};
    void put(String k, String? v) {
      if (v != null && v.trim().isNotEmpty) map[k] = v.trim();
    }

    put('customerType', customerType);
    put('taxCode', taxCode);
    put('reason', reason);
    return map;
  }
}

/// Payload for UC-8.5 exception E6 — POST /leads/{id}/link-customer.
/// Attaches the lead to a customer profile that already exists instead of creating a second one.
class LinkLeadToCustomerPayload {
  const LinkLeadToCustomerPayload({required this.customerId, this.reason});

  final String customerId;
  final String? reason;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{'customerId': customerId};
    if (reason != null && reason!.trim().isNotEmpty) map['reason'] = reason!.trim();
    return map;
  }
}
