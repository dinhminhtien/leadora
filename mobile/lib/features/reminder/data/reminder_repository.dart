import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'reminder_models.dart';

/// Reminder API calls. Create/edit/escalate stay web-only for now — mobile
/// only needs to view "my reminders" and dismiss them (UC-16.1/16.2).
class ReminderRepository {
  ReminderRepository(this._client);

  final ApiClient _client;

  /// [userId] scopes the list to "my reminders" (excludes CANCELLED by
  /// default, matching `GetRemindersUseCase`); omit for none.
  Future<List<Reminder>> getReminders({String? userId, String? status}) {
    return _client.get<List<Reminder>>(
      ApiPaths.reminders,
      query: {
        'userId': ?userId,
        'status': ?status,
      },
      decode: (data) =>
          (data as List).map((e) => Reminder.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }

  /// UC-16.1 — mark a reminder done.
  Future<void> dismiss(String reminderId) {
    return _client.patch<void>(
      ApiPaths.reminderDismiss(reminderId),
      decode: (_) {},
    );
  }
}

final reminderRepositoryProvider = Provider<ReminderRepository>((ref) {
  return ReminderRepository(ref.watch(apiClientProvider));
});
