import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../auth/presentation/providers/auth_controller.dart';

/// The "More" tab — a hub for every module that does not earn a bottom-nav slot.
///
/// Only modules that actually exist are listed. A tile that opens a stub is
/// worse than no tile, so new entries land here as their screens land.
class MoreScreen extends ConsumerWidget {
  const MoreScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('More'),
        actions: [
          IconButton(
            tooltip: 'Notifications',
            onPressed: () => context.pushNamed(RouteNames.notifications),
            icon: const Icon(Icons.notifications_none_rounded),
          ),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.md,
          AppSpacing.lg,
          AppSpacing.xxxl,
        ),
        children: [
          _ProfileHeader(
            name: user?.name ?? 'Signed in',
            email: user?.email,
            avatarUrl: user?.avatarUrl,
            onTap: () => context.pushNamed(RouteNames.profile),
          ),
          const SizedBox(height: AppSpacing.lg),

          SectionCard(
            title: 'Sales',
            icon: Icons.trending_up_rounded,
            padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
            child: Column(
              children: [
                _MoreTile(
                  icon: Icons.view_kanban_outlined,
                  label: 'Pipeline',
                  onTap: () => context.pushNamed(RouteNames.pipeline),
                ),
                _MoreTile(
                  icon: Icons.request_quote_outlined,
                  label: 'Quotations',
                  onTap: () => context.pushNamed(RouteNames.quotations),
                ),
                _MoreTile(
                  icon: Icons.badge_outlined,
                  label: 'Customers',
                  onTap: () => context.pushNamed(RouteNames.customers),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.md),

          SectionCard(
            title: 'Reservations',
            icon: Icons.hotel_outlined,
            padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
            child: Column(
              children: [
                _MoreTile(
                  icon: Icons.event_available_outlined,
                  label: 'Bookings',
                  onTap: () => context.pushNamed(RouteNames.bookings),
                ),
                _MoreTile(
                  icon: Icons.payments_outlined,
                  label: 'Payments',
                  onTap: () => context.pushNamed(RouteNames.payments),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.md),

          SectionCard(
            title: 'Operations',
            icon: Icons.schedule_rounded,
            padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
            child: Column(
              children: [
                _MoreTile(
                  icon: Icons.notifications_none_rounded,
                  label: 'Notifications',
                  onTap: () => context.pushNamed(RouteNames.notifications),
                ),
                _MoreTile(
                  icon: Icons.alarm_rounded,
                  label: 'Reminders',
                  onTap: () => context.pushNamed(RouteNames.reminders),
                ),
                _MoreTile(
                  icon: Icons.timer_outlined,
                  label: 'SLA monitoring',
                  onTap: () => context.pushNamed(RouteNames.sla),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.md),

          SectionCard(
            title: 'Account',
            icon: Icons.person_outline_rounded,
            padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
            child: Column(
              children: [
                _MoreTile(
                  icon: Icons.manage_accounts_outlined,
                  label: 'Profile & settings',
                  onTap: () => context.pushNamed(RouteNames.profile),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.xl),

          Center(
            child: Text(
              'Leadora CRM',
              style: theme.textTheme.labelMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ProfileHeader extends StatelessWidget {
  const _ProfileHeader({
    required this.name,
    required this.onTap,
    this.email,
    this.avatarUrl,
  });

  final String name;
  final String? email;
  final String? avatarUrl;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            children: [
              AppAvatar(name: name, radius: 26, imageUrl: avatarUrl),
              const SizedBox(width: AppSpacing.lg),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    if (email != null && email!.isNotEmpty) ...[
                      const SizedBox(height: 2),
                      Text(
                        email!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              Icon(Icons.chevron_right_rounded, color: scheme.onSurfaceVariant),
            ],
          ),
        ),
      ),
    );
  }
}

class _MoreTile extends StatelessWidget {
  const _MoreTile({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return ListTile(
      // Keeps the row at the 48dp minimum tap target even at the smallest
      // text scale.
      minTileHeight: 48,
      leading: Icon(icon, color: scheme.onSurfaceVariant),
      title: Text(label),
      trailing: Icon(
        Icons.chevron_right_rounded,
        color: scheme.onSurfaceVariant,
      ),
      onTap: onTap,
    );
  }
}
