import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/reminder_models.dart';
import '../../data/reminder_repository.dart';

/// Loads and mutates the signed-in user's reminders.
class ReminderListController extends AutoDisposeAsyncNotifier<List<Reminder>> {
  ReminderRepository get _repo => ref.read(reminderRepositoryProvider);

  Future<List<Reminder>> _fetch() {
    final userId = ref.read(currentUserProvider)?.id;
    return _repo.getReminders(userId: userId);
  }

  @override
  Future<List<Reminder>> build() => _fetch();

  Future<void> refresh() async {
    state = const AsyncLoading<List<Reminder>>().copyWithPrevious(state);
    state = await AsyncValue.guard(_fetch);
  }

  /// Optimistically mark [reminder] done, then reconcile with the server.
  Future<void> dismiss(Reminder reminder) async {
    final current = state.valueOrNull;
    if (current == null) return;
    state = AsyncData([
      for (final item in current)
        if (item.reminderId != reminder.reminderId) item,
    ]);
    try {
      await _repo.dismiss(reminder.reminderId);
    } catch (_) {
      state = AsyncData(current); // revert on failure
    }
  }
}

final reminderListControllerProvider =
    AutoDisposeAsyncNotifierProvider<ReminderListController, List<Reminder>>(
      ReminderListController.new,
    );
