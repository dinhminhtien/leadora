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

/// Payload for UC-24.13 Change Password.
class ChangePasswordPayload {
  const ChangePasswordPayload({
    required this.currentPassword,
    required this.newPassword,
    required this.confirmPassword,
  });

  final String currentPassword;
  final String newPassword;
  final String confirmPassword;

  Map<String, dynamic> toJson() => {
        'currentPassword': currentPassword,
        'newPassword': newPassword,
        'confirmPassword': confirmPassword,
      };
}
