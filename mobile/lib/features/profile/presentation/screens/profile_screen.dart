import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../core/theme/theme_mode_controller.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/profile_models.dart';
import '../../data/profile_repository.dart';

/// UC-24.12 View Profile + entry points for Edit Profile (UC-5), Change
/// Password (UC-24.13) and Logout (UC-24.9). Mirrors the web Profile Settings
/// page: summary card with avatar/role/status, full metadata list, and the
/// account security actions.
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  /// Keep in sync with pubspec.yaml `version:`.
  static const String _appVersion = '1.0.0';

  /// Web `formatRoleName` — friendly labels for the known roles.
  static String _roleLabel(String? role) => switch (role?.toUpperCase()) {
    'ADMIN' => 'Administrator',
    'MANAGER' => 'Sales Manager',
    'SALES' => 'Sales Staff',
    'FRONT_OFFICE' => 'Front Office',
    'STAFF' => 'Staff',
    null => 'Staff',
    _ => Formatters.humanizeEnum(role),
  };

  static StatusTone _statusTone(String? status) => switch (status) {
    'ACTIVE' => StatusTone.success,
    'INACTIVE' => StatusTone.warning,
    _ => StatusTone.danger,
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(myProfileProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Profile')),
      body: AsyncValueView<Profile>(
        value: async,
        onRetry: () => ref.invalidate(myProfileProvider),
        data: (profile) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(myProfileProvider),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.lg,
              AppSpacing.sm,
              AppSpacing.lg,
              AppSpacing.xxxl,
            ),
            children: [
              _ProfileHeaderCard(profile: profile),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Account',
                icon: Icons.badge_outlined,
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Email',
                      value: profile.email,
                      icon: Icons.mail_outline_rounded,
                    ),
                    InfoRow(
                      label: 'Phone',
                      value: profile.phone,
                      icon: Icons.phone_outlined,
                    ),
                    InfoRow(
                      label: 'Role',
                      value: _roleLabel(profile.roleName),
                      icon: Icons.shield_outlined,
                    ),
                    InfoRow(
                      label: 'Status',
                      value: Formatters.humanizeEnum(profile.status),
                      icon: Icons.verified_user_outlined,
                    ),
                    InfoRow(
                      label: 'Member since',
                      value: Formatters.date(profile.createdAt),
                      icon: Icons.event_outlined,
                    ),
                    InfoRow(
                      label: 'Last login',
                      value: Formatters.dateTime(profile.lastLoginAt),
                      icon: Icons.login_rounded,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              const _AppearanceCard(),
              const SizedBox(height: 16),
              Card(
                elevation: 0,
                margin: EdgeInsets.zero,
                color: Theme.of(context).colorScheme.surfaceContainerLow,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(AppRadii.lg),
                ),
                child: Column(
                  children: [
                    ListTile(
                      leading: const Icon(Icons.edit_outlined),
                      title: const Text('Edit profile'),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: () => context.pushNamed(
                        RouteNames.profileEdit,
                        extra: profile,
                      ),
                    ),
                    const Divider(height: 1, indent: 16, endIndent: 16),
                    ListTile(
                      leading: const Icon(Icons.lock_outline_rounded),
                      title: const Text('Change password'),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: () => context.pushNamed(RouteNames.changePassword),
                    ),
                    const Divider(height: 1, indent: 16, endIndent: 16),
                    ListTile(
                      leading: Icon(
                        Icons.logout_rounded,
                        color: Theme.of(context).colorScheme.error,
                      ),
                      title: Text(
                        'Log out',
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                      onTap: () => _confirmLogout(context, ref),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              Center(
                child: Text(
                  'Leadora Mobile · v$_appVersion',
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: Theme.of(context).colorScheme.outline,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _confirmLogout(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Log out?'),
        content: const Text(
          'You will need to sign in again to access your workspace.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Log out'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await ref.read(authControllerProvider.notifier).logout();
    // The router's redirect guard reacts to the session change and returns to
    // login automatically; no manual navigation needed.
  }
}

/// Summary card with a brand-gradient banner, overlapping avatar (tap or the
/// camera badge to edit), name and role/status chips — the mobile take on the
/// web profile summary card.
class _ProfileHeaderCard extends ConsumerWidget {
  const _ProfileHeaderCard({required this.profile});

  final Profile profile;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    void openEdit() =>
        context.pushNamed(RouteNames.profileEdit, extra: profile);

    return Card(
      elevation: 0,
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      color: scheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadii.lg),
      ),
      child: Column(
        children: [
          Stack(
            alignment: Alignment.topCenter,
            children: [
              // Banner + spacer establish the stack height so the overlapping
              // avatar (and its camera badge) stay inside hit-test bounds.
              Column(
                children: [
                  Container(
                    height: 88,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          scheme.primary,
                          scheme.primary.withValues(alpha: 0.75),
                          scheme.tertiary.withValues(alpha: 0.85),
                        ],
                        begin: Alignment.centerLeft,
                        end: Alignment.centerRight,
                      ),
                    ),
                  ),
                  const SizedBox(height: 56),
                ],
              ),
              Positioned(
                top: 36,
                child: GestureDetector(
                  onTap: openEdit,
                  child: Stack(
                    children: [
                      Container(
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: scheme.surfaceContainerLow,
                            width: 4,
                          ),
                        ),
                        child: AppAvatar(
                          name: profile.fullName,
                          radius: 44,
                          imageUrl: profile.avatarUrl,
                        ),
                      ),
                      Positioned(
                        right: 0,
                        bottom: 0,
                        child: Material(
                          color: scheme.primary,
                          shape: const CircleBorder(),
                          elevation: 1,
                          child: InkWell(
                            customBorder: const CircleBorder(),
                            onTap: openEdit,
                            child: Padding(
                              padding: const EdgeInsets.all(AppSpacing.sm),
                              child: Icon(
                                Icons.photo_camera_rounded,
                                size: 14,
                                color: scheme.onPrimary,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.lg,
              0,
              AppSpacing.lg,
              AppSpacing.xl,
            ),
            child: Column(
              children: [
                Text(
                  profile.fullName,
                  textAlign: TextAlign.center,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  profile.email,
                  textAlign: TextAlign.center,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  alignment: WrapAlignment.center,
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    StatusChip(
                      tone: StatusTone.brand,
                      label: ProfileScreen._roleLabel(profile.roleName),
                      icon: Icons.shield_outlined,
                    ),
                    if (profile.status != null)
                      StatusChip(
                        tone: ProfileScreen._statusTone(profile.status),
                        rawStatus: profile.status,
                        icon: Icons.circle,
                      ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Light / Dark / System selector — persisted via [themeModeProvider] so the
/// choice survives restarts, mirroring the web dashboard's theme toggle.
class _AppearanceCard extends ConsumerWidget {
  const _AppearanceCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mode = ref.watch(themeModeProvider);
    return SectionCard(
      title: 'Appearance',
      icon: Icons.palette_outlined,
      child: SegmentedButton<ThemeMode>(
        showSelectedIcon: false,
        segments: const [
          ButtonSegment(
            value: ThemeMode.light,
            label: Text('Light'),
            icon: Icon(Icons.light_mode_outlined, size: 18),
          ),
          ButtonSegment(
            value: ThemeMode.dark,
            label: Text('Dark'),
            icon: Icon(Icons.dark_mode_outlined, size: 18),
          ),
          ButtonSegment(
            value: ThemeMode.system,
            label: Text('Auto'),
            icon: Icon(Icons.brightness_auto_outlined, size: 18),
          ),
        ],
        selected: {mode},
        onSelectionChanged: (selection) =>
            ref.read(themeModeProvider.notifier).setMode(selection.first),
      ),
    );
  }
}
