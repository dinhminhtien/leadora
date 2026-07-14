import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../auth/data/dto/auth_user.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/task_models.dart';

/// What the signed-in user may do inside the Follow-up Task module.
///
/// This is a Dart mirror of the server's `TaskAccessPolicy` / `BaseAccessPolicy`
/// — MANAGER and ADMIN act on every task, SALES / SALES_STAFF only on the tasks
/// assigned to them. Gating the UI on this doesn't *grant* anything: the backend
/// re-checks every call. It only keeps us from offering a control that would
/// come back 403.
///
/// Each getter names the rule it shadows so the two stay honest about each other.
class TaskPermissions {
  const TaskPermissions({required this.userId, required this.isManager});

  /// Everything a signed-out user may do: nothing.
  static const TaskPermissions none = TaskPermissions(
    userId: null,
    isManager: false,
  );

  final String? userId;

  /// MANAGER / ADMIN. Named for the business role rather than the role string,
  /// since that is how the rules below read.
  final bool isManager;

  factory TaskPermissions.of(AuthUser? user) {
    if (user == null) return none;
    return TaskPermissions(userId: user.id, isManager: user.hasFullAccess);
  }

  /// Staff may only raise tasks for themselves; handing work to someone else is
  /// a manager action (`CreateTaskUseCase` enforces it).
  bool get canAssignToOthers => isManager;

  /// BR-18 — moving a task to a different assignee, whether by editing it or by
  /// resigning it, is manager-only (`assertFullAccess` in Update/Resign).
  bool get canReassign => isManager;

  /// Re-pointing a task at a different lead / customer / deal rewrites its
  /// business context, so `UpdateTaskUseCase` restricts it to managers. Staff
  /// still *see* the link — they just can't move it.
  bool get canChangeRelatedRecord => isManager;

  /// Staff are hard-scoped to their own tasks server-side, so an assignee filter
  /// would be a no-op control for them.
  bool get canFilterByAssignee => isManager;

  /// Whether [task] is this user's to work on. Staff only ever see their own, so
  /// this is really only interesting for a manager looking at someone else's.
  bool isAssignee(Task task) =>
      userId != null && task.assignedUserId == userId;
}

/// The current user's task permissions. Rebuilds when the session changes.
final taskPermissionsProvider = Provider<TaskPermissions>((ref) {
  return TaskPermissions.of(ref.watch(currentUserProvider));
});
