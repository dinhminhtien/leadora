import '../../../shared/widgets/status_chip.dart';

/// Mirrors backend `ReminderStatus`.
enum ReminderStatus {
  pending('PENDING', 'Pending'),
  done('DONE', 'Done'),
  overdue('OVERDUE', 'Overdue'),
  cancelled('CANCELLED', 'Cancelled');

  const ReminderStatus(this.wire, this.label);
  final String wire;
  final String label;

  static ReminderStatus fromWire(String? raw) => ReminderStatus.values
      .firstWhere((s) => s.wire == raw, orElse: () => ReminderStatus.pending);

  StatusTone get tone => switch (this) {
    ReminderStatus.pending => StatusTone.info,
    ReminderStatus.done => StatusTone.success,
    ReminderStatus.overdue => StatusTone.danger,
    ReminderStatus.cancelled => StatusTone.neutral,
  };
}

/// Mirrors backend `ReminderPriority`.
enum ReminderPriority {
  high('HIGH', 'High'),
  medium('MEDIUM', 'Medium'),
  low('LOW', 'Low');

  const ReminderPriority(this.wire, this.label);
  final String wire;
  final String label;

  static ReminderPriority fromWire(String? raw) => ReminderPriority.values
      .firstWhere((p) => p.wire == raw, orElse: () => ReminderPriority.medium);

  StatusTone get tone => switch (this) {
    ReminderPriority.high => StatusTone.danger,
    ReminderPriority.medium => StatusTone.info,
    ReminderPriority.low => StatusTone.neutral,
  };
}

/// The kind of record a manually created reminder is linked to — mirrors the
/// backend's `CreateReminderRequest.relatedEntity` pattern
/// (`QUOTATION|LEAD|BOOKING|DEPOSIT|DEAL`). Required to create a reminder;
/// fixed for the lifetime of the reminder (`UpdateReminderRequest` has no
/// field to change it).
enum ReminderRelatedEntityType {
  lead('LEAD', 'Lead'),
  deal('DEAL', 'Deal'),
  quotation('QUOTATION', 'Quotation'),
  booking('BOOKING', 'Booking'),
  deposit('DEPOSIT', 'Deposit / Payment');

  const ReminderRelatedEntityType(this.wire, this.label);
  final String wire;
  final String label;

  static ReminderRelatedEntityType? fromWire(String? raw) {
    for (final t in ReminderRelatedEntityType.values) {
      if (t.wire == raw) return t;
    }
    return null;
  }
}

/// A linked record picked in the create-reminder entity picker.
class ReminderRelatedEntityLink {
  const ReminderRelatedEntityLink({
    required this.type,
    required this.id,
    required this.label,
    this.subtitle,
  });

  final ReminderRelatedEntityType type;
  final String id;
  final String label;
  final String? subtitle;
}

/// How the reminder list is ordered — mirrors `GetRemindersUseCase`'s
/// `sortBy` param: `"priority"` for HIGH→LOW, anything else (including
/// omitted) for `remindAt` ascending.
enum ReminderSort {
  dueDate(null, 'Due date'),
  priority('priority', 'Priority');

  const ReminderSort(this.wire, this.label);
  final String? wire;
  final String label;
}

/// Server-side filters for `GET /reminders` — search, status, date range and
/// sort all round-trip to `GetRemindersUseCase`. [allStaff], when true, omits
/// `userId` entirely so a Manager/Admin sees every staff member's reminders
/// (server enforces that Sales Staff can never actually get this).
class ReminderFilters {
  const ReminderFilters({
    this.search,
    this.status,
    this.remindFrom,
    this.remindTo,
    this.sortBy = ReminderSort.dueDate,
    this.allStaff = false,
  });

  final String? search;
  final ReminderStatus? status;
  final DateTime? remindFrom;
  final DateTime? remindTo;
  final ReminderSort sortBy;
  final bool allStaff;

  int get activeCount =>
      (status != null ? 1 : 0) +
      (remindFrom != null || remindTo != null ? 1 : 0);

  ReminderFilters copyWith({
    Object? search = _sentinel,
    Object? status = _sentinel,
    Object? remindFrom = _sentinel,
    Object? remindTo = _sentinel,
    ReminderSort? sortBy,
    bool? allStaff,
  }) {
    return ReminderFilters(
      search: search == _sentinel ? this.search : search as String?,
      status: status == _sentinel ? this.status : status as ReminderStatus?,
      remindFrom: remindFrom == _sentinel
          ? this.remindFrom
          : remindFrom as DateTime?,
      remindTo: remindTo == _sentinel ? this.remindTo : remindTo as DateTime?,
      sortBy: sortBy ?? this.sortBy,
      allStaff: allStaff ?? this.allStaff,
    );
  }

  static const Object _sentinel = Object();
}

/// Dart mirror of backend `ReminderResponse` — UC-16.1–16.5 on mobile: view,
/// dismiss, create, update and search/filter "my reminders" (or, for
/// Manager/Admin, everyone's).
class Reminder {
  const Reminder({
    required this.reminderId,
    required this.title,
    required this.priority,
    required this.status,
    this.description,
    this.remindAt,
    this.relatedEntity,
    this.relatedId,
    this.assignedUserId,
    this.assignedUserName,
    this.createdByUserId,
    this.createdByName,
    this.createdAt,
  });

  final String reminderId;
  final String title;
  final ReminderPriority priority;
  final ReminderStatus status;
  final String? description;
  final DateTime? remindAt;
  final String? relatedEntity;
  final String? relatedId;
  final String? assignedUserId;
  final String? assignedUserName;
  final String? createdByUserId;
  final String? createdByName;
  final DateTime? createdAt;

  bool get isActionable =>
      status == ReminderStatus.pending || status == ReminderStatus.overdue;

  factory Reminder.fromJson(Map<String, dynamic> json) {
    DateTime? parse(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;
    return Reminder(
      reminderId: json['reminderId'] as String,
      title: json['title'] as String? ?? 'Reminder',
      priority: ReminderPriority.fromWire(json['priority'] as String?),
      status: ReminderStatus.fromWire(json['status'] as String?),
      description: json['description'] as String?,
      remindAt: parse(json['remindAt']),
      relatedEntity: json['relatedEntity'] as String?,
      relatedId: json['relatedId'] as String?,
      assignedUserId: json['assignedUserId'] as String?,
      assignedUserName: json['assignedUserName'] as String?,
      createdByUserId: json['createdByUserId'] as String?,
      createdByName: json['createdByName'] as String?,
      createdAt: parse(json['createdAt']),
    );
  }
}

/// Body for `POST /reminders` — mirrors backend `CreateReminderRequest`.
/// [title], [remindAt] (must be strictly in the future — E4), [relatedEntity]
/// and [relatedId] are required; everything else is optional.
class CreateReminderPayload {
  const CreateReminderPayload({
    required this.title,
    required this.remindAt,
    required this.relatedEntity,
    required this.relatedId,
    this.description,
    this.priority,
    this.assignedUserId,
  });

  final String title;
  final DateTime remindAt;
  final ReminderRelatedEntityType relatedEntity;
  final String relatedId;
  final String? description;
  final ReminderPriority? priority;
  final String? assignedUserId;

  Map<String, dynamic> toJson() => {
    'title': title.trim(),
    'remindAt': remindAt.toUtc().toIso8601String(),
    'relatedEntity': relatedEntity.wire,
    'relatedId': relatedId,
    if (description != null && description!.trim().isNotEmpty)
      'description': description!.trim(),
    if (priority != null) 'priority': priority!.wire,
    if (assignedUserId != null) 'assignedUserId': assignedUserId,
  };
}

/// Body for `PUT /reminders/{id}` — mirrors backend `UpdateReminderRequest`.
/// Every field is optional so a partial edit never clobbers an untouched
/// field; [remindAt] carries the server's `@Future` constraint, so callers
/// should only set it when the user actually changed the due date (an
/// unchanged, already-past due date would otherwise fail validation).
/// [forceIfDone] must be `true` to change anything on a reminder that is
/// already `DONE` (`REMINDER_ALREADY_DONE`, HTTP 409, otherwise).
class UpdateReminderPayload {
  const UpdateReminderPayload({
    this.title,
    this.description,
    this.remindAt,
    this.priority,
    this.markAsDone,
    this.forceIfDone = false,
  });

  final String? title;
  final String? description;
  final DateTime? remindAt;
  final ReminderPriority? priority;
  final bool? markAsDone;
  final bool forceIfDone;

  Map<String, dynamic> toJson() => {
    if (title != null) 'title': title!.trim(),
    if (description != null) 'description': description!.trim(),
    if (remindAt != null) 'remindAt': remindAt!.toUtc().toIso8601String(),
    if (priority != null) 'priority': priority!.wire,
    if (markAsDone != null) 'markAsDone': markAsDone,
    'forceIfDone': forceIfDone,
  };
}
