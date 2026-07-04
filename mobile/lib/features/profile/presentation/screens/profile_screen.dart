import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/profile_models.dart';
import '../../data/profile_repository.dart';

/// UC-24.12 View Profile + entry points for Change Password (UC-24.13) and
/// Logout (UC-24.9).
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

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
            padding: const EdgeInsets.fromLTRB(16, 24, 16, 32),
            children: [
              Center(
                child: Column(
                  children: [
                    AppAvatar(name: profile.fullName, radius: 44, imageUrl: profile.avatarUrl),
                    const SizedBox(height: 14),
                    Text(profile.fullName,
                        style: Theme.of(context)
                            .textTheme
                            .titleLarge
                            ?.copyWith(fontWeight: FontWeight.w700)),
                    const SizedBox(height: 4),
                    Text(profile.email,
                        style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
                    if (profile.roleName != null) ...[
                      const SizedBox(height: 10),
                      Chip(
                        label: Text(Formatters.humanizeEnum(profile.roleName)),
                        visualDensity: VisualDensity.compact,
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 24),
              SectionCard(
                title: 'Account',
                icon: Icons.badge_outlined,
                child: Column(
                  children: [
                    InfoRow(label: 'Phone', value: profile.phone, icon: Icons.phone_outlined),
                    InfoRow(label: 'Status', value: Formatters.humanizeEnum(profile.status), icon: Icons.verified_user_outlined),
                    InfoRow(label: 'Last login', value: Formatters.dateTime(profile.lastLoginAt), icon: Icons.login_rounded),
                    InfoRow(label: 'Member since', value: Formatters.date(profile.createdAt), icon: Icons.event_outlined),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Card(
                elevation: 0,
                margin: EdgeInsets.zero,
                color: Theme.of(context).colorScheme.surfaceContainerLow,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                child: Column(
                  children: [
                    ListTile(
                      leading: const Icon(Icons.lock_outline_rounded),
                      title: const Text('Change password'),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: () => context.pushNamed(RouteNames.changePassword),
                    ),
                    const Divider(height: 1, indent: 16, endIndent: 16),
                    ListTile(
                      leading: Icon(Icons.logout_rounded, color: Theme.of(context).colorScheme.error),
                      title: Text('Log out', style: TextStyle(color: Theme.of(context).colorScheme.error)),
                      onTap: () => _confirmLogout(context, ref),
                    ),
                  ],
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
        content: const Text('You will need to sign in again to access your workspace.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Log out')),
        ],
      ),
    );
    if (ok != true) return;
    await ref.read(authControllerProvider.notifier).logout();
    // The router's redirect guard reacts to the session change and returns to
    // login automatically; no manual navigation needed.
  }
}
