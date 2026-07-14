import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:skeletonizer/skeletonizer.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/brand_logo.dart';
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

  /// Content column cap so the dashboard stays readable on tablets and in
  /// landscape instead of stretching cards edge-to-edge.
  static const double _maxContentWidth = 760;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final summary = ref.watch(dashboardSummaryProvider);

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () => _refresh(ref),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: _maxContentWidth),
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.lg,
                  AppSpacing.md,
                  AppSpacing.lg,
                  AppSpacing.xxxl,
                ),
                children: [
                  _Greeting(
                    name: user?.name ?? 'there',
                    avatarUrl: user?.avatarUrl,
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  AsyncValueView<DashboardSummary>(
                    value: summary,
                    onRetry: () => ref.invalidate(dashboardSummaryProvider),
                    loading: const Skeletonizer(
                      child: _KpiGrid(
                        summary: _kPlaceholderSummary,
                        animate: false,
                      ),
                    ),
                    data: (s) => _FadeSlideIn(child: _KpiGrid(summary: s)),
                  ),
                  const SizedBox(height: AppSpacing.xl),
                  const _QuickActions(),
                  const SizedBox(height: AppSpacing.xl),
                  summary.maybeWhen(
                    data: (s) => _Funnel(stages: s.funnelStages),
                    orElse: () => const SizedBox.shrink(),
                  ),
                  const SizedBox(height: AppSpacing.xl),
                  const _UpcomingTasks(),
                  const SizedBox(height: AppSpacing.xl),
                  const _RecentNotifications(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Greeting extends StatelessWidget {
  const _Greeting({required this.name, this.avatarUrl});
  final String name;
  final String? avatarUrl;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hour = DateTime.now().hour;
    final part = hour < 12
        ? 'Good morning'
        : (hour < 18 ? 'Good afternoon' : 'Good evening');
    return Row(
      children: [
        const BrandLogo(size: 40),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '$part,',
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.headlineSmall,
              ),
            ],
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        const _NotificationBell(),
        const SizedBox(width: AppSpacing.md),
        AppAvatar(name: name, radius: 24, imageUrl: avatarUrl),
      ],
    );
  }
}

/// Bell icon + unread badge, mirroring the web header's notification button
/// (top-right, next to the avatar). Tapping opens the full-screen list.
///
/// Watches the unread count itself so a badge change repaints only this
/// widget, not the whole dashboard.
class _NotificationBell extends ConsumerWidget {
  const _NotificationBell();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unreadCount =
        ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;
    final scheme = Theme.of(context).colorScheme;
    return Badge(
      isLabelVisible: unreadCount > 0,
      label: Text(unreadCount > 9 ? '9+' : '$unreadCount'),
      child: IconButton.filledTonal(
        style: IconButton.styleFrom(
          backgroundColor: scheme.surfaceContainerHighest,
        ),
        tooltip: 'Notifications',
        onPressed: () => context.pushNamed(RouteNames.notifications),
        icon: const Icon(Icons.notifications_rounded),
      ),
    );
  }
}

/// Fake numbers rendered under the skeleton shimmer while the summary loads —
/// only their text metrics matter.
const _kPlaceholderSummary = DashboardSummary(
  activeLeadsCount: 24,
  totalLeadsCount: 60,
  activeDealsCount: 12,
  activeDealsValue: 120000000,
  weightedPipelineValue: 86000000,
  totalDealsValue: 240000000,
  pendingTasksCount: 8,
  overdueTasksCount: 2,
  funnelStages: [],
);

/// Fades + slides content in when data replaces the skeleton, so the swap
/// reads as one motion instead of a flash.
class _FadeSlideIn extends StatelessWidget {
  const _FadeSlideIn({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOutCubic,
      builder: (context, t, child) => Opacity(
        opacity: t,
        child: Transform.translate(
          offset: Offset(0, 12 * (1 - t)),
          child: child,
        ),
      ),
      child: child,
    );
  }
}

class _KpiGrid extends StatelessWidget {
  const _KpiGrid({required this.summary, this.animate = true});
  final DashboardSummary summary;
  final bool animate;

  @override
  Widget build(BuildContext context) {
    // A fixed aspect ratio ties tile height to tile width, which overflows on
    // narrow phones (320dp) and at large accessibility text scales. Instead,
    // derive the tile height from what's actually inside it — icon chip,
    // value, label, sub — scaling the text portion with the user's font size.
    final textScaler = MediaQuery.textScalerOf(context);
    // Fixed chrome: card padding (16 × 2) + icon chip (8+8 padding + 18 icon)
    // + minimum breathing room (the Spacer absorbs any extra) + value→label
    // gap. The text stack — value (32) + label (16) + sub (16) — scales with
    // the user's font size.
    const chrome = AppSpacing.lg * 2 + 34 + AppSpacing.sm + AppSpacing.xs;
    final tileExtent = chrome + textScaler.scale(32 + 16 + 16);
    return LayoutBuilder(
      builder: (context, constraints) {
        // Two columns on phones, four on tablet/landscape widths.
        final columns = constraints.maxWidth >= 560 ? 4 : 2;
        return GridView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            mainAxisSpacing: AppSpacing.md,
            crossAxisSpacing: AppSpacing.md,
            mainAxisExtent: tileExtent,
          ),
          children: [
            _KpiCard(
              label: 'Active leads',
              value: summary.activeLeadsCount,
              format: Formatters.compact,
              animate: animate,
              sub: 'of ${summary.totalLeadsCount} total',
              icon: Icons.people_alt_rounded,
              color: AppColors.brandSeed,
            ),
            _KpiCard(
              label: 'Pending tasks',
              value: summary.pendingTasksCount,
              format: Formatters.compact,
              animate: animate,
              sub: '${summary.overdueTasksCount} overdue',
              icon: Icons.checklist_rounded,
              color: summary.overdueTasksCount > 0
                  ? AppColors.danger
                  : AppColors.success,
            ),
            _KpiCard(
              label: 'Active deals',
              value: summary.activeDealsCount,
              format: Formatters.compact,
              animate: animate,
              sub: Formatters.money(summary.activeDealsValue),
              icon: Icons.handshake_rounded,
              color: Theme.of(context).colorScheme.tertiary,
            ),
            _KpiCard(
              label: 'Weighted pipeline',
              value: summary.weightedPipelineValue,
              format: Formatters.money,
              animate: animate,
              sub: '${Formatters.money(summary.totalDealsValue)} total',
              icon: Icons.trending_up_rounded,
              color: AppColors.info,
            ),
          ],
        );
      },
    );
  }
}

class _KpiCard extends StatelessWidget {
  const _KpiCard({
    required this.label,
    required this.value,
    required this.format,
    required this.sub,
    required this.icon,
    required this.color,
    this.animate = true,
  });

  final String label;
  final num value;
  final String Function(num?) format;
  final String sub;
  final IconData icon;
  final Color color;
  final bool animate;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final valueStyle = theme.textTheme.headlineSmall?.copyWith(
      fontWeight: FontWeight.w800,
      letterSpacing: -0.5,
    );
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
          // Count-up on first appearance; static under the skeleton shimmer.
          if (animate)
            TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: value.toDouble()),
              duration: const Duration(milliseconds: 700),
              curve: Curves.easeOutCubic,
              builder: (context, v, _) => Text(
                format(value is int ? v.round() : v),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: valueStyle,
              ),
            )
          else
            Text(
              format(value),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: valueStyle,
            ),
          const SizedBox(height: AppSpacing.xs),
          Text(
            label.toUpperCase(),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.labelSmall?.copyWith(
              fontWeight: FontWeight.w700,
              letterSpacing: 0.4,
              color: scheme.onSurfaceVariant,
            ),
          ),
          Text(
            sub,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.labelSmall?.copyWith(color: scheme.outline),
          ),
        ],
      ),
    );
  }
}

class _QuickActions extends StatelessWidget {
  const _QuickActions();

  @override
  Widget build(BuildContext context) {
    final actions = [
      (
        Icons.person_add_alt_1_rounded,
        'New lead',
        () => context.pushNamed(RouteNames.leadCreate),
      ),
      (
        Icons.people_alt_rounded,
        'Leads',
        () => context.goNamed(RouteNames.leads),
      ),
      (
        Icons.contacts_rounded,
        'Customers',
        () => context.pushNamed(RouteNames.customers),
      ),
      (
        Icons.checklist_rounded,
        'Tasks',
        () => context.goNamed(RouteNames.tasks),
      ),
      (
        Icons.notifications_rounded,
        'Alerts',
        () => context.pushNamed(RouteNames.notifications),
      ),
      (
        // Quotations left the bottom nav for the More hub, so it is a
        // full-screen push now — `go` would replace the shell and strip the
        // bottom bar with nothing to pop back to.
        Icons.receipt_long_outlined,
        'Quotations',
        () => context.pushNamed(RouteNames.quotations),
      ),
      (Icons.verified_outlined, 'SLA', () => context.pushNamed(RouteNames.sla)),
      (
        Icons.alarm_outlined,
        'Reminders',
        () => context.pushNamed(RouteNames.reminders),
      ),
    ];
    // Horizontally scrollable so the row can grow past 4 icons without
    // squishing tap targets below 48dp. The label line scales with the user's
    // font size; everything else in the tile is fixed chrome.
    final height = 76 + MediaQuery.textScalerOf(context).scale(16);
    return SizedBox(
      height: height,
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
  const _ActionButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(AppRadii.lg),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
        decoration: BoxDecoration(
          color: theme.colorScheme.primaryContainer.withValues(alpha: 0.4),
          borderRadius: BorderRadius.circular(AppRadii.lg),
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
              style: theme.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.w600,
              ),
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
    final maxCount = stages
        .map((s) => s.count)
        .fold<int>(1, (a, b) => b > a ? b : a);
    final theme = Theme.of(context);
    return SectionCard(
      title: 'Sales funnel',
      icon: Icons.filter_alt_outlined,
      child: Column(
        children: [
          for (final s in stages)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          Formatters.humanizeEnum(s.stage),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      const SizedBox(width: AppSpacing.sm),
                      Text(
                        '${s.count} · ${Formatters.money(s.value)}',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(AppRadii.pill),
                    child: LinearProgressIndicator(
                      value: (s.count / maxCount).clamp(0.02, 1.0),
                      minHeight: 8,
                      backgroundColor:
                          theme.colorScheme.surfaceContainerHighest,
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
          loading: () => const _ListSkeleton(),
          error: (_, _) => const SizedBox.shrink(),
          data: (tasks) => tasks.isEmpty
              ? const _MiniEmpty(message: 'No open tasks. Nice work!')
              : _FadeSlideIn(
                  child: Column(
                    children: [
                      for (final t in tasks)
                        Padding(
                          padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                          child: TaskCard(task: t),
                        ),
                    ],
                  ),
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
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionHeader(
          title: 'Recent notifications',
          onSeeAll: () => context.pushNamed(RouteNames.notifications),
        ),
        const SizedBox(height: 8),
        async.when(
          loading: () => const _ListSkeleton(count: 3),
          error: (_, _) => const SizedBox.shrink(),
          data: (items) => items.isEmpty
              ? const _MiniEmpty(message: 'No notifications yet.')
              : SectionCard(
                  padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
                  child: Column(
                    children: [
                      for (var i = 0; i < items.length; i++) ...[
                        if (i > 0) const Divider(height: 1),
                        ListTile(
                          dense: true,
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 4,
                          ),
                          leading: Icon(
                            items[i].icon,
                            color: theme.colorScheme.primary,
                            size: 20,
                          ),
                          title: Text(
                            items[i].title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontWeight: items[i].isRead
                                  ? FontWeight.w500
                                  : FontWeight.w700,
                            ),
                          ),
                          subtitle: Text(
                            Formatters.relative(items[i].createdAt),
                            style: theme.textTheme.labelSmall,
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
  const _SectionHeader({required this.title, required this.onSeeAll});
  final String title;
  final VoidCallback onSeeAll;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(
              context,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
        ),
        TextButton(onPressed: onSeeAll, child: const Text('See all')),
      ],
    );
  }
}

/// Shimmering placeholder rows shown while a preview section loads — avoids
/// spinners and layout jumps (the bones roughly match the real card heights).
class _ListSkeleton extends StatelessWidget {
  const _ListSkeleton({this.count = 2});
  final int count;

  @override
  Widget build(BuildContext context) {
    return Skeletonizer(
      child: Column(
        children: [
          for (var i = 0; i < count; i++)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.sm),
              child: SectionCard(
                padding: const EdgeInsets.all(AppSpacing.lg),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Placeholder task title for the shimmer',
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Meta line · schedule · assignee',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _MiniEmpty extends StatelessWidget {
  const _MiniEmpty({required this.message});
  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
      child: Text(
        message,
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }
}
