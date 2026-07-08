/// Dart mirror of backend `UserSummaryResponse` — the lightweight, flat shape
/// returned by `GET /users` for assignee dropdowns (leads / tasks / deals).
class UserSummary {
  const UserSummary({
    required this.userId,
    required this.fullName,
    this.email,
    this.roleName,
  });

  final String userId;
  final String fullName;
  final String? email;
  final String? roleName;

  factory UserSummary.fromJson(Map<String, dynamic> json) {
    return UserSummary(
      userId: json['userId'] as String,
      fullName: json['fullName'] as String? ?? 'Unknown user',
      email: json['email'] as String?,
      roleName: json['roleName'] as String?,
    );
  }
}
