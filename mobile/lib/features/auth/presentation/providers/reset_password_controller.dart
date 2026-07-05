import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/auth_repository.dart';

/// Controller handling the state of updating a password.
class ResetPasswordController extends AutoDisposeAsyncNotifier<void> {
  AuthRepository get _repo => ref.read(authRepositoryProvider);

  @override
  FutureOr<void> build() {
    // Initial idle state
  }

  /// Sends a request to reset password with the given token and password.
  /// Returns `true` if successful, or `false` on failure.
  Future<bool> resetPassword({
    required String token,
    required String password,
  }) async {
    state = const AsyncLoading<void>();
    final result = await AsyncValue.guard(
      () => _repo.resetPassword(token: token, password: password),
    );
    state = result;
    return !result.hasError;
  }
}

final resetPasswordControllerProvider =
    AutoDisposeAsyncNotifierProvider<ResetPasswordController, void>(
  ResetPasswordController.new,
);
