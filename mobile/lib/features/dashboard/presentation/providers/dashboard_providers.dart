import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../notification/data/notification_models.dart';
import '../../../notification/data/notification_repository.dart';
import '../../../task/data/task_models.dart';
import '../../../task/data/task_repository.dart';

/// Top open tasks for the dashboard "Upcoming" preview.
final upcomingTasksProvider = FutureProvider.autoDispose<List<Task>>((ref) async {
  final page = await ref
      .watch(taskRepositoryProvider)
      .getTasks(status: 'OPEN', page: 0, size: 5);
  return page.items;
});

/// A few most-recent notifications for the dashboard preview.
final recentNotificationsProvider =
    FutureProvider.autoDispose<List<AppNotification>>((ref) async {
  final all = await ref.watch(notificationRepositoryProvider).getNotifications();
  return all.take(4).toList();
});
