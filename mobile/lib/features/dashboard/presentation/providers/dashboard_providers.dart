import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../notification/data/notification_models.dart';
import '../../../notification/data/notification_repository.dart';
import '../../../task/data/task_models.dart';
import '../../../task/data/task_repository.dart';

/// Top open tasks for the dashboard "Upcoming" preview.
final upcomingTasksProvider = FutureProvider.autoDispose<List<Task>>((
  ref,
) async {
  final page = await ref
      .watch(taskRepositoryProvider)
      .getTasks(status: 'OPEN', page: 0, size: 5);
  return page.items;
});

/// A few most-recent notifications for the dashboard preview.
///
/// The page size is the number actually shown. It used to default to 50 and
/// then `.take(4)`, so opening the workspace pulled a dozen times the payload
/// it rendered — on every cold start and every pull-to-refresh.
const _recentNotificationCount = 4;

final recentNotificationsProvider =
    FutureProvider.autoDispose<List<AppNotification>>((ref) async {
      return ref
          .watch(notificationRepositoryProvider)
          .getNotifications(size: _recentNotificationCount);
    });
