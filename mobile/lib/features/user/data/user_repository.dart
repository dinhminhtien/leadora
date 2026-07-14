import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'user_models.dart';

/// Read-only access to the user directory. Currently just the flat assignee
/// list; management (accounts CRUD) lives on the web admin.
class UserRepository {
  UserRepository(this._client);

  final ApiClient _client;

  /// `GET /users` — flat array of all users, for assignee pickers.
  Future<List<UserSummary>> getAssignableUsers() {
    return _client.get<List<UserSummary>>(
      ApiPaths.users,
      decode: (data) => (data as List<dynamic>)
          .map((e) => UserSummary.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

final userRepositoryProvider = Provider<UserRepository>((ref) {
  return UserRepository(ref.watch(apiClientProvider));
});

/// Cached directory of assignable users. Kept alive (not autoDispose) so the
/// assignee picker opens instantly across task create/edit without re-fetching.
final assignableUsersProvider = FutureProvider<List<UserSummary>>((ref) {
  return ref.watch(userRepositoryProvider).getAssignableUsers();
});
