import '../../../shared/widgets/status_chip.dart';

/// Mirrors backend `ReminderStatus`.
enum ReminderStatus {
  pending('PENDING'),
  done('DONE'),
  overdue('OVERDUE'),
  cancelled('CANCELLED');

  const ReminderStatus(this.wire);
  final String wire;

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
  high('HIGH'),
  medium('MEDIUM'),
  low('LOW');

  const ReminderPriority(this.wire);
  final String wire;

  static ReminderPriority fromWire(String? raw) => ReminderPriority.values
      .firstWhere((p) => p.wire == raw, orElse: () => ReminderPriority.medium);

  StatusTone get tone => switch (this) {
    ReminderPriority.high => StatusTone.danger,
    ReminderPriority.medium => StatusTone.info,
    ReminderPriority.low => StatusTone.neutral,
  };
}

/// Dart mirror of backend `ReminderResponse` — UC-16.1/16.2 on mobile.
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
    this.assignedUserName,
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
  final String? assignedUserName;
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
      assignedUserName: json['assignedUserName'] as String?,
      createdByName: json['createdByName'] as String?,
      createdAt: parse(json['createdAt']),
    );
  }
}
