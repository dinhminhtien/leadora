import 'package:flutter/material.dart';

import '../../../shared/widgets/status_chip.dart';

/// Computed SLA health — mirrors backend `SlaMonitoringResponse.displayStatus`.
enum SlaDisplayStatus {
  withinSla('WITHIN_SLA'),
  warning('WARNING'),
  breached('BREACHED');

  const SlaDisplayStatus(this.wire);
  final String wire;

  static SlaDisplayStatus fromWire(String? raw) =>
      SlaDisplayStatus.values.firstWhere(
        (s) => s.wire == raw,
        orElse: () => SlaDisplayStatus.withinSla,
      );

  StatusTone get tone => switch (this) {
    SlaDisplayStatus.withinSla => StatusTone.success,
    SlaDisplayStatus.warning => StatusTone.warning,
    SlaDisplayStatus.breached => StatusTone.danger,
  };
}

/// Dart mirror of backend `SlaMonitoringResponse` — UC-17.3 View SLA (mobile).
class SlaTrackingEntry {
  const SlaTrackingEntry({
    required this.trackingId,
    required this.entityType,
    required this.entityId,
    required this.activityType,
    required this.displayStatus,
    required this.hoursRemaining,
    this.startedAt,
    this.deadlineAt,
    this.warningAt,
    this.resolvedAt,
  });

  final String trackingId;
  final String entityType;
  final String entityId;
  final String activityType;
  final SlaDisplayStatus displayStatus;

  /// Hours until deadline (negative = overdue); for resolved records, hours
  /// between resolution and deadline.
  final int hoursRemaining;
  final DateTime? startedAt;
  final DateTime? deadlineAt;
  final DateTime? warningAt;
  final DateTime? resolvedAt;

  bool get isResolved => resolvedAt != null;

  IconData get icon => switch (entityType.toUpperCase()) {
    'LEAD' => Icons.person_outline_rounded,
    'QUOTATION' => Icons.receipt_long_outlined,
    'TASK' => Icons.checklist_rounded,
    'BOOKING' => Icons.event_available_rounded,
    _ => Icons.timer_outlined,
  };

  factory SlaTrackingEntry.fromJson(Map<String, dynamic> json) {
    DateTime? parse(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;
    return SlaTrackingEntry(
      trackingId: json['trackingId'] as String,
      entityType: json['entityType'] as String? ?? '',
      entityId: json['entityId'] as String? ?? '',
      activityType: json['activityType'] as String? ?? '',
      displayStatus: SlaDisplayStatus.fromWire(
        json['displayStatus'] as String?,
      ),
      hoursRemaining: (json['hoursRemaining'] as num?)?.toInt() ?? 0,
      startedAt: parse(json['startedAt']),
      deadlineAt: parse(json['deadlineAt']),
      warningAt: parse(json['warningAt']),
      resolvedAt: parse(json['resolvedAt']),
    );
  }
}
