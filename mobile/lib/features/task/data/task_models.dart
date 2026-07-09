import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/status_chip.dart';

/// Pipedrive-style activity categories, detected from the title prefix
/// ("Call: …"). Mirrors the web `ACTIVITY_TYPES` / `detectActivityType` — the
/// backend has no dedicated column; the convention lives in the title.
enum TaskActivityType {
  call('Call', Icons.call_rounded, AppColors.success),
  email('Email', Icons.mail_rounded, AppColors.brandSeed),
  meeting('Meeting', Icons.groups_rounded, AppColors.accentPurple),
  siteVisit('Site Visit', Icons.place_rounded, AppColors.accentOrange),
  followUp('Follow-up', Icons.autorenew_rounded, AppColors.accentTeal),
  task('Task', Icons.check_box_outlined, AppColors.neutral);

  const TaskActivityType(this.label, this.icon, this.color);

  final String label;
  final IconData icon;
  final Color color;

  /// The auto-title prefix inserted when this type is picked ("Call: ").
  String get titlePrefix => '$label: ';

  static TaskActivityType detect(String title) {
    final lower = title.toLowerCase();
    return TaskActivityType.values.firstWhere(
      (t) => lower.startsWith('${t.label.toLowerCase()}:'),
      orElse: () => TaskActivityType.task,
    );
  }
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

  /// Activity category detected from the title prefix (web parity).
  TaskActivityType get activityType => TaskActivityType.detect(title);

  /// Copy with a different lifecycle status — used by the list's optimistic
  /// quick-complete toggle (overdue clears, matching the backend's rule that
  /// only OPEN tasks can be overdue).
  Task withStatus(TaskStatus newStatus) => Task(
    taskId: taskId,
    title: title,
    status: newStatus,
    priority: priority,
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
