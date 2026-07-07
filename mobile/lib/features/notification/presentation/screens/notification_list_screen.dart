import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../data/notification_models.dart';
import '../providers/notification_providers.dart';

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

/// Tapping a notification opens its related record (if this app has a screen
/// for it) and marks it read — mirrors the web `handleNotificationClick`.
/// Deliberately one-directional (never re-marks an already-read notification
/// unread on tap; `toggleRead` only flips when the notification is unread).
Future<void> _openNotification(
  BuildContext context,
  NotificationListController controller,
  AppNotification n,
) async {
  if (!n.isRead) {
    await controller.toggleRead(n);
  }
  final route = _relatedRoute(n);
  if (route != null && context.mounted) {
    context.push(route);
  }
}

/// Which subset of notifications the filter row shows — mirrors the web's
/// "Unread only" toggle but as an explicit All / Unread / Read split.
enum _ReadFilter { all, unread, read }

List<AppNotification> _applyFilter(List<AppNotification> list, _ReadFilter filter) => switch (filter) {
      _ReadFilter.all => list,
      _ReadFilter.unread => list.where((n) => !n.isRead).toList(),
      _ReadFilter.read => list.where((n) => n.isRead).toList(),
    };

/// UC-24.24 / UC-24.25 — notification list with mark read / mark all read.
class NotificationListScreen extends ConsumerStatefulWidget {
  const NotificationListScreen({super.key});

  @override
  ConsumerState<NotificationListScreen> createState() => _NotificationListScreenState();
}

class _NotificationListScreenState extends ConsumerState<NotificationListScreen> {
  _ReadFilter _filter = _ReadFilter.all;

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(notificationListControllerProvider);
    final controller = ref.read(notificationListControllerProvider.notifier);
    final all = async.valueOrNull ?? const <AppNotification>[];
    final unreadCount = all.where((n) => !n.isRead).length;
    final visible = _applyFilter(all, _filter);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          if (unreadCount > 0)
            TextButton(
              onPressed: controller.markAllRead,
              child: const Text('Mark all read'),
            ),
        ],
      ),
      body: Column(
        children: [
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: ChoiceChip(
                    label: const Text('All'),
                    selected: _filter == _ReadFilter.all,
                    onSelected: (_) => setState(() => _filter = _ReadFilter.all),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: ChoiceChip(
                    label: Text('Unread${unreadCount > 0 ? ' ($unreadCount)' : ''}'),
                    selected: _filter == _ReadFilter.unread,
                    onSelected: (_) => setState(() => _filter = _ReadFilter.unread),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: ChoiceChip(
                    label: const Text('Read'),
                    selected: _filter == _ReadFilter.read,
                    onSelected: (_) => setState(() => _filter = _ReadFilter.read),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: AsyncValueView<List<AppNotification>>(
              value: async,
              onRetry: controller.refresh,
              isEmpty: (_) => visible.isEmpty,
              empty: EmptyState(
                icon: Icons.notifications_off_outlined,
                title: _filter == _ReadFilter.read ? 'Nothing here' : 'No notifications',
                message: _filter == _ReadFilter.read
                    ? 'No read notifications yet.'
                    : "You're all caught up.",
              ),
              data: (_) => RefreshIndicator(
                onRefresh: controller.refresh,
                child: ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  itemCount: visible.length,
                  separatorBuilder: (_, _) => const Divider(height: 1, indent: 72),
                  itemBuilder: (context, index) => _NotificationTile(
                    notification: visible[index],
                    onTap: () => _openNotification(context, controller, visible[index]),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({required this.notification, required this.onTap});

  final AppNotification notification;
  final VoidCallback onTap;

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
          color: unread ? theme.colorScheme.onPrimaryContainer : theme.colorScheme.outline,
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
            Text(notification.message, maxLines: 2, overflow: TextOverflow.ellipsis),
          const SizedBox(height: 2),
          Text(
            Formatters.relative(notification.createdAt),
            style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.outline),
          ),
        ],
      ),
      trailing: unread
          ? Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(color: theme.colorScheme.primary, shape: BoxShape.circle),
            )
          : null,
      isThreeLine: notification.message.isNotEmpty,
    );
  }
}
