import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/features/notification/data/notification_models.dart';
import 'package:leadora_mobile/features/notification/presentation/notification_grouping.dart';

AppNotification _n(String id, {DateTime? createdAt}) => AppNotification(
  id: id,
  title: 'Title $id',
  message: 'Message $id',
  isRead: false,
  createdAt: createdAt,
);

List<String> _labels(List<NotificationRow> rows) => [
  for (final r in rows)
    if (r is NotificationGroupHeader) r.label,
];

List<String> _ids(List<NotificationRow> rows) => [
  for (final r in rows)
    if (r is NotificationGroupItem) r.notification.id,
];

void main() {
  // A fixed "now" so the boundaries are deterministic.
  final now = DateTime(2026, 7, 10, 9);

  group('groupNotificationsByDay', () {
    test('empty input produces no rows', () {
      expect(groupNotificationsByDay(const [], now: now), isEmpty);
    });

    test('splits into Today / Yesterday / Earlier and keeps input order', () {
      final rows = groupNotificationsByDay([
        _n('today-1', createdAt: DateTime(2026, 7, 10, 8)),
        _n('today-2', createdAt: DateTime(2026, 7, 10, 1)),
        _n('yday', createdAt: DateTime(2026, 7, 9, 23)),
        _n('old', createdAt: DateTime(2026, 7, 1)),
      ], now: now);

      expect(_labels(rows), ['Today', 'Yesterday', 'Earlier']);
      expect(_ids(rows), ['today-1', 'today-2', 'yday', 'old']);
    });

    test('a section with no items produces no header', () {
      final rows = groupNotificationsByDay([
        _n('old', createdAt: DateTime(2026, 1, 1)),
      ], now: now);

      expect(_labels(rows), ['Earlier']);
    });

    test('a notification with no timestamp lands in Earlier', () {
      final rows = groupNotificationsByDay([_n('unknown')], now: now);
      expect(_labels(rows), ['Earlier']);
      expect(_ids(rows), ['unknown']);
    });

    test('a future timestamp (clock skew) stays in Today, not Earlier', () {
      final rows = groupNotificationsByDay([
        _n('future', createdAt: DateTime(2026, 7, 11, 3)),
      ], now: now);
      expect(_labels(rows), ['Today']);
    });

    test('a UTC instant is bucketed by its LOCAL calendar day', () {
      // The regression this guards: comparing the UTC day would drop a
      // late-evening local notification into the wrong bucket whenever the
      // local zone is ahead of UTC.
      final localNow = DateTime(2026, 7, 10, 21);
      final localEvening = DateTime(2026, 7, 10, 20, 30);

      final rows = groupNotificationsByDay([
        _n('tonight', createdAt: localEvening.toUtc()),
      ], now: localNow);

      expect(_labels(rows), ['Today']);
    });

    test('midnight boundary: 00:00 today is Today, 23:59 yesterday is not', () {
      final rows = groupNotificationsByDay([
        _n('midnight', createdAt: DateTime(2026, 7, 10)),
        _n('just-before', createdAt: DateTime(2026, 7, 9, 23, 59, 59)),
      ], now: now);

      expect(_labels(rows), ['Today', 'Yesterday']);
      expect(_ids(rows), ['midnight', 'just-before']);
    });
  });
}
