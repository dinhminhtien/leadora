/// Authenticated user — Dart mirror of `LoginResponse.UserInfo`.
///
/// Carries effective [permissions] (e.g. `LEAD_VIEW`) and [roles] (e.g.
/// `SALES`, `MANAGER`) so the UI can gate screens/actions client-side. Server
/// remains the source of truth (403s are still handled); this only avoids
/// showing controls the user can't use.
class AuthUser {
  const AuthUser({
    required this.id,
    required this.email,
    required this.name,
    this.roles = const [],
    this.permissions = const [],
    this.avatarUrl,
  });

  final String id;
  final String email;
  final String name;
  final List<String> roles;
  final List<String> permissions;
  final String? avatarUrl;

  bool hasPermission(String code) => permissions.contains(code);

  bool hasAnyRole(Iterable<String> candidates) =>
      candidates.any(roles.contains);

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    return AuthUser(
      id: json['id'] as String,
      email: json['email'] as String? ?? '',
      name: json['name'] as String? ?? '',
      roles: _stringList(json['roles']),
      permissions: _stringList(json['permissions']),
      avatarUrl: json['avatarUrl'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'email': email,
        'name': name,
        'roles': roles,
        'permissions': permissions,
        'avatarUrl': avatarUrl,
      };

  static List<String> _stringList(Object? raw) {
    if (raw is List) return raw.map((e) => '$e').toList(growable: false);
    return const [];
  }
}

/// Result of a successful `POST /auth/login` — the user plus their access token.
class LoginResult {
  const LoginResult({required this.user, required this.accessToken});

  final AuthUser user;
  final String accessToken;

  factory LoginResult.fromJson(Map<String, dynamic> json) {
    final userJson = json['user'];
    if (userJson is! Map<String, dynamic>) {
      throw const FormatException('login response missing "user"');
    }
    return LoginResult(
      user: AuthUser.fromJson(userJson),
      accessToken: json['accessToken'] as String? ?? '',
    );
  }
}
