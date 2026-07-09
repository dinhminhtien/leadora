import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../task/data/task_models.dart';
import '../../../task/presentation/screens/task_list_screen.dart' show TaskCard;
import '../../data/customer_models.dart';
import '../providers/customer_providers.dart';

/// Customer detail — profile header, task stats and Overview / History / Info
/// tabs. Mirrors the web Customer Profile detail screen (unified activity
/// timeline of tasks + deals + bookings + quotations).
class CustomerDetailScreen extends ConsumerWidget {
  const CustomerDetailScreen({
    super.key,
    required this.customerId,
    this.initial,
  });

  final String customerId;
  final Customer? initial;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(customerDetailProvider(customerId));
    final customer = async.valueOrNull ?? initial;

    if (customer == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Customer')),
        body: AsyncValueView<Customer>(
          value: async,
          onRetry: () => ref.invalidate(customerDetailProvider(customerId)),
          data: (_) => const SizedBox.shrink(),
        ),
      );
    }

    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Customer'),
          actions: [
            IconButton(
              tooltip: 'Edit',
              icon: const Icon(Icons.edit_outlined),
              onPressed: () => context.pushNamed(
                RouteNames.customerEdit,
                pathParameters: {'id': customer.customerId},
                extra: customer,
              ),
            ),
            const SizedBox(width: 4),
          ],
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Overview'),
              Tab(text: 'History'),
              Tab(text: 'Info'),
            ],
          ),
        ),
        body: LayoutBuilder(
          builder: (context, constraints) {
            return Column(
              children: [
                // On short viewports (landscape phones, large text scale) the
                // header + stats block can outgrow the screen; cap it to half
                // the height and let it scroll instead of overflowing.
                ConstrainedBox(
                  constraints: BoxConstraints(
                    maxHeight: constraints.maxHeight / 2,
                  ),
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _ProfileHeader(customer: customer),
                        _TaskStatsRow(customerId: customerId),
                      ],
                    ),
                  ),
                ),
                const Divider(height: 1),
                Expanded(
                  child: TabBarView(
                    children: [
                      _OverviewTab(customer: customer),
                      _HistoryTab(customerId: customerId),
                      _InfoTab(customer: customer),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _ProfileHeader extends StatelessWidget {
  const _ProfileHeader({required this.customer});

  final Customer customer;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.lg,
        AppSpacing.lg,
        AppSpacing.md,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AppAvatar(name: customer.fullName, radius: 28),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  customer.fullName,
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 6),
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  children: [
                    StatusChip(
                      tone: customer.customerType.tone,
                      label: customer.customerType.label,
                      icon: customer.customerType.icon,
                      dense: true,
                    ),
                    StatusChip(
                      tone: customer.status.tone,
                      rawStatus: customer.status.wire,
                      dense: true,
                    ),
                  ],
                ),
                if (customer.isCorporate &&
                    customer.companyName != null &&
                    customer.companyName!.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Icon(
                        Icons.apartment_rounded,
                        size: AppIconSize.sm,
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                      const SizedBox(width: 6),
                      Flexible(
                        child: Text(
                          customer.companyName!,
                          style: theme.textTheme.bodyMedium,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 8),
                _ContactLine(icon: Icons.phone_outlined, value: customer.phone),
                _ContactLine(icon: Icons.email_outlined, value: customer.email),
                _ContactLine(
                  icon: Icons.location_on_outlined,
                  value: customer.address,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ContactLine extends StatelessWidget {
  const _ContactLine({required this.icon, required this.value});

  final IconData icon;
  final String? value;

  @override
  Widget build(BuildContext context) {
    if (value == null || value!.trim().isEmpty) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.xs),
      child: Row(
        children: [
          Icon(
            icon,
            size: AppIconSize.xs,
            color: theme.colorScheme.onSurfaceVariant,
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              value!,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}

/// Open / overdue / completed / total counts derived from the customer's tasks.
class _TaskStatsRow extends ConsumerWidget {
  const _TaskStatsRow({required this.customerId});

  final String customerId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tasks = ref.watch(customerTasksProvider(customerId)).valueOrNull;
    if (tasks == null) return const SizedBox(height: 4);
    final open = tasks
        .where((t) => t.status == TaskStatus.open && !t.isOverdue)
        .length;
    final overdue = tasks.where((t) => t.isOverdue).length;
    final completed = tasks
        .where((t) => t.status == TaskStatus.completed)
        .length;

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        0,
        AppSpacing.lg,
        AppSpacing.md,
      ),
      child: Row(
        children: [
          _MiniStat(label: 'Open', value: open, tone: StatusTone.info),
          _MiniStat(label: 'Overdue', value: overdue, tone: StatusTone.danger),
          _MiniStat(label: 'Done', value: completed, tone: StatusTone.success),
          _MiniStat(
            label: 'Total',
            value: tasks.length,
            tone: StatusTone.neutral,
          ),
        ],
      ),
    );
  }
}

class _MiniStat extends StatelessWidget {
  const _MiniStat({
    required this.label,
    required this.value,
    required this.tone,
  });

  final String label;
  final int value;
  final StatusTone tone;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Expanded(
      child: Container(
        margin: const EdgeInsets.only(right: AppSpacing.sm),
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(AppRadii.md),
          border: Border.all(
            color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
          ),
        ),
        child: Column(
          children: [
            Text(
              '$value',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w800,
              ),
            ),
            Text(
              label.toUpperCase(),
              style: theme.textTheme.labelSmall?.copyWith(
                letterSpacing: 0.3,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _OverviewTab extends ConsumerWidget {
  const _OverviewTab({required this.customer});

  final Customer customer;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final async = ref.watch(customerTasksProvider(customer.customerId));

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(customerTasksProvider(customer.customerId));
        ref.invalidate(customerDetailProvider(customer.customerId));
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.xxxl,
        ),
        children: [
          async.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(AppSpacing.xxl),
              child: Center(child: CircularProgressIndicator()),
            ),
            error: (_, _) => const SizedBox.shrink(),
            data: (tasks) {
              final overdue = tasks.where((t) => t.isOverdue).toList();
              final open = tasks
                  .where((t) => t.status == TaskStatus.open && !t.isOverdue)
                  .toList();
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (overdue.isNotEmpty) ...[
                    _OverdueBanner(count: overdue.length),
                    const SizedBox(height: 16),
                    ...overdue.map(
                      (t) => Padding(
                        padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                        child: TaskCard(task: t),
                      ),
                    ),
                  ],
                  Text(
                    'Open tasks',
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 8),
                  if (open.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(
                        vertical: AppSpacing.lg,
                      ),
                      child: Text(
                        'No open tasks.',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    )
                  else
                    ...open.map(
                      (t) => Padding(
                        padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                        child: TaskCard(task: t),
                      ),
                    ),
                ],
              );
            },
          ),
          const SizedBox(height: 8),
          SectionCard(
            title: 'Customer details',
            icon: Icons.info_outline_rounded,
            child: Column(
              children: [
                InfoRow(label: 'Assigned to', value: customer.assignedUserName),
                InfoRow(label: 'Created by', value: customer.createdByName),
                InfoRow(
                  label: 'Customer since',
                  value: Formatters.date(customer.createdAt),
                ),
                if (customer.leadId != null)
                  const InfoRow(label: 'Source', value: 'Converted from lead'),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _OverdueBanner extends StatelessWidget {
  const _OverdueBanner({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: theme.colorScheme.errorContainer.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(AppRadii.md),
      ),
      child: Row(
        children: [
          Icon(Icons.warning_amber_rounded, color: theme.colorScheme.error),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              '$count overdue task${count > 1 ? 's' : ''} need attention',
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w600,
                color: theme.colorScheme.error,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Unified activity timeline: follow-up tasks + deals + bookings + quotations,
/// merged newest-first and grouped by month — mirrors the web history tab.
class _HistoryTab extends ConsumerWidget {
  const _HistoryTab({required this.customerId});

  final String customerId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tasksAsync = ref.watch(customerTasksProvider(customerId));
    final historyAsync = ref.watch(customerHistoryProvider(customerId));

    final tasks = tasksAsync.valueOrNull ?? const [];
    final history = historyAsync.valueOrNull ?? const [];

    if (historyAsync.isLoading && historyAsync.valueOrNull == null) {
      return const Center(child: CircularProgressIndicator());
    }

    final entries = <_TimelineEntry>[
      ...tasks.map((t) => _TimelineEntry(ts: t.createdAt ?? t.endAt, task: t)),
      ...history.map((h) => _TimelineEntry(ts: h.createdAt, history: h)),
    ]..sort((a, b) => (b.ts ?? DateTime(0)).compareTo(a.ts ?? DateTime(0)));

    if (entries.isEmpty) {
      return RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(customerHistoryProvider(customerId));
          ref.invalidate(customerTasksProvider(customerId));
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(
                vertical: AppSpacing.xxxl * 2,
              ),
              child: Center(
                child: Text(
                  'No service history yet.',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ),
          ],
        ),
      );
    }

    // Group by "Month Year".
    final groups = <String, List<_TimelineEntry>>{};
    for (final e in entries) {
      final label = e.ts == null ? 'Earlier' : Formatters.monthYear(e.ts);
      groups.putIfAbsent(label, () => []).add(e);
    }

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(customerHistoryProvider(customerId));
        ref.invalidate(customerTasksProvider(customerId));
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.xxxl,
        ),
        children: [
          for (final entry in groups.entries) ...[
            Padding(
              padding: const EdgeInsets.only(
                bottom: AppSpacing.sm,
                top: AppSpacing.xs,
              ),
              child: Text(
                entry.key.toUpperCase(),
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  letterSpacing: 0.6,
                  fontWeight: FontWeight.w700,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            for (final e in entry.value)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                child: e.task != null
                    ? _TaskTimelineTile(task: e.task!)
                    : _HistoryTile(item: e.history!),
              ),
          ],
        ],
      ),
    );
  }
}

class _TimelineEntry {
  _TimelineEntry({this.ts, this.task, this.history});
  final DateTime? ts;
  final Task? task;
  final CustomerHistoryItem? history;
}

class _TaskTimelineTile extends StatelessWidget {
  const _TaskTimelineTile({required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SectionCard(
      padding: const EdgeInsets.all(AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                Icons.checklist_rounded,
                size: AppIconSize.sm,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(width: 8),
              const StatusChip(
                tone: StatusTone.brand,
                label: 'Task',
                dense: true,
              ),
              const SizedBox(width: 6),
              StatusChip(
                tone: task.isOverdue ? StatusTone.danger : task.status.tone,
                label: task.isOverdue
                    ? 'Overdue'
                    : Formatters.humanizeEnum(task.status.wire),
                dense: true,
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            task.title,
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w600,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          if (task.resultNote != null &&
              task.resultNote!.trim().isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              '“${task.resultNote!}”',
              style: theme.textTheme.bodySmall?.copyWith(
                fontStyle: FontStyle.italic,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ],
          const SizedBox(height: 6),
          Text(
            [
              Formatters.dateTime(task.createdAt ?? task.endAt),
              if (task.assignedUserName != null) task.assignedUserName!,
            ].join(' · '),
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.outline,
            ),
          ),
        ],
      ),
    );
  }
}

class _HistoryTile extends StatelessWidget {
  const _HistoryTile({required this.item});

  final CustomerHistoryItem item;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SectionCard(
      padding: const EdgeInsets.all(AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                item.type.icon,
                size: AppIconSize.sm,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(width: 8),
              StatusChip(
                tone: item.type.tone,
                label: item.type.label,
                dense: true,
              ),
              if (item.status != null) ...[
                const SizedBox(width: 6),
                StatusChip(
                  tone: StatusTone.neutral,
                  label: Formatters.humanizeEnum(item.status),
                  dense: true,
                ),
              ],
              const Spacer(),
              if (item.amount != null)
                Text(
                  Formatters.money(item.amount),
                  style: theme.textTheme.labelLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            item.title,
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w600,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          if (item.stage != null) ...[
            const SizedBox(height: 2),
            Text(
              Formatters.humanizeEnum(item.stage),
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
          if (item.checkIn != null) ...[
            const SizedBox(height: 4),
            Row(
              children: [
                Icon(
                  Icons.event_outlined,
                  size: AppIconSize.xs,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 4),
                Text(
                  item.checkOut != null
                      ? '${item.checkIn} → ${item.checkOut}'
                      : item.checkIn!,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ],
          if (item.notes != null && item.notes!.trim().isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              item.notes!,
              style: theme.textTheme.bodySmall?.copyWith(
                fontStyle: FontStyle.italic,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ],
          if (item.createdAt != null) ...[
            const SizedBox(height: 6),
            Text(
              Formatters.dateTime(item.createdAt),
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.outline,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _InfoTab extends StatelessWidget {
  const _InfoTab({required this.customer});

  final Customer customer;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.lg,
        AppSpacing.lg,
        AppSpacing.xxxl,
      ),
      children: [
        SectionCard(
          title: 'Contact information',
          icon: Icons.contact_page_outlined,
          child: Column(
            children: [
              InfoRow(label: 'Full name', value: customer.fullName),
              InfoRow(label: 'Type', value: customer.customerType.label),
              InfoRow(label: 'Phone', value: customer.phone),
              InfoRow(label: 'Email', value: customer.email),
              InfoRow(label: 'Address', value: customer.address),
            ],
          ),
        ),
        if (customer.isCorporate) ...[
          const SizedBox(height: 12),
          SectionCard(
            title: 'Corporate information',
            icon: Icons.apartment_outlined,
            child: Column(
              children: [
                InfoRow(label: 'Company', value: customer.companyName),
                InfoRow(label: 'Tax code', value: customer.taxCode),
              ],
            ),
          ),
        ],
        const SizedBox(height: 12),
        SectionCard(
          title: 'Account details',
          icon: Icons.manage_accounts_outlined,
          child: Column(
            children: [
              InfoRow(
                label: 'Status',
                value: Formatters.humanizeEnum(customer.status.wire),
              ),
              InfoRow(label: 'Assigned to', value: customer.assignedUserName),
              InfoRow(label: 'Created by', value: customer.createdByName),
              InfoRow(
                label: 'Customer since',
                value: Formatters.dateTime(customer.createdAt),
              ),
              InfoRow(
                label: 'Last updated',
                value: Formatters.dateTime(customer.updatedAt),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
