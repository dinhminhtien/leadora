import 'package:flutter/material.dart';

import '../../../shared/widgets/status_chip.dart';

/// Interaction type — mirrors backend `InteractionTimelineResponse.type`.
/// The backend validates this as a raw string (no Java enum), so
/// [fromWire] falls back to [note] for anything unrecognized.
enum InteractionType {
  call('call'),
  email('email'),
  meeting('meeting'),
  note('note');

  const InteractionType(this.wire);
  final String wire;

  static InteractionType fromWire(String? raw) => InteractionType.values.firstWhere(
        (t) => t.wire == raw?.toLowerCase(),
        orElse: () => InteractionType.note,
      );

  String get label => switch (this) {
        InteractionType.call => 'Call',
        InteractionType.email => 'Email',
        InteractionType.meeting => 'Meeting',
        InteractionType.note => 'Note',
      };

  IconData get icon => switch (this) {
        InteractionType.call => Icons.call_rounded,
        InteractionType.email => Icons.mail_outline_rounded,
        InteractionType.meeting => Icons.groups_rounded,
        InteractionType.note => Icons.sticky_note_2_outlined,
      };

  StatusTone get tone => switch (this) {
        InteractionType.call => StatusTone.info,
        InteractionType.email => StatusTone.brand,
        InteractionType.meeting => StatusTone.warning,
        InteractionType.note => StatusTone.neutral,
      };
}

/// Dart mirror of backend `InteractionTimelineResponse`.
class InteractionTimelineEntry {
  const InteractionTimelineEntry({
    required this.id,
    required this.type,
    required this.description,
    required this.agentName,
    this.agentId,
    this.linkedName,
    this.linkedType,
    this.linkedId,
    this.occurredAt,
    this.createdAt,
  });

  final String id;
  final InteractionType type;
  final String description;
  final String agentName;
  final String? agentId;
  final String? linkedName;
  final String? linkedType;
  final String? linkedId;
  final DateTime? occurredAt;
  final DateTime? createdAt;

  factory InteractionTimelineEntry.fromJson(Map<String, dynamic> json) {
    DateTime? parse(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;
    return InteractionTimelineEntry(
      id: json['id'] as String,
      type: InteractionType.fromWire(json['type'] as String?),
      description: json['description'] as String? ?? '',
      agentName: json['agentName'] as String? ?? 'Unknown',
      agentId: json['agentId'] as String?,
      linkedName: json['linkedName'] as String?,
      linkedType: json['linkedType'] as String?,
      linkedId: json['linkedId'] as String?,
      occurredAt: parse(json['occurredAt']),
      createdAt: parse(json['createdAt']),
    );
  }
}

/// Dart mirror of backend `InteractionAuditLogResponse`.
class InteractionAuditLog {
  const InteractionAuditLog({
    required this.auditId,
    required this.action,
    this.changedByName,
    this.changedByRole,
    this.timestamp,
    this.fieldName,
    this.oldValue,
    this.newValue,
  });

  final String auditId;
  final String action;
  final String? changedByName;
  final String? changedByRole;
  final DateTime? timestamp;
  final String? fieldName;
  final String? oldValue;
  final String? newValue;

  factory InteractionAuditLog.fromJson(Map<String, dynamic> json) {
    return InteractionAuditLog(
      auditId: json['auditId'] as String,
      action: json['action'] as String? ?? '',
      changedByName: json['changedByName'] as String?,
      changedByRole: json['changedByRole'] as String?,
      timestamp: json['timestamp'] is String
          ? DateTime.tryParse(json['timestamp'] as String)
          : null,
      fieldName: json['fieldName'] as String?,
      oldValue: json['oldValue'] as String?,
      newValue: json['newValue'] as String?,
    );
  }
}

/// Payload for Log Customer Interaction Note / Record Customer Meeting
/// Summary — POST /interaction-timeline. Exactly one of [leadId] /
/// [customerId] / [dealId] must be set (backend requirement).
class CreateInteractionPayload {
  const CreateInteractionPayload({
    required this.type,
    required this.description,
    required this.occurredAt,
    this.leadId,
    this.customerId,
    this.dealId,
  });

  final InteractionType type;
  final String description;
  final DateTime occurredAt;
  final String? leadId;
  final String? customerId;
  final String? dealId;

  Map<String, dynamic> toJson() => {
        'type': type.wire,
        'description': description,
        'occurredAt': occurredAt.toUtc().toIso8601String(),
        if (leadId != null) 'leadId': leadId,
        if (customerId != null) 'customerId': customerId,
        if (dealId != null) 'dealId': dealId,
      };
}

/// Payload for editing an interaction's own fields — PUT /interaction-timeline/{id}.
class UpdateInteractionPayload {
  const UpdateInteractionPayload({
    required this.type,
    required this.description,
    required this.occurredAt,
  });

  final InteractionType type;
  final String description;
  final DateTime occurredAt;

  Map<String, dynamic> toJson() => {
        'type': type.wire,
        'description': description,
        'occurredAt': occurredAt.toUtc().toIso8601String(),
      };
}