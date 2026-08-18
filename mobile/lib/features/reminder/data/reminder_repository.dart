import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'reminder_models.dart';

/// Reminder API calls — UC-16.1 (create), UC-16.2 (view "my reminders" or,
/// for Manager/Admin, everyone's), UC-16.3 (update), plus search/filter and
/// dismiss.
class ReminderRepository {
  ReminderRepository(this._client);

  final ApiClient _client;

  /// [userId] scopes the list to "my reminders" (excludes CANCELLED by
  /// default, matching `GetRemindersUseCase`); omit for the "all staff" view
  /// (Manager/Admin only — the server re-scopes Sales Staff regardless).
  /// [remindFrom]/[remindTo] filter the due-date range; [sortBy] `"priority"`
  /// sorts HIGH→LOW (otherwise `remindAt` ascending); [search] matches
  /// title/description.
  Future<List<Reminder>> getReminders({
    String? userId,
    String? status,
    DateTime? remindFrom,
    DateTime? remindTo,
    String? sortBy,
    String? search,
  }) {
    return _client.get<List<Reminder>>(
      ApiPaths.reminders,
      query: {
        'userId': ?userId,
        'status': ?status,
        'remindFrom': ?remindFrom?.toUtc().toIso8601String(),
        'remindTo': ?remindTo?.toUtc().toIso8601String(),
        'sortBy': ?sortBy,
        'search': ?search,
      },
      decode: (data) => (data as List)
          .map((e) => Reminder.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  /// UC-16.1 — create a manual reminder linked to a lead/deal/quotation/
  /// booking/deposit.
  Future<Reminder> createReminder(CreateReminderPayload payload) {
    return _client.post<Reminder>(
      ApiPaths.reminders,
      data: payload.toJson(),
      decode: (data) => Reminder.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-16.3 — update title/description/due date/priority, mark done, or
  /// force-edit an already-completed reminder.
  Future<Reminder> updateReminder(String reminderId, UpdateReminderPayload payload) {
    return _client.put<Reminder>(
      ApiPaths.reminderById(reminderId),
      data: payload.toJson(),
      decode: (data) => Reminder.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-16.4 — escalate an overdue reminder to the manager.
  ///
  /// The server refuses anything but an OVERDUE reminder (`NOT_OVERDUE`, 409),
  /// refuses an already-completed one (`REMINDER_ALREADY_DONE`, 409), and only
  /// accepts the call from the **assignee or a MANAGER** (`UNAUTHORIZED_ESCALATE`,
  /// 403). `ReminderPermissions.canEscalate` mirrors those rules so the button is
  /// hidden rather than offered and rejected — but the server is still the
  /// authority, so callers must surface the error either way.
  Future<void> escalate(String reminderId) {
    return _client.post<void>(
      ApiPaths.reminderEscalate(reminderId),
      decode: (_) {},
    );
  }

  /// UC-16.1 — mark a reminder done (quick action; see [updateReminder] for
  /// the full edit form).
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
