import '../../../shared/widgets/status_chip.dart';

/// Deal status — mirrors backend `DealResponse.status` ("active" | "won" |
/// "lost", see `DealMapper.mapStatusToString`).
enum DealStatus {
  active('active'),
  won('won'),
  lost('lost');

  const DealStatus(this.wire);
  final String wire;

  static DealStatus fromWire(String? raw) => DealStatus.values.firstWhere(
        (s) => s.wire == raw,
        orElse: () => DealStatus.active,
      );

  StatusTone get tone => switch (this) {
        DealStatus.active => StatusTone.brand,
        DealStatus.won => StatusTone.success,
        DealStatus.lost => StatusTone.danger,
      };
}

/// Dart mirror of backend `DealResponse`. `stage` is already a human-readable
/// label from the backend (e.g. "Negotiation", "Confirmed") — no local enum.
class Deal {
  const Deal({
    required this.id,
    required this.title,
    required this.status,
    this.contactName,
    this.email,
    this.phone,
    this.value,
    this.probability,
    this.stage,
    this.owner,
    this.expectedClose,
    this.createdAt,
    this.notes,
  });

  final String id;
  final String title;
  final DealStatus status;
  final String? contactName;
  final String? email;
  final String? phone;
  final double? value;
  final int? probability;
  final String? stage;
  final String? owner;
  final DateTime? expectedClose;
  final DateTime? createdAt;
  final String? notes;

  factory Deal.fromJson(Map<String, dynamic> json) {
    DateTime? parseDate(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;

    return Deal(
      id: json['id'] as String,
      title: json['title'] as String? ?? 'Untitled deal',
      status: DealStatus.fromWire(json['status'] as String?),
      contactName: json['contactName'] as String?,
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      value: (json['value'] as num?)?.toDouble(),
      probability: json['probability'] as int?,
      stage: json['stage'] as String?,
      owner: json['owner'] as String?,
      expectedClose: parseDate(json['expectedClose']),
      createdAt: parseDate(json['createdAt']),
      notes: json['notes'] as String?,
    );
  }
}
