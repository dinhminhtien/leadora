import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/status_chip.dart';

/// What kind of work a task is — mirrors backend `ActivityType`.
///
/// This used to be *guessed by parsing the title* ("Call: …"), which made the
/// title load-bearing. It is a real column now (`tasks.activity_type`) and the
/// title is just a title. Nothing here reads the title.
enum TaskActivityType {
  call('CALL', 'Call', Icons.call_rounded, AppColors.success),
  email('EMAIL', 'Email', Icons.mail_rounded, AppColors.brandSeed),
  meeting('MEETING', 'Meeting', Icons.groups_rounded, AppColors.accentPurple),
  siteVisit('SITE_VISIT', 'Site Visit', Icons.place_rounded, AppColors.accentOrange),
  followUp('FOLLOW_UP', 'Follow-up', Icons.autorenew_rounded, AppColors.accentTeal),
  task('TASK', 'Task', Icons.check_box_outlined, AppColors.neutral);

  const TaskActivityType(this.wire, this.label, this.icon, this.color);

  final String wire;
  final String label;
  final IconData icon;
  final Color color;

  /// The value shown for a task that has none — a row written before the
  /// activity-type backfill, or a build of the backend that predates the field.
  static const TaskActivityType fallback = TaskActivityType.task;

  /// Never throws: an unknown or missing value reads as [fallback], so a legacy
  /// task still renders with an icon and a colour instead of blowing up.
  static TaskActivityType fromWire(String? raw) => TaskActivityType.values
      .firstWhere((t) => t.wire == raw, orElse: () => fallback);
}

/// Backend `CreateTaskRequest` caps the title at 255 chars.
const int kTaskTitleMaxLength = 255;

/// Task lifecycle — mirrors backend `TaskStatus` (OVERDUE is computed, not a
/// stored value; the backend flags it via `isOverdue`).
enum TaskStatus {
  open('OPEN'),
  completed('COMPLETED'),
  cancelled('CANCELLED');

  const TaskStatus(this.wire);
  final String wire;

  static TaskStatus fromWire(String? raw) => TaskStatus.values.firstWhere(
    (s) => s.wire == raw,
    orElse: () => TaskStatus.open,
  );

  StatusTone get tone => switch (this) {
    TaskStatus.open => StatusTone.info,
    TaskStatus.completed => StatusTone.success,
    TaskStatus.cancelled => StatusTone.neutral,
  };
}

enum TaskPriority {
  low('LOW'),
  medium('MEDIUM'),
  high('HIGH');

  const TaskPriority(this.wire);
  final String wire;

  static TaskPriority fromWire(String? raw) => TaskPriority.values.firstWhere(
    (p) => p.wire == raw,
    orElse: () => TaskPriority.medium,
  );

  StatusTone get tone => switch (this) {
    TaskPriority.low => StatusTone.neutral,
    TaskPriority.medium => StatusTone.info,
    TaskPriority.high => StatusTone.danger,
  };
}

/// Dart mirror of backend `TaskResponse`.
class Task {
  const Task({
    required this.taskId,
    required this.title,
    required this.status,
    required this.priority,
    this.activityType = TaskActivityType.fallback,
    this.description,
    this.resultNote,
    this.assignedUserId,
    this.assignedUserName,
    this.createdById,
    this.createdByName,
    this.leadId,
    this.leadName,
    this.leadPhone,
    this.leadEmail,
    this.leadCompanyName,
    this.customerId,
    this.customerName,
    this.customerPhone,
    this.customerEmail,
    this.customerCompanyName,
    this.dealId,
    this.dealName,
    this.dealStage,
    this.dealValue,
    this.dealCustomerName,
    this.dealOwnerName,
    this.leadStatus,
    this.leadSource,
    this.leadOwnerName,
    this.primaryContactName,
    this.primaryContactPhone,
    this.startAt,
    this.endAt,
    this.createdAt,
    this.updatedAt,
    this.isOverdue = false,
  });

  final String taskId;
  final String title;
  final TaskStatus status;
  final TaskPriority priority;
  final TaskActivityType activityType;
  final String? description;
  final String? resultNote;
  final String? assignedUserId;
  final String? assignedUserName;
  final String? createdById;
  final String? createdByName;
  final String? leadId;
  final String? leadName;
  final String? leadPhone;
  final String? leadEmail;
  final String? leadCompanyName;
  final String? customerId;
  final String? customerName;
  final String? customerPhone;
  final String? customerEmail;
  final String? customerCompanyName;
  final String? dealId;
  final String? dealName;

  // Extended linked-record context — present only on the detail response
  // (GET /tasks/{id}); null on list rows. Backs the Related Record card.
  final String? dealStage;
  final double? dealValue;
  final String? dealCustomerName;
  final String? dealOwnerName;
  final String? leadStatus;
  final String? leadSource;
  final String? leadOwnerName;

  final String? primaryContactName;
  final String? primaryContactPhone;
  final DateTime? startAt;
  final DateTime? endAt;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final bool isOverdue;

  bool get isOpen => status == TaskStatus.open;

  /// The subject this task follows up on (lead / customer / deal).
  String? get relatedName => leadName ?? customerName ?? dealName;

  /// Contact details of the linked entity (lead wins over customer, matching
  /// the web detail drawer).
  String? get contactName => leadId != null ? leadName : customerName;
  String? get contactPhone => leadId != null ? leadPhone : customerPhone;
  String? get contactEmail => leadId != null ? leadEmail : customerEmail;
  String? get contactCompany =>
      leadId != null ? leadCompanyName : customerCompanyName;

  /// Primary date for calendar grouping — start wins, then due (web parity).
  DateTime? get anchorDate => startAt ?? endAt;

  /// Copy with a different lifecycle status — used by the list's optimistic
  /// quick-complete toggle (overdue clears, matching the backend's rule that
  /// only OPEN tasks can be overdue).
  Task withStatus(TaskStatus newStatus) => Task(
    taskId: taskId,
    title: title,
    status: newStatus,
    priority: priority,
    activityType: activityType,
    description: description,
    resultNote: resultNote,
    assignedUserId: assignedUserId,
    assignedUserName: assignedUserName,
    createdById: createdById,
    createdByName: createdByName,
    leadId: leadId,
    leadName: leadName,
    leadPhone: leadPhone,
    leadEmail: leadEmail,
    leadCompanyName: leadCompanyName,
    customerId: customerId,
    customerName: customerName,
    customerPhone: customerPhone,
    customerEmail: customerEmail,
    customerCompanyName: customerCompanyName,
    dealId: dealId,
    dealName: dealName,
    dealStage: dealStage,
    dealValue: dealValue,
    dealCustomerName: dealCustomerName,
    dealOwnerName: dealOwnerName,
    leadStatus: leadStatus,
    leadSource: leadSource,
    leadOwnerName: leadOwnerName,
    primaryContactName: primaryContactName,
    primaryContactPhone: primaryContactPhone,
    startAt: startAt,
    endAt: endAt,
    createdAt: createdAt,
    updatedAt: updatedAt,
    isOverdue: newStatus == TaskStatus.open && isOverdue,
  );

  factory Task.fromJson(Map<String, dynamic> json) {
    DateTime? parse(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;
    return Task(
      taskId: json['taskId'] as String,
      title: json['title'] as String? ?? 'Untitled task',
      status: TaskStatus.fromWire(json['status'] as String?),
      priority: TaskPriority.fromWire(json['priority'] as String?),
      // Absent on a task the backfill hasn't reached (and on any backend build
      // that predates the column) — reads as Task, never null.
      activityType: TaskActivityType.fromWire(json['activityType'] as String?),
      description: json['description'] as String?,
      resultNote: json['resultNote'] as String?,
      assignedUserId: json['assignedUserId'] as String?,
      assignedUserName: json['assignedUserName'] as String?,
      createdById: json['createdById'] as String?,
      createdByName: json['createdByName'] as String?,
      leadId: json['leadId'] as String?,
      leadName: json['leadName'] as String?,
      leadPhone: json['leadPhone'] as String?,
      leadEmail: json['leadEmail'] as String?,
      leadCompanyName: json['leadCompanyName'] as String?,
      customerId: json['customerId'] as String?,
      customerName: json['customerName'] as String?,
      customerPhone: json['customerPhone'] as String?,
      customerEmail: json['customerEmail'] as String?,
      customerCompanyName: json['customerCompanyName'] as String?,
      dealId: json['dealId'] as String?,
      dealName: json['dealName'] as String?,
      dealStage: json['dealStage'] as String?,
      dealValue: (json['dealValue'] as num?)?.toDouble(),
      dealCustomerName: json['dealCustomerName'] as String?,
      dealOwnerName: json['dealOwnerName'] as String?,
      leadStatus: json['leadStatus'] as String?,
      leadSource: json['leadSource'] as String?,
      leadOwnerName: json['leadOwnerName'] as String?,
      primaryContactName: json['primaryContactName'] as String?,
      primaryContactPhone: json['primaryContactPhone'] as String?,
      startAt: parse(json['startAt']),
      endAt: parse(json['endAt']),
      createdAt: parse(json['createdAt']),
      updatedAt: parse(json['updatedAt']),
      isOverdue: json['isOverdue'] as bool? ?? false,
    );
  }
}

/// Body for `POST /tasks` — mirrors backend `CreateTaskRequest`.
/// [title], [assignedUserId] and [priority] are required by the backend.
class CreateTaskPayload {
  const CreateTaskPayload({
    required this.title,
    required this.assignedUserId,
    required this.priority,
    required this.activityType,
    this.description,
    this.resultNote,
    this.leadId,
    this.customerId,
    this.dealId,
    this.primaryContactName,
    this.primaryContactPhone,
    this.startAt,
    this.endAt,
  });

  final String title;
  final String assignedUserId;
  final TaskPriority priority;
  final TaskActivityType activityType;
  final String? description;
  final String? resultNote;
  final String? leadId;
  final String? customerId;
  final String? dealId;
  final String? primaryContactName;
  final String? primaryContactPhone;
  final DateTime? startAt;
  final DateTime? endAt;

  Map<String, dynamic> toJson() => {
    'title': title.trim(),
    'assignedUserId': assignedUserId,
    'priority': priority.wire,
    // Required by the backend: a new task must say what kind of work it is.
    'activityType': activityType.wire,
    ..._optionalTaskFields(
      description: description,
      resultNote: resultNote,
      leadId: leadId,
      customerId: customerId,
      dealId: dealId,
      primaryContactName: primaryContactName,
      primaryContactPhone: primaryContactPhone,
      startAt: startAt,
      endAt: endAt,
    ),
  };
}

/// Body for `PUT /tasks/{id}` — mirrors backend `UpdateTaskRequest`. Every
/// field is optional; only non-null values are sent so a partial edit never
/// clobbers untouched fields.
class UpdateTaskPayload {
  const UpdateTaskPayload({
    this.title,
    this.description,
    this.assignedUserId,
    this.priority,
    this.status,
    this.activityType,
    this.resultNote,
    this.leadId,
    this.customerId,
    this.dealId,
    this.primaryContactName,
    this.primaryContactPhone,
    this.startAt,
    this.endAt,
  });

  final String? title;
  final String? description;
  final String? assignedUserId;
  final TaskPriority? priority;
  final TaskStatus? status;
  final TaskActivityType? activityType;
  final String? resultNote;
  final String? leadId;
  final String? customerId;
  final String? dealId;
  final String? primaryContactName;
  final String? primaryContactPhone;
  final DateTime? startAt;
  final DateTime? endAt;

  Map<String, dynamic> toJson() => {
    if (title != null && title!.trim().isNotEmpty) 'title': title!.trim(),
    if (assignedUserId != null) 'assignedUserId': assignedUserId,
    if (priority != null) 'priority': priority!.wire,
    if (status != null) 'status': status!.wire,
    if (activityType != null) 'activityType': activityType!.wire,
    ..._optionalTaskFields(
      description: description,
      resultNote: resultNote,
      leadId: leadId,
      customerId: customerId,
      dealId: dealId,
      primaryContactName: primaryContactName,
      primaryContactPhone: primaryContactPhone,
      startAt: startAt,
      endAt: endAt,
    ),
  };
}

/// Body for `POST /tasks/{id}/resign` — mirrors backend `ResignTaskRequest`.
/// Any field left null is copied from the parent task server-side.
class ResignTaskPayload {
  const ResignTaskPayload({
    this.title,
    this.description,
    this.priority,
    this.assignedUserId,
    this.resignNote,
    this.startAt,
    this.endAt,
  });

  final String? title;
  final String? description;
  final TaskPriority? priority;
  final String? assignedUserId;
  final String? resignNote;
  final DateTime? startAt;
  final DateTime? endAt;

  Map<String, dynamic> toJson() => {
    if (title != null && title!.trim().isNotEmpty) 'title': title!.trim(),
    if (description != null && description!.trim().isNotEmpty)
      'description': description!.trim(),
    if (priority != null) 'priority': priority!.wire,
    if (assignedUserId != null) 'assignedUserId': assignedUserId,
    if (resignNote != null && resignNote!.trim().isNotEmpty)
      'resignNote': resignNote!.trim(),
    if (startAt != null) 'startAt': startAt!.toUtc().toIso8601String(),
    if (endAt != null) 'endAt': endAt!.toUtc().toIso8601String(),
  };
}

/// Shared serialization for the optional fields common to create/update.
/// Empty strings are dropped so we never send blank values the backend would
/// store verbatim.
Map<String, dynamic> _optionalTaskFields({
  String? description,
  String? resultNote,
  String? leadId,
  String? customerId,
  String? dealId,
  String? primaryContactName,
  String? primaryContactPhone,
  DateTime? startAt,
  DateTime? endAt,
}) {
  String? clean(String? v) => (v == null || v.trim().isEmpty) ? null : v.trim();
  return {
    'description': ?clean(description),
    'resultNote': ?clean(resultNote),
    'leadId': ?leadId,
    'customerId': ?customerId,
    'dealId': ?dealId,
    'primaryContactName': ?clean(primaryContactName),
    'primaryContactPhone': ?clean(primaryContactPhone),
    'startAt': ?startAt?.toUtc().toIso8601String(),
    'endAt': ?endAt?.toUtc().toIso8601String(),
  };
}
