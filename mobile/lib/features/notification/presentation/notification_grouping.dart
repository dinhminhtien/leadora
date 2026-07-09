import '../data/notification_models.dart';

/// One rendered row in the grouped notification list: either a day header or a
/// notification.
sealed class NotificationRow {
  const NotificationRow();
}

class NotificationGroupHeader extends NotificationRow {
  const NotificationGroupHeader(this.label);
  final String label;
}

class NotificationGroupItem extends NotificationRow {
  const NotificationGroupItem(this.notification);
  final AppNotification notification;
}

/// Splits [notifications] into Today / Yesterday / Earlier, preserving the
/// backend's ordering inside each group.
///
/// `createdAt` arrives as a UTC instant, so it is converted to local time before
/// the calendar day is taken — comparing the UTC day would push an 8pm UTC+7
/// notification into "Yesterday". A notification with no timestamp cannot be
/// placed on a day and falls into Earlier.
///
/// [now] is injectable so the boundary behaviour is testable without waiting
/// for midnight.
List<NotificationRow> groupNotificationsByDay(
  List<AppNotification> notifications, {
  DateTime? now,
}) {
  if (notifications.isEmpty) return const [];

  final today = _startOfLocalDay(now ?? DateTime.now());
  final yesterday = today.subtract(const Duration(days: 1));

  final todayItems = <AppNotification>[];
  final yesterdayItems = <AppNotification>[];
  final earlierItems = <AppNotification>[];

  for (final n in notifications) {
    final createdAt = n.createdAt;
    if (createdAt == null) {
      earlierItems.add(n);
      continue;
    }
    final day = _startOfLocalDay(createdAt.toLocal());
    if (!day.isBefore(today)) {
      // Includes a clock-skewed future timestamp, which belongs at the top
      // rather than in "Earlier".
      todayItems.add(n);
    } else if (day == yesterday) {
      yesterdayItems.add(n);
    } else {
      earlierItems.add(n);
    }
  }

  return [
    ..._section('Today', todayItems),
    ..._section('Yesterday', yesterdayItems),
    ..._section('Earlier', earlierItems),
  ];
}

Iterable<NotificationRow> _section(String label, List<AppNotification> items) {
  if (items.isEmpty) return const [];
  return [
    NotificationGroupHeader(label),
    ...items.map(NotificationGroupItem.new),
  ];
}

DateTime _startOfLocalDay(DateTime value) {
  final local = value.isUtc ? value.toLocal() : value;
  return DateTime(local.year, local.month, local.day);
}
