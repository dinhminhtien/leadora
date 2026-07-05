import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/auth_repository.dart';

/// Controller handling the state of requesting a password reset email link.
class ForgotPasswordController extends AutoDisposeAsyncNotifier<void> {
  AuthRepository get _repo => ref.read(authRepositoryProvider);

  @override
  FutureOr<void> build() {
    // Initial idle state
  }

  /// Sends a request to send a reset password link to the specified email.
  /// Returns `true` if successful, or `false` on failure.
  Future<bool> sendResetLink(String email) async {
    state = const AsyncLoading<void>();
    final result = await AsyncValue.guard(() => _repo.forgotPassword(email));
    state = result;
    return !result.hasError;
  }
}

final forgotPasswordControllerProvider =
    AutoDisposeAsyncNotifierProvider<ForgotPasswordController, void>(
  ForgotPasswordController.new,
);
