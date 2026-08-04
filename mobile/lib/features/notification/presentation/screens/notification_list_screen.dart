import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_filter_chip.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/list_skeleton.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/notification_models.dart';
import '../notification_grouping.dart';
import '../providers/notification_providers.dart';

/// Dummy row for the loading skeleton — same widget as a real row so the list
/// keeps its shape when data lands.
const _skeletonNotification = AppNotification(
  id: '',
  title: 'Placeholder notification title',
  message: 'Placeholder notification message body goes here.',
  isRead: false,
);

/// Maps a notification's `relatedEntity`/`relatedId` to a screen this app can
/// show. BOOKING and HANDOVER have no mobile screen yet and fall through to
/// `null` — tapping them only marks the notification read. SLA/REMINDER route
/// to their list screens (the `relatedId` is the tracking/reminder row's own
/// id, which those list screens don't deep-link to individually yet).
String? _relatedRoute(AppNotification n) {
  final id = n.relatedId;
  if (id == null || id.isEmpty) return null;
  switch (n.relatedEntity?.toUpperCase()) {
    case 'LEAD':
      return Routes.leadDetailPath(id);
    case 'TASK':
      return Routes.taskDetailPath(id);
    case 'QUOTATION':
      return Routes.quotationDetailPath(id);
    case 'DEAL':
      return Routes.dealDetailPath(id);
    case 'SLA':
      return Routes.slaPath(highlightId: id);
    case 'REMINDER':
      return Routes.remindersPath(highlightId: id);
    default:
      return null;
  }
}

/// UC-15.2 — tapping a notification calls `GET /notifications/{id}` first
/// (`NotificationListController.open`), which enforces the ownership/access
/// check server-side (403/404) and marks it read as a side effect. Navigation
/// only happens once that call succeeds; on failure an error is shown and the
/// route is not opened. This mirrors the web `handleNotificationClick`, which
/// also awaits the access call before navigating.
Future<void> _openNotification(
  BuildContext context,
  NotificationListController controller,
  AppNotification n,
) async {
  final route = _relatedRoute(n);
  final messenger = ScaffoldMessenger.of(context);
  try {
    await controller.open(n);
  } on AppException catch (e) {
    if (!context.mounted) return;
    messenger.showSnackBar(SnackBar(content: Text(e.message)));
    return;
  } catch (_) {
    if (!context.mounted) return;
    messenger.showSnackBar(
      const SnackBar(content: Text('Could not open this notification.')),
    );
    return;
  }
  if (!context.mounted) return;
  if (route != null) context.push(route);
}

/// UC-24.24 / UC-24.25 — notification list with mark read / mark all read,
/// server-side pagination ("load more" on scroll) and a filter sheet covering
/// every backend query param (type, priority, created date range, sortBy).
class NotificationListScreen extends ConsumerStatefulWidget {
  const NotificationListScreen({super.key});

  @override
  ConsumerState<NotificationListScreen> createState() =>
      _NotificationListScreenState();
}

class _NotificationListScreenState
    extends ConsumerState<NotificationListScreen> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  NotificationListController get _controller =>
      ref.read(notificationListControllerProvider.notifier);

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      _controller.loadMore();
    }
  }

  Future<void> _openFilterSheet() async {
    final result = await showModalBottomSheet<NotificationFilters>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _NotificationFilterSheet(initial: _controller.filters),
    );
    if (result != null) _controller.applyFilters(result);
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(notificationListControllerProvider);
    final controller = _controller;
    final filters = async.valueOrNull?.filters ?? const NotificationFilters();
    final all = async.valueOrNull?.items ?? const <AppNotification>[];
    final unreadCount = all.where((n) => !n.isRead).length;
    final advancedCount = filters.activeAdvancedCount;

    // Manager/Admin only — org-wide "who did what" activity feed instead of
    // just their own (mirrors the web Team Activity toggle).
    final canViewAll = ref.watch(currentUserProvider)?.hasFullAccess ?? false;
    final viewAllUsers = ref.watch(notificationViewAllProvider);
    // "Mark all read" is always self-scoped server-side even in the Team
    // Activity feed, so its visibility/count must come from the caller's own
    // unread count, not from whatever's currently loaded (which may be the
    // whole team's).
    final ownUnreadCount =
        ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          IconButton(
            tooltip: 'Filters',
            onPressed: _openFilterSheet,
            icon: Badge.count(
              count: advancedCount,
              isLabelVisible: advancedCount > 0,
              child: const Icon(Icons.tune_rounded),
            ),
          ),
          if (ownUnreadCount > 0)
            TextButton(
              onPressed: controller.markAllRead,
              child: const Text('Mark all read'),
            ),
        ],
      ),
      body: Column(
        children: [
          AppFilterChipBar(
            children: [
              AppFilterChip(
                label: 'All',
                selected: filters.quick == NotificationReadFilter.all,
                onTap: () =>
                    controller.setQuickFilter(NotificationReadFilter.all),
              ),
              AppFilterChip(
                label: 'Unread',
                count: unreadCount,
                selected: filters.quick == NotificationReadFilter.unread,
                onTap: () =>
                    controller.setQuickFilter(NotificationReadFilter.unread),
              ),
              AppFilterChip(
                label: 'Read',
                selected: filters.quick == NotificationReadFilter.read,
                onTap: () =>
                    controller.setQuickFilter(NotificationReadFilter.read),
              ),
            ],
          ),
          if (canViewAll)
            Padding(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.lg,
                AppSpacing.xs,
                AppSpacing.lg,
                0,
              ),
              child: Row(
                children: [
                  const Icon(Icons.groups_outlined, size: 16),
                  const SizedBox(width: 6),
                  const Expanded(child: Text('Team activity')),
                  Switch(
                    value: viewAllUsers,
                    onChanged: (v) =>
                        ref.read(notificationViewAllProvider.notifier).state =
                            v,
                  ),
                ],
              ),
            ),
          const SizedBox(height: AppSpacing.xs),
          Expanded(
            child: AsyncValueView<NotificationListState>(
              value: async,
              onRetry: controller.refresh,
              loading: ListSkeleton(
                separatorHeight: 0,
                padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
                itemBuilder: (_) => const _NotificationTile(
                  notification: _skeletonNotification,
                ),
              ),
              isEmpty: (s) => s.filters.applyQuick(s.items).isEmpty,
              empty: EmptyState(
                icon: Icons.notifications_off_outlined,
                title: filters.quick == NotificationReadFilter.read
                    ? 'Nothing here'
                    : 'No notifications',
                message: filters.quick == NotificationReadFilter.read
                    ? 'No read notifications yet.'
                    : "You're all caught up.",
              ),
              data: (s) {
                final visible = s.filters.applyQuick(s.items);
                final rows = groupNotificationsByDay(visible);
                return RefreshIndicator(
                  onRefresh: controller.refresh,
                  child: ListView.builder(
                    controller: _scrollController,
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.only(bottom: AppSpacing.xxxl),
                    itemCount: rows.length + (s.hasMore ? 1 : 0),
                    itemBuilder: (context, index) {
                      if (index >= rows.length) {
                        return const Padding(
                          padding: EdgeInsets.all(AppSpacing.lg),
                          child: Center(child: CircularProgressIndicator()),
                        );
                      }
                      final row = rows[index];
                      return switch (row) {
                        NotificationGroupHeader(:final label) => _DayHeader(
                          label: label,
                        ),
                        NotificationGroupItem(:final notification) =>
                          _NotificationTile(
                            notification: notification,
                            showRecipient: viewAllUsers,
                            onTap: () => _openNotification(
                              context,
                              controller,
                              notification,
                            ),
                          ),
                      };
                    },
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

/// Advanced filter editor: type, priority, created date range and sortBy.
/// Edits a local draft of the current [initial] filters and pops with the
/// result on Apply — mirrors the Lead/Task filter sheets' shape.
class _NotificationFilterSheet extends StatefulWidget {
  const _NotificationFilterSheet({required this.initial});

  final NotificationFilters initial;

  @override
  State<_NotificationFilterSheet> createState() =>
      _NotificationFilterSheetState();
}

class _NotificationFilterSheetState extends State<_NotificationFilterSheet> {
  late NotificationFilters _draft = widget.initial;

  Future<void> _pickDateRange() async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(now.year - 3),
      lastDate: now,
      initialDateRange: _draft.createdFrom != null && _draft.createdTo != null
          ? DateTimeRange(start: _draft.createdFrom!, end: _draft.createdTo!)
          : null,
    );
    if (picked != null) {
      setState(
        () => _draft = _draft.copyWith(
          createdFrom: picked.start,
          createdTo: picked.end,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasDateWindow = _draft.createdFrom != null || _draft.createdTo != null;

    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.xl,
          0,
          AppSpacing.xl,
          AppSpacing.lg,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Filter notifications', style: theme.textTheme.titleMedium),
            const SizedBox(height: 16),
            Text('Priority', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                ChoiceChip(
                  label: const Text('Any'),
                  selected: _draft.priority == null,
                  onSelected: (_) =>
                      setState(() => _draft = _draft.copyWith(priority: null)),
                ),
                for (final p in kNotificationPriorityOptions)
                  ChoiceChip(
                    label: Text(Formatters.humanizeEnum(p)),
                    selected: _draft.priority == p,
                    onSelected: (_) =>
                        setState(() => _draft = _draft.copyWith(priority: p)),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            Text('Type', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 0,
              children: [
                ChoiceChip(
                  label: const Text('Any'),
                  selected: _draft.type == null,
                  onSelected: (_) =>
                      setState(() => _draft = _draft.copyWith(type: null)),
                ),
                for (final t in kNotificationTypeOptions)
                  ChoiceChip(
                    label: Text(Formatters.humanizeEnum(t)),
                    selected: _draft.type == t,
                    onSelected: (_) =>
                        setState(() => _draft = _draft.copyWith(type: t)),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.date_range_rounded),
              title: Text(
                hasDateWindow
                    ? '${Formatters.date(_draft.createdFrom)} → ${Formatters.date(_draft.createdTo)}'
                    : 'Created date — any time',
                style: theme.textTheme.bodyMedium,
              ),
              onTap: _pickDateRange,
              trailing: hasDateWindow
                  ? IconButton(
                      tooltip: 'Clear dates',
                      icon: const Icon(Icons.close_rounded, size: 20),
                      onPressed: () => setState(
                        () => _draft = _draft.copyWith(
                          createdFrom: null,
                          createdTo: null,
                        ),
                      ),
                    )
                  : const Icon(Icons.chevron_right_rounded),
            ),
            const SizedBox(height: 4),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Sort by priority'),
              subtitle: const Text('Urgent first, instead of newest first'),
              value: _draft.sortByPriority,
              onChanged: (v) =>
                  setState(() => _draft = _draft.copyWith(sortByPriority: v)),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () =>
                        Navigator.of(context).pop(_draft.resetAdvanced()),
                    child: const Text('Reset'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: FilledButton(
                    onPressed: () => Navigator.of(context).pop(_draft),
                    child: const Text('Apply filters'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  /// [onTap] is null only for the loading skeleton, which must not be tappable.
  const _NotificationTile({
    required this.notification,
    this.onTap,
    this.showRecipient = false,
  });

  final AppNotification notification;
  final VoidCallback? onTap;

  /// Team Activity feed only — shows who the notification was sent to, since
  /// the list is no longer implicitly "mine".
  final bool showRecipient;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final unread = !notification.isRead;
    return ListTile(
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: unread
            ? theme.colorScheme.primaryContainer
            : theme.colorScheme.surfaceContainerHighest,
        child: Icon(
          notification.icon,
          color: unread
              ? theme.colorScheme.onPrimaryContainer
              : theme.colorScheme.outline,
          size: 20,
        ),
      ),
      title: Text(
        notification.title,
        style: theme.textTheme.bodyLarge?.copyWith(
          fontWeight: unread ? FontWeight.w700 : FontWeight.w500,
        ),
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (notification.message.isNotEmpty)
            Text(
              notification.message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          if (showRecipient && notification.recipientName != null) ...[
            const SizedBox(height: 2),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.person_outline_rounded,
                  size: 12,
                  color: theme.colorScheme.outline,
                ),
                const SizedBox(width: 3),
                Flexible(
                  child: Text(
                    notification.recipientName!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.outline,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ],
          const SizedBox(height: 2),
          Text(
            Formatters.relative(notification.createdAt),
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.outline,
            ),
          ),
        ],
      ),
      trailing: unread
          ? Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(
                color: theme.colorScheme.primary,
                shape: BoxShape.circle,
              ),
            )
          : null,
      isThreeLine: notification.message.isNotEmpty,
    );
  }
}

/// Sticky-looking day label between notification groups.
class _DayHeader extends StatelessWidget {
  const _DayHeader({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.lg,
        AppSpacing.lg,
        AppSpacing.sm,
      ),
      child: Text(
        label,
        style: theme.textTheme.labelLarge?.copyWith(
          fontWeight: FontWeight.w800,
          color: theme.colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }
}
