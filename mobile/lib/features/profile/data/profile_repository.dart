import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'profile_models.dart';

/// Self-service profile API calls (UC-5 / UC-24.12 / UC-24.13).
class ProfileRepository {
  ProfileRepository(this._client);

  final ApiClient _client;

  Future<Profile> getMyProfile() {
    return _client.get<Profile>(
      ApiPaths.profileMe,
      decode: (data) => Profile.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-5 — update own name / phone / avatar. `PUT /profile/me`.
  Future<Profile> updateMyProfile(UpdateProfilePayload payload) {
    return _client.put<Profile>(
      ApiPaths.profileMe,
      data: payload.toJson(),
      decode: (data) => Profile.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<void> changePassword(ChangePasswordPayload payload) {
    return _client.put<void>(
      ApiPaths.changePassword,
      data: payload.toJson(),
      decode: (_) {},
    );
  }
}

final profileRepositoryProvider = Provider<ProfileRepository>((ref) {
  return ProfileRepository(ref.watch(apiClientProvider));
});

final myProfileProvider = FutureProvider.autoDispose<Profile>((ref) {
  return ref.watch(profileRepositoryProvider).getMyProfile();
});
