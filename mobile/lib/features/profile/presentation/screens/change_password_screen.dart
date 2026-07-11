import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../data/profile_models.dart';
import '../../data/profile_repository.dart';

/// UC-24.13 — Change Password. Rules mirror the web `changePasswordSchema`
/// exactly: min 8 chars with uppercase, lowercase, number and special
/// character, plus a live strength meter and requirements checklist.
class ChangePasswordScreen extends ConsumerStatefulWidget {
  const ChangePasswordScreen({super.key});

  @override
  ConsumerState<ChangePasswordScreen> createState() =>
      _ChangePasswordScreenState();
}

class _ChangePasswordScreenState extends ConsumerState<ChangePasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _current = TextEditingController();
  final _next = TextEditingController();
  final _confirm = TextEditingController();
  bool _showCurrent = false;
  bool _showNext = false;
  bool _showConfirm = false;
  bool _submitting = false;
  bool _autovalidate = false;

  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    _confirm.dispose();
    super.dispose();
  }

  // ── Web `changePasswordSchema` mirrors ─────────────────────────────────────

  static final _upper = RegExp('[A-Z]');
  static final _lower = RegExp('[a-z]');
  static final _digit = RegExp('[0-9]');
  static final _special = RegExp('[^A-Za-z0-9]');

  String? _validateNew(String? v) {
    final value = v ?? '';
    if (value.length < 8) return 'Password must be at least 8 characters';
    if (!_upper.hasMatch(value)) return 'Must contain an uppercase letter';
    if (!_lower.hasMatch(value)) return 'Must contain a lowercase letter';
    if (!_digit.hasMatch(value)) return 'Must contain a number';
    if (!_special.hasMatch(value)) return 'Must contain a special character';
    return null;
  }

  /// Web `getPasswordStrength` — same 6-point score and labels.
  ({int score, String label, Color color}) _strength(String pw) {
    var score = 0;
    if (pw.length >= 8) score++;
    if (pw.length >= 12) score++;
    if (_upper.hasMatch(pw)) score++;
    if (_lower.hasMatch(pw)) score++;
    if (_digit.hasMatch(pw)) score++;
    if (_special.hasMatch(pw)) score++;
    if (score <= 2) {
      return (score: score, label: 'Weak', color: AppColors.danger);
    }
    if (score <= 4) {
      return (score: score, label: 'Medium', color: AppColors.warning);
    }
    return (score: score, label: 'Strong', color: AppColors.success);
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      setState(() => _autovalidate = true);
      return;
    }
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    try {
      await ref
          .read(profileRepositoryProvider)
          .changePassword(
            ChangePasswordPayload(
              currentPassword: _current.text,
              newPassword: _next.text,
              confirmPassword: _confirm.text,
            ),
          );
      messenger.showSnackBar(
        const SnackBar(content: Text('Password changed successfully')),
      );
      router.pop();
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Widget _visibilityToggle(bool shown, VoidCallback onTap) => IconButton(
    tooltip: shown ? 'Hide password' : 'Show password',
    icon: Icon(
      shown ? Icons.visibility_off_outlined : Icons.visibility_outlined,
    ),
    onPressed: onTap,
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final pw = _next.text;
    return Scaffold(
      appBar: AppBar(title: const Text('Change password')),
      body: Form(
        key: _formKey,
        autovalidateMode: _autovalidate
            ? AutovalidateMode.onUserInteraction
            : AutovalidateMode.disabled,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.lg,
            AppSpacing.xl,
            AppSpacing.lg,
            AppSpacing.huge,
          ),
          children: [
            Text(
              'Ensure your account is using a secure, random password.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 20),
            TextFormField(
              controller: _current,
              enabled: !_submitting,
              obscureText: !_showCurrent,
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(
                labelText: 'Current password',
                prefixIcon: const Icon(Icons.lock_outline_rounded),
                suffixIcon: _visibilityToggle(
                  _showCurrent,
                  () => setState(() => _showCurrent = !_showCurrent),
                ),
              ),
              validator: (v) => (v == null || v.isEmpty)
                  ? 'Current password is required'
                  : null,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _next,
              enabled: !_submitting,
              obscureText: !_showNext,
              textInputAction: TextInputAction.next,
              onChanged: (_) => setState(() {}), // live meter + checklist
              decoration: InputDecoration(
                labelText: 'New password',
                prefixIcon: const Icon(Icons.lock_reset_rounded),
                suffixIcon: _visibilityToggle(
                  _showNext,
                  () => setState(() => _showNext = !_showNext),
                ),
              ),
              validator: _validateNew,
            ),
            if (pw.isNotEmpty) ...[
              const SizedBox(height: 12),
              _StrengthPanel(strength: _strength(pw), password: pw),
            ],
            const SizedBox(height: 16),
            TextFormField(
              controller: _confirm,
              enabled: !_submitting,
              obscureText: !_showConfirm,
              textInputAction: TextInputAction.done,
              onFieldSubmitted: (_) => _submitting ? null : _submit(),
              decoration: InputDecoration(
                labelText: 'Confirm new password',
                prefixIcon: const Icon(Icons.check_circle_outline_rounded),
                suffixIcon: _visibilityToggle(
                  _showConfirm,
                  () => setState(() => _showConfirm = !_showConfirm),
                ),
              ),
              validator: (v) => (v == null || v.isEmpty)
                  ? 'Please confirm your new password'
                  : v != _next.text
                  ? 'Passwords do not match'
                  : null,
            ),
            const SizedBox(height: 28),
            FilledButton.icon(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(52),
              ),
              icon: _submitting
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2.5),
                    )
                  : const Icon(Icons.shield_outlined, size: 20),
              label: Text(_submitting ? 'Updating…' : 'Update password'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Live strength meter (3 segments) + requirements checklist — the mobile
/// version of the web password panel.
class _StrengthPanel extends StatelessWidget {
  const _StrengthPanel({required this.strength, required this.password});

  final ({int score, String label, Color color}) strength;
  final String password;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final segments = switch (strength.label) {
      'Weak' => 1,
      'Medium' => 2,
      _ => 3,
    };
    final requirements = <(String, bool)>[
      ('8+ chars', password.length >= 8),
      ('Uppercase', RegExp('[A-Z]').hasMatch(password)),
      ('Lowercase', RegExp('[a-z]').hasMatch(password)),
      ('Number', RegExp('[0-9]').hasMatch(password)),
      ('Special', RegExp('[^A-Za-z0-9]').hasMatch(password)),
    ];

    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: scheme.outlineVariant.withValues(alpha: 0.6)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              for (var i = 1; i <= 3; i++) ...[
                if (i > 1) const SizedBox(width: 6),
                Expanded(
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 250),
                    height: 6,
                    decoration: BoxDecoration(
                      color: i <= segments
                          ? strength.color
                          : scheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(AppRadii.pill),
                    ),
                  ),
                ),
              ],
              const SizedBox(width: 10),
              Text(
                strength.label,
                style: theme.textTheme.labelMedium?.copyWith(
                  color: strength.color,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 12,
            runSpacing: 6,
            children: [
              for (final (label, met) in requirements)
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      met ? Icons.check_circle_rounded : Icons.circle_outlined,
                      size: AppIconSize.xs,
                      color: met ? AppColors.success : scheme.outlineVariant,
                    ),
                    const SizedBox(width: AppSpacing.xs),
                    Text(
                      label,
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: met
                            ? AppColors.success
                            : scheme.onSurfaceVariant,
                        fontWeight: met ? FontWeight.w700 : FontWeight.w500,
                      ),
                    ),
                  ],
                ),
            ],
          ),
        ],
      ),
    );
  }
}
