import 'package:flutter/material.dart';

/// Dart mirror of backend `NotificationResponse`.
class AppNotification {
  const AppNotification({
    required this.id,
    required this.title,
    required this.message,
    required this.isRead,
    this.type,
    this.priority,
    this.relatedEntity,
    this.relatedId,
    this.createdAt,
    this.recipientId,
    this.recipientName,
  });

  final String id;
  final String title;
  final String message;
  final bool isRead;
  final String? type;

  /// Mirrors backend `NotificationResponse.priority` (`NotificationPriority`
  /// enum: LOW/NORMAL/HIGH/URGENT) — see [kNotificationPriorityOptions].
  final String? priority;
  final String? relatedEntity;
  final String? relatedId;
  final DateTime? createdAt;

  /// Who this notification was sent to — only populated in the Manager/Admin
  /// aggregate feed (`GET /notifications?allUsers=true`); null elsewhere.
  final String? recipientId;
  final String? recipientName;

  AppNotification copyWith({bool? isRead}) => AppNotification(
    id: id,
    title: title,
    message: message,
    isRead: isRead ?? this.isRead,
    type: type,
    priority: priority,
    relatedEntity: relatedEntity,
    relatedId: relatedId,
    createdAt: createdAt,
    recipientId: recipientId,
    recipientName: recipientName,
  );

  /// Icon derived from the backend `type` / `relatedEntity` for a richer list.
  IconData get icon {
    final key = (type ?? relatedEntity ?? '').toUpperCase();
    if (key.contains('TASK')) return Icons.checklist_rounded;
    if (key.contains('LEAD')) return Icons.person_add_alt_1_rounded;
    if (key.contains('BOOKING')) return Icons.event_available_rounded;
    if (key.contains('PAYMENT')) return Icons.payments_rounded;
    if (key.contains('DEAL')) return Icons.handshake_rounded;
    if (key.contains('SLA')) return Icons.timer_outlined;
    return Icons.notifications_rounded;
  }

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    return AppNotification(
      id: json['id'] as String,
      title: json['title'] as String? ?? 'Notification',
      message: json['message'] as String? ?? '',
      isRead: json['isRead'] as bool? ?? json['read'] as bool? ?? false,
      type: json['type'] as String?,
      priority: json['priority'] as String?,
      relatedEntity: json['relatedEntity'] as String?,
      relatedId: json['relatedId'] as String?,
      createdAt: json['createdAt'] is String
          ? DateTime.tryParse(json['createdAt'] as String)
          : null,
      recipientId: json['recipientId'] as String?,
      recipientName: json['recipientName'] as String?,
    );
  }
}

/// Known values of `NotificationResponse.priority` (backend
/// `NotificationPriority` enum) — backs the priority filter chips.
/// `NotificationSpecifications.priority` returns "no matches" (not a 500) for
/// anything outside this set, so these are the only values worth offering.
const kNotificationPriorityOptions = <String>['LOW', 'NORMAL', 'HIGH', 'URGENT'];

/// Known values of `NotificationEntity.type` written by every notifier
/// (`TaskNotifier`, `RoomRequestNotifier`, and the lead/quotation/SLA/reminder/
/// handover/reporting use cases) — there is no backend enum for this free-text
/// column, so this fixed list is the mobile filter sheet's vocabulary, the same
/// way `kLeadSourceOptions` covers the free-text lead `source` field. Exact
/// match against `NotificationSpecifications.type`.
const kNotificationTypeOptions = <String>[
  'LEAD_ASSIGNED',
  'LEAD_REOPENED',
  'TASK_ASSIGNED',
  'TASK_REASSIGNED',
  'TASK_COMPLETED',
  'TASK_OVERDUE',
  'QUOTATION_PENDING_APPROVAL',
  'QUOTATION_SENT',
  'QUOTATION_APPROVAL',
  'CUSTOMER_RESPONSE',
  'BOOKING_UPDATE',
  'SLA_WARNING',
  'SLA_BREACH',
  'SLA_ESCALATED',
  'REMINDER',
  'REMINDER_DUE_SOON',
  'REMINDER_OVERDUE',
  'REMINDER_ESCALATED',
  'HANDOVER',
  'ROOM_REQUEST_RAISED',
  'ROOM_REQUEST_ANSWERED',
  'ROOM_CONFIRMATION_URGENT',
  'DISCOUNT_REPORT_GENERATED',
];
