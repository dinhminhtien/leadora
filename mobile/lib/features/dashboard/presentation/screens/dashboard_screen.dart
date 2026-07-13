import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../../notification/presentation/providers/notification_providers.dart';
import '../../../task/presentation/screens/task_list_screen.dart';
import '../../data/dashboard_models.dart';
import '../../data/dashboard_repository.dart';
import '../providers/dashboard_providers.dart';

/// UC-24.1 — Mobile Sales Workspace. KPIs, funnel, quick actions and previews.
class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  Future<void> _refresh(WidgetRef ref) async {
    ref.invalidate(dashboardSummaryProvider);
    ref.invalidate(upcomingTasksProvider);
    ref.invalidate(recentNotificationsProvider);
    await Future.wait([
      ref.read(dashboardSummaryProvider.future),
      ref.read(upcomingTasksProvider.future),
      ref.read(recentNotificationsProvider.future),
    ]);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final summary = ref.watch(dashboardSummaryProvider);
    final unread = ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () => _refresh(ref),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
            children: [
              _Greeting(name: user?.name ?? 'there', unreadCount: unread),
              const SizedBox(height: 16),
              AsyncValueView<DashboardSummary>(
                value: summary,
                onRetry: () => ref.invalidate(dashboardSummaryProvider),
                loading: const SizedBox(
                  height: 220,
                  child: Center(child: CircularProgressIndicator()),
                ),
                data: (s) => _KpiGrid(summary: s),
              ),
              const SizedBox(height: 20),
              _QuickActions(),
              const SizedBox(height: 20),
              summary.maybeWhen(
                data: (s) => _Funnel(stages: s.funnelStages),
                orElse: () => const SizedBox.shrink(),
              ),
              const SizedBox(height: 20),
              const _UpcomingTasks(),
              const SizedBox(height: 20),
              const _RecentNotifications(),
            ],
          ),
        ),
      ),
    );
  }
}

class _Greeting extends StatelessWidget {
  const _Greeting({required this.name, required this.unreadCount});
  final String name;
  final int unreadCount;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hour = DateTime.now().hour;
    final part = hour < 12 ? 'Good morning' : (hour < 18 ? 'Good afternoon' : 'Good evening');
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('$part,',
                  style: theme.textTheme.bodyLarge
                      ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
              const SizedBox(height: 2),
              Text(name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.headlineSmall),
            ],
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        _NotificationBell(unreadCount: unreadCount),
        const SizedBox(width: AppSpacing.md),
        AppAvatar(name: name, radius: 24),
      ],
    );
  }
}

/// Bell icon + unread badge, mirroring the web header's notification button
/// (top-right, next to the avatar). Tapping opens the full-screen list.
class _NotificationBell extends StatelessWidget {
  const _NotificationBell({required this.unreadCount});
  final int unreadCount;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Badge(
      isLabelVisible: unreadCount > 0,
      label: Text(unreadCount > 9 ? '9+' : '$unreadCount'),
      child: IconButton.filledTonal(
        style: IconButton.styleFrom(backgroundColor: scheme.surfaceContainerHighest),
        tooltip: 'Notifications',
        onPressed: () => context.pushNamed(RouteNames.notifications),
        icon: const Icon(Icons.notifications_rounded),
      ),
    );
  }
}

class _KpiGrid extends StatelessWidget {
  const _KpiGrid({required this.summary});
  final DashboardSummary summary;

  @override
  Widget build(BuildContext context) {
    return GridView.count(
      crossAxisCount: 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: AppSpacing.md,
      crossAxisSpacing: AppSpacing.md,
      // Headroom for the icon chip + large value + label + sub. Kept generous
      // so it never clips at larger text scale (a 1.55 card overflowed 5.5px).
      childAspectRatio: 1.28,
      children: [
        _KpiCard(
          label: 'Active leads',
          value: Formatters.compact(summary.activeLeadsCount),
          sub: 'of ${summary.totalLeadsCount} total',
          icon: Icons.people_alt_rounded,
          color: const Color(0xFF2563EB),
        ),
        _KpiCard(
          label: 'Pending tasks',
          value: Formatters.compact(summary.pendingTasksCount),
          sub: '${summary.overdueTasksCount} overdue',
          icon: Icons.checklist_rounded,
          color: summary.overdueTasksCount > 0 ? const Color(0xFFDC2626) : const Color(0xFF16A34A),
        ),
        _KpiCard(
          label: 'Active deals',
          value: Formatters.compact(summary.activeDealsCount),
          sub: Formatters.money(summary.activeDealsValue),
          icon: Icons.handshake_rounded,
          color: const Color(0xFF7C3AED),
        ),
        _KpiCard(
          label: 'Weighted pipeline',
          value: Formatters.money(summary.weightedPipelineValue),
          sub: '${Formatters.money(summary.totalDealsValue)} total',
          icon: Icons.trending_up_rounded,
          color: const Color(0xFF0EA5E9),
        ),
      ],
    );
  }
}

class _KpiCard extends StatelessWidget {
  const _KpiCard({
    required this.label,
    required this.value,
    required this.sub,
    required this.icon,
    required this.color,
  });

  final String label;
  final String value;
  final String sub;
  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: scheme.outlineVariant.withValues(alpha: 0.6)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(AppSpacing.sm),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(AppRadii.sm),
            ),
            child: Icon(icon, size: 18, color: color),
          ),
          const Spacer(),
          Text(value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.w800,
                letterSpacing: -0.5,
              )),
          const SizedBox(height: AppSpacing.xs),
          Text(label.toUpperCase(),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.w700,
                letterSpacing: 0.4,
                color: scheme.onSurfaceVariant,
              )),
          Text(sub,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.labelSmall?.copyWith(color: scheme.outline)),
        ],
      ),
    );
  }
}

class _QuickActions extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final actions = [
      (Icons.person_add_alt_1_rounded, 'New lead', () => context.pushNamed(RouteNames.leadCreate)),
      (Icons.people_alt_rounded, 'Leads', () => context.goNamed(RouteNames.leads)),
      (Icons.checklist_rounded, 'Tasks', () => context.goNamed(RouteNames.tasks)),
      (Icons.notifications_rounded, 'Alerts', () => context.pushNamed(RouteNames.notifications)),
      (Icons.receipt_long_outlined, 'Quotations', () => context.goNamed(RouteNames.quotations)),
      (Icons.verified_outlined, 'SLA', () => context.pushNamed(RouteNames.sla)),
      (Icons.alarm_outlined, 'Reminders', () => context.pushNamed(RouteNames.reminders)),
    ];
    // Horizontally scrollable so the row can grow past 4 icons without
    // squishing tap targets below 48dp.
    return SizedBox(
      height: 92,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: actions.length,
        separatorBuilder: (_, _) => const SizedBox(width: AppSpacing.sm),
        itemBuilder: (context, i) {
          final a = actions[i];
          return SizedBox(
            width: 84,
            child: _ActionButton(icon: a.$1, label: a.$2, onTap: a.$3),
          );
        },
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          color: theme.colorScheme.primaryContainer.withValues(alpha: 0.4),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: theme.colorScheme.primary),
            const SizedBox(height: 6),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: theme.textTheme.labelSmall?.copyWith(fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    );
  }
}

class _Funnel extends StatelessWidget {
  const _Funnel({required this.stages});
  final List<StageSummary> stages;

  @override
  Widget build(BuildContext context) {
    if (stages.isEmpty) return const SizedBox.shrink();
    final maxCount = stages.map((s) => s.count).fold<int>(1, (a, b) => b > a ? b : a);
    final theme = Theme.of(context);
    return SectionCard(
      title: 'Sales funnel',
      icon: Icons.filter_alt_outlined,
      child: Column(
        children: [
          for (final s in stages)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(Formatters.humanizeEnum(s.stage),
                          style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                      Text('${s.count} · ${Formatters.money(s.value)}',
                          style: theme.textTheme.labelSmall
                              ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                    ],
                  ),
                  const SizedBox(height: 6),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(999),
                    child: LinearProgressIndicator(
                      value: (s.count / maxCount).clamp(0.02, 1.0),
                      minHeight: 8,
                      backgroundColor: theme.colorScheme.surfaceContainerHighest,
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _UpcomingTasks extends ConsumerWidget {
  const _UpcomingTasks();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(upcomingTasksProvider);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionHeader(
          title: 'Upcoming tasks',
          onSeeAll: () => context.goNamed(RouteNames.tasks),
        ),
        const SizedBox(height: 8),
        async.when(
          loading: () => const Padding(
            padding: EdgeInsets.all(20),
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (_, _) => const SizedBox.shrink(),
          data: (tasks) => tasks.isEmpty
              ? const _MiniEmpty(message: 'No open tasks. Nice work!')
              : Column(
                  children: [
                    for (final t in tasks)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: TaskCard(task: t),
                      ),
                  ],
                ),
        ),
      ],
    );
  }
}

class _RecentNotifications extends ConsumerWidget {
  const _RecentNotifications();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(recentNotificationsProvider);
    final theme = Theme.of(context);
    final unreadCount = async.valueOrNull?.where((n) => !n.isRead).length ?? 0;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionHeader(
          title: 'Recent notifications',
          count: unreadCount,
          onSeeAll: () => context.pushNamed(RouteNames.notifications),
        ),
        const SizedBox(height: 8),
        async.when(
          loading: () => const Padding(
            padding: EdgeInsets.all(20),
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (_, _) => const SizedBox.shrink(),
          data: (items) => items.isEmpty
              ? const _MiniEmpty(message: 'No notifications yet.')
              : SectionCard(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Column(
                    children: [
                      for (var i = 0; i < items.length; i++) ...[
                        if (i > 0) const Divider(height: 1),
                        // Unread rows get a tinted background + a leading dot;
                        // read rows fade — mirrors the web preview dropdown.
                        Container(
                          color: items[i].isRead
                              ? null
                              : theme.colorScheme.primaryContainer.withValues(alpha: 0.10),
                          child: Opacity(
                            opacity: items[i].isRead ? 0.7 : 1,
                            child: ListTile(
                              dense: true,
                              contentPadding: const EdgeInsets.symmetric(horizontal: 4),
                              leading: Icon(items[i].icon, color: theme.colorScheme.primary, size: 20),
                              title: Text(items[i].title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                      fontWeight:
                                          items[i].isRead ? FontWeight.w500 : FontWeight.w700)),
                              subtitle: Text(Formatters.relative(items[i].createdAt),
                                  style: theme.textTheme.labelSmall),
                              trailing: items[i].isRead
                                  ? null
                                  : Container(
                                      width: 8,
                                      height: 8,
                                      decoration: BoxDecoration(
                                        color: theme.colorScheme.primary,
                                        shape: BoxShape.circle,
                                      ),
                                    ),
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
        ),
      ],
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title, required this.onSeeAll, this.count = 0});
  final String title;
  final VoidCallback onSeeAll;
  final int count;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(title,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
            if (count > 0) ...[
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.error,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  count > 9 ? '9+' : '$count',
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: Theme.of(context).colorScheme.onError,
                        fontWeight: FontWeight.w700,
                      ),
                ),
              ),
            ],
          ],
        ),
        TextButton(onPressed: onSeeAll, child: const Text('See all')),
      ],
    );
  }
}

class _MiniEmpty extends StatelessWidget {
  const _MiniEmpty({required this.message});
  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Text(message,
          style: Theme.of(context)
              .textTheme
              .bodyMedium
              ?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
    );
  }
}
