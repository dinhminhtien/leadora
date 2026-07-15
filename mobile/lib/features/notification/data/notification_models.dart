import 'package:flutter/material.dart';

/// Dart mirror of backend `NotificationResponse`.
class AppNotification {
  const AppNotification({
    required this.id,
    required this.title,
    required this.message,
    required this.isRead,
    this.type,
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
