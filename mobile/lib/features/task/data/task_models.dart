import '../../../shared/widgets/status_chip.dart';

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
    this.assignedUserName,
    this.leadId,
    this.leadName,
    this.customerId,
    this.customerName,
    this.dealId,
    this.dealName,
    this.primaryContactName,
    this.primaryContactPhone,
    this.startAt,
    this.endAt,
    this.createdAt,
    this.isOverdue = false,
  });

  final String taskId;
  final String title;
  final TaskStatus status;
  final TaskPriority priority;
  final String? description;
  final String? resultNote;
  final String? assignedUserName;
  final String? leadId;
  final String? leadName;
  final String? customerId;
  final String? customerName;
  final String? dealId;
  final String? dealName;
  final String? primaryContactName;
  final String? primaryContactPhone;
  final DateTime? startAt;
  final DateTime? endAt;
  final DateTime? createdAt;
  final bool isOverdue;

  bool get isOpen => status == TaskStatus.open;

  /// The subject this task follows up on (lead / customer / deal).
  String? get relatedName => leadName ?? customerName ?? dealName;

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
      assignedUserName: json['assignedUserName'] as String?,
      leadId: json['leadId'] as String?,
      leadName: json['leadName'] as String?,
      customerId: json['customerId'] as String?,
      customerName: json['customerName'] as String?,
      dealId: json['dealId'] as String?,
      dealName: json['dealName'] as String?,
      primaryContactName: json['primaryContactName'] as String?,
      primaryContactPhone: json['primaryContactPhone'] as String?,
      startAt: parse(json['startAt']),
      endAt: parse(json['endAt']),
      createdAt: parse(json['createdAt']),
      isOverdue: json['isOverdue'] as bool? ?? false,
    );
  }
}
