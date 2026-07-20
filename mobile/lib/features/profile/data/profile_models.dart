/// Dart mirror of backend `ProfileResponse` (UC-5.1).
class Profile {
  const Profile({
    required this.userId,
    required this.fullName,
    required this.email,
    this.phone,
    this.roleName,
    this.status,
    this.avatarUrl,
    this.lastLoginAt,
    this.createdAt,
  });

  final String userId;
  final String fullName;
  final String email;
  final String? phone;
  final String? roleName;
  final String? status;
  final String? avatarUrl;
  final DateTime? lastLoginAt;
  final DateTime? createdAt;

  factory Profile.fromJson(Map<String, dynamic> json) {
    DateTime? parse(Object? v) =>
        v is String && v.isNotEmpty ? DateTime.tryParse(v) : null;
    return Profile(
      userId: json['userId'] as String,
      fullName: json['fullName'] as String? ?? '',
      email: json['email'] as String? ?? '',
      phone: json['phone'] as String?,
      roleName: json['roleName'] as String?,
      status: json['status'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
      lastLoginAt: parse(json['lastLoginAt']),
      createdAt: parse(json['createdAt']),
    );
  }
}

/// Payload for `PUT /profile/me` — mirrors the web `UpdateProfilePayload`.
/// [phone] / [avatarUrl] are sent as explicit nulls when cleared, matching the
/// web client (`phone: data.phone || null`).
class UpdateProfilePayload {
  const UpdateProfilePayload({
    required this.fullName,
    this.phone,
    this.avatarUrl,
  });

  final String fullName;
  final String? phone;
  final String? avatarUrl;

  Map<String, dynamic> toJson() => {
    'fullName': fullName.trim(),
    'phone': (phone == null || phone!.trim().isEmpty) ? null : phone!.trim(),
    'avatarUrl': (avatarUrl == null || avatarUrl!.trim().isEmpty)
        ? null
        : avatarUrl,
  };
}

/// Payload for UC-24.13 Change Password.
class ChangePasswordPayload {
  const ChangePasswordPayload({
    required this.currentPassword,
    required this.newPassword,
  });

  final String currentPassword;
  final String newPassword;

  Map<String, dynamic> toJson() => {
    'currentPassword': currentPassword,
    'newPassword': newPassword,
  };
}
