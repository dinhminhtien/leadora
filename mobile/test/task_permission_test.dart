// Role-based gating for the Follow-up Task module.
//
// The wider `responsive_smoke_test` only ever signs in as SALES, so it exercises
// the staff view of these screens. This suite pins down the thing that actually
// differs between the two roles: a manager routes work to other people, a staff
// member does not — and must not be shown controls that would only come back 403.
//
// It also covers two layout rules the screens promise: the detail page renders no
// empty placeholder cards, and the activity type strip stays on one scrollable row
// rather than wrapping.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:leadora_mobile/core/theme/app_theme.dart';
import 'package:leadora_mobile/features/auth/data/dto/auth_user.dart';
import 'package:leadora_mobile/features/auth/presentation/providers/auth_controller.dart';
import 'package:leadora_mobile/features/task/data/task_models.dart';
import 'package:leadora_mobile/features/task/presentation/providers/task_permissions.dart';
import 'package:leadora_mobile/features/task/presentation/providers/task_providers.dart';
import 'package:leadora_mobile/features/task/presentation/screens/task_detail_screen.dart';
import 'package:leadora_mobile/features/task/presentation/screens/task_form_screen.dart';
import 'package:leadora_mobile/features/task/presentation/widgets/activity_type_selector.dart';
import 'package:leadora_mobile/features/task/presentation/widgets/task_detail_cards.dart';

const _staffId = 'user-staff';

AuthUser _staff() => const AuthUser(
  id: _staffId,
  email: 'sam@leadora.vn',
  name: 'Sam Staff',
  roles: ['SALES'],
);

AuthUser _manager() => const AuthUser(
  id: 'user-manager',
  email: 'maria@leadora.vn',
  name: 'Maria Manager',
  roles: ['MANAGER'],
);

/// A task assigned to the staff user, carrying only the fields named. Everything
/// else stays null so the "hide empty sections" rule is exercised by default.
///
/// The title is a plain title and the activity type is its own field — the two are
/// independent now. (Type-from-title is covered in `task_test.dart`.)
Task _task({
  String? description,
  String? resultNote,
  DateTime? startAt,
  String? leadId,
  String? primaryContactName,
}) {
  return Task(
    taskId: 't1',
    title: 'Confirm the headcount',
    status: TaskStatus.open,
    priority: TaskPriority.medium,
    activityType: TaskActivityType.call,
    assignedUserId: _staffId,
    assignedUserName: 'Sam Staff',
    createdByName: 'Maria Manager',
    description: description,
    resultNote: resultNote,
    startAt: startAt,
    leadId: leadId,
    leadName: leadId == null ? null : 'Grand Hotel',
    primaryContactName: primaryContactName,
  );
}

/// Pumps [screen] as the given user.
///
/// [tall] gives the form tests a viewport deep enough to build every field: the
/// form is a lazy ListView, so on the default 600dp-high surface the fields near
/// the bottom (the assignee among them) are never constructed and can't be found.
Future<void> _pump(
  WidgetTester tester,
  Widget screen, {
  required AuthUser as,
  bool tall = false,
}) {
  if (tall) {
    tester.view.physicalSize = const Size(400, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
  }
  return tester.pumpWidget(
    ProviderScope(
      overrides: [
        currentUserProvider.overrideWithValue(as),
        taskDetailProvider('t1').overrideWith((ref) => _task()),
      ],
      child: MaterialApp(theme: AppTheme.light(), home: screen),
    ),
  );
}

void main() {
  group('TaskPermissions mirrors the server policy', () {
    test('MANAGER and ADMIN get full access', () {
      expect(TaskPermissions.of(_manager()).isManager, isTrue);
      expect(TaskPermissions.of(_manager()).canReassign, isTrue);
      expect(TaskPermissions.of(_manager()).canAssignToOthers, isTrue);

      const admin = AuthUser(id: 'a', email: 'a@x', name: 'A', roles: ['ADMIN']);
      expect(TaskPermissions.of(admin).isManager, isTrue);
    });

    test('SALES and SALES_STAFF are scoped', () {
      for (final role in ['SALES', 'SALES_STAFF']) {
        final scoped = AuthUser(id: 's', email: 's@x', name: 'S', roles: [role]);
        final p = TaskPermissions.of(scoped);
        expect(p.isManager, isFalse, reason: role);
        expect(p.canReassign, isFalse, reason: role);
        expect(p.canAssignToOthers, isFalse, reason: role);
        expect(p.canChangeRelatedRecord, isFalse, reason: role);
        expect(p.canFilterByAssignee, isFalse, reason: role);
      }
    });

    test('a signed-out user may do nothing', () {
      expect(TaskPermissions.of(null).isManager, isFalse);
      expect(TaskPermissions.of(null).canReassign, isFalse);
    });

    test('isAssignee identifies the signed-in user', () {
      expect(TaskPermissions.of(_staff()).isAssignee(_task()), isTrue);
      expect(TaskPermissions.of(_manager()).isAssignee(_task()), isFalse);
    });
  });

  group('Task detail — role-gated actions', () {
    testWidgets('a manager is offered Reassign', (tester) async {
      await _pump(tester, const TaskDetailScreen(taskId: 't1'), as: _manager());
      await tester.pump();

      await tester.tap(find.byIcon(Icons.more_vert_rounded));
      await tester.pumpAndSettle();

      expect(find.text('Reassign (hand over)'), findsOneWidget);
      expect(find.text('Edit task'), findsOneWidget);
    });

    testWidgets('a staff member is not — only Edit (BR-18)', (tester) async {
      await _pump(tester, const TaskDetailScreen(taskId: 't1'), as: _staff());
      await tester.pump();

      await tester.tap(find.byIcon(Icons.more_vert_rounded));
      await tester.pumpAndSettle();

      expect(find.text('Reassign (hand over)'), findsNothing);
      expect(find.text('Edit task'), findsOneWidget);
    });

    testWidgets('the assignee is badged "You" for the person who owns it', (
      tester,
    ) async {
      await _pump(tester, const TaskDetailScreen(taskId: 't1'), as: _staff());
      await tester.pump();

      expect(find.text('You'), findsOneWidget);
    });
  });

  group('Task detail — sections with no data are hidden, not empty', () {
    testWidgets('a bare task shows Overview + Assignment and nothing else', (
      tester,
    ) async {
      await _pump(tester, const TaskDetailScreen(taskId: 't1'), as: _staff());
      await tester.pump();

      // Always present — every task has a status and an owner.
      expect(find.text('Overview'), findsOneWidget);
      expect(find.text('Assignment'), findsOneWidget);

      // Nothing to say → no card at all.
      expect(find.text('Schedule'), findsNothing);
      expect(find.text('Contact information'), findsNothing);
      expect(find.text('Description'), findsNothing);
      expect(find.text('RELATED TO'), findsNothing);
    });

    test('shouldShow guards gate on real data only', () {
      expect(TaskScheduleCard.shouldShow(_task()), isFalse);
      expect(TaskScheduleCard.shouldShow(_task(startAt: DateTime.now())), isTrue);

      expect(TaskDescriptionCard.shouldShow(_task()), isFalse);
      expect(TaskDescriptionCard.shouldShow(_task(description: '   ')), isFalse);
      expect(TaskDescriptionCard.shouldShow(_task(description: 'Ring them')), isTrue);

      expect(TaskRelatedRecordCard.shouldShow(_task()), isFalse);
      expect(TaskRelatedRecordCard.shouldShow(_task(leadId: 'l1')), isTrue);

      expect(TaskContactCard.shouldShow(_task()), isFalse);
      expect(TaskContactCard.shouldShow(_task(primaryContactName: 'Ann')), isTrue);

      expect(TaskResultNoteCard.shouldShow(_task()), isFalse);
      expect(TaskResultNoteCard.shouldShow(_task(resultNote: 'Booked')), isTrue);
    });
  });

  group('Task form — role-gated assignee', () {
    testWidgets('a manager picks the assignee', (tester) async {
      await _pump(
        tester,
        const TaskFormScreen(mode: TaskFormMode.create),
        as: _manager(),
        tall: true,
      );
      await tester.pump();

      expect(find.text('Assignee *'), findsOneWidget);
      // No lock: the field is interactive.
      expect(find.byIcon(Icons.lock_outline_rounded), findsNothing);
    });

    testWidgets('a staff member gets a locked "You" instead', (tester) async {
      await _pump(
        tester,
        const TaskFormScreen(mode: TaskFormMode.create),
        as: _staff(),
        tall: true,
      );
      await tester.pump();

      expect(find.text('Assigned to'), findsOneWidget);
      expect(find.text('Assignee *'), findsNothing);
      expect(
        find.text('Only a manager can assign this to someone else.'),
        findsOneWidget,
      );
      expect(find.byIcon(Icons.lock_outline_rounded), findsWidgets);
    });

    testWidgets('resign is refused outright for staff, even by direct route', (
      tester,
    ) async {
      await _pump(
        tester,
        const TaskFormLoader(mode: TaskFormMode.resign, taskId: 't1'),
        as: _staff(),
      );
      await tester.pump();

      expect(
        find.text('Only a manager can reassign a follow-up task.'),
        findsOneWidget,
      );
    });
  });

  group('Activity type selector', () {
    testWidgets('offers every type on one scrollable row — it never wraps', (
      tester,
    ) async {
      // A 320dp phone cannot fit six chips; the strip must scroll, not wrap.
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          home: Scaffold(
            body: ActivityTypeSelector(
              selected: TaskActivityType.call,
              onSelected: (_) {},
            ),
          ),
        ),
      );

      // One horizontally-scrolling list, so the field is always a single line.
      final list = tester.widget<ListView>(find.byType(ListView));
      expect(list.scrollDirection, Axis.horizontal);

      // Every type is reachable, including ones scrolled off-screen.
      for (final type in TaskActivityType.values) {
        await tester.scrollUntilVisible(find.text(type.label), 60);
        expect(find.text(type.label), findsOneWidget);
      }
    });
  });
}
