// BR-05 / BR-06 as the UI must present them, and the edit form that answers
// them.
//
// The bug this suite pins down: a rep who created a lead in the quick form got
// a lead they could neither advance nor complete. Moving it out of NEW came
// back "A lead in active follow-up needs an interested service. Fill them in,
// then change the status." — and mobile had no way to fill anything in, because
// the only lead write it had was a status change.
import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:leadora_mobile/core/network/pagination_response.dart';
import 'package:leadora_mobile/core/theme/app_theme.dart';
import 'package:leadora_mobile/features/auth/data/dto/auth_user.dart';
import 'package:leadora_mobile/features/auth/presentation/providers/auth_controller.dart';
import 'package:leadora_mobile/features/lead/data/lead_models.dart';
import 'package:leadora_mobile/features/lead/data/lead_repository.dart';
import 'package:leadora_mobile/features/lead/data/lead_suggestions.dart';
import 'package:leadora_mobile/features/lead/presentation/screens/edit_lead_screen.dart';
import 'package:leadora_mobile/features/lead/presentation/screens/lead_detail_screen.dart';

const _leadId = 'lead-1';

const _staff = AuthUser(
  id: 'user-1',
  email: 'sam@leadora.vn',
  name: 'Sam Staff',
  roles: ['SALES'],
);

Lead _lead({
  String? source,
  String? interestedService,
  String? assignedUserId = 'user-1',
  LeadStatus status = LeadStatus.neww,
}) {
  return Lead(
    leadId: _leadId,
    fullName: 'Tran Nhat Minh',
    status: status,
    phone: '0912345678',
    source: source,
    interestedService: interestedService,
    assignedUserId: assignedUserId,
  );
}

class _RecordingLeadRepository implements LeadRepository {
  _RecordingLeadRepository(this.lead);

  Lead lead;
  UpdateLeadPayload? saved;

  @override
  Future<Lead> getLead(String leadId) async => lead;

  @override
  Future<Lead> updateLead(String leadId, UpdateLeadPayload payload) async {
    saved = payload;
    return payload.applyTo(lead);
  }

  @override
  Future<PaginationResponse<Lead>> getLeads({
    LeadFilters filters = const LeadFilters(),
    int page = 0,
    int size = 15,
    CancelToken? cancelToken,
  }) async => PaginationResponse<Lead>.empty();

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName} is not faked');
}

Future<void> _pump(
  WidgetTester tester,
  _RecordingLeadRepository repo, {
  String initialLocation = '/leads/detail/$_leadId',
}) async {
  tester.view.physicalSize = const Size(400, 2400);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  final router = GoRouter(
    initialLocation: initialLocation,
    routes: [
      GoRoute(
        path: '/leads',
        builder: (_, _) => const Scaffold(body: Text('lead list')),
        routes: [
          GoRoute(
            path: 'detail/:id',
            name: 'leadDetail',
            builder: (_, state) =>
                LeadDetailScreen(leadId: state.pathParameters['id']!),
          ),
          GoRoute(
            path: 'edit/:id',
            name: 'leadEdit',
            builder: (_, state) =>
                EditLeadScreen(leadId: state.pathParameters['id']!),
          ),
        ],
      ),
    ],
  );
  addTearDown(router.dispose);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        leadRepositoryProvider.overrideWithValue(repo),
        currentUserProvider.overrideWithValue(_staff),
        interestedServiceSuggestionsProvider.overrideWith(
          (ref) async => kInterestedServiceFallback,
        ),
      ],
      child: MaterialApp.router(
        theme: AppTheme.light(),
        routerConfig: router,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  group('LeadStatusGate mirrors the server refusals', () {
    test('a lead with no owner cannot move at all', () {
      final gate = LeadStatusGate.of(_lead(assignedUserId: null));
      expect(gate.unassigned, isTrue);
      expect(gate.allBlocked, isTrue);
      expect(gate.forwardBlocked, isTrue);
      expect(gate.reason, contains('no owner'));
    });

    test('BR-05 names exactly what is missing', () {
      expect(LeadStatusGate.of(_lead()).missing, [
        'a source',
        'an interested service',
      ]);
      expect(
        LeadStatusGate.of(_lead(source: 'Referral')).missing,
        ['an interested service'],
      );
      expect(
        LeadStatusGate.of(
          _lead(source: 'Referral', interestedService: 'Rooms'),
        ).missing,
        isEmpty,
      );
    });

    test('BR-05 blocks forward moves but never closing the lead', () {
      final gate = LeadStatusGate.of(_lead(assignedUserId: 'user-1'));
      expect(gate.forwardBlocked, isTrue);
      // A junk lead has to be closable immediately, not after filling in
      // details nobody will use.
      expect(gate.allBlocked, isFalse);
    });

    test('a complete, owned lead is not gated', () {
      final gate = LeadStatusGate.of(
        _lead(source: 'Referral', interestedService: 'Rooms'),
      );
      expect(gate.forwardBlocked, isFalse);
      expect(gate.reason, isNull);
    });
  });

  group('Lead detail presents the gate instead of a 422', () {
    testWidgets('an incomplete lead says what it needs and offers the fix',
        (tester) async {
      await _pump(tester, _RecordingLeadRepository(_lead()));

      expect(find.textContaining('an interested service'), findsWidgets);
      expect(find.text('Add the missing details'), findsOneWidget);
      // Update status stays live: Lost is still reachable, and BR-05 does not
      // block it.
      await tester.tap(find.text('Update status'));
      await tester.pumpAndSettle();
      expect(find.text('Move lead to'), findsOneWidget);
    });

    testWidgets('the forward option is locked with its reason, Lost is not',
        (tester) async {
      await _pump(tester, _RecordingLeadRepository(_lead()));

      await tester.tap(find.text('Update status'));
      await tester.pumpAndSettle();

      final contacted = tester.widget<ListTile>(
        find.widgetWithText(ListTile, 'Contacted').first,
      );
      expect(contacted.enabled, isFalse);
      expect(contacted.onTap, isNull);
      expect(find.textContaining('Needs'), findsOneWidget);

      final lost = tester.widget<ListTile>(
        find.widgetWithText(ListTile, 'Lost').first,
      );
      expect(lost.enabled, isTrue);
    });

    testWidgets('a complete lead has no hint and no locked option',
        (tester) async {
      await _pump(
        tester,
        _RecordingLeadRepository(
          _lead(source: 'Referral', interestedService: 'Rooms'),
        ),
      );

      expect(find.text('Add the missing details'), findsNothing);
      await tester.tap(find.text('Update status'));
      await tester.pumpAndSettle();
      final contacted = tester.widget<ListTile>(
        find.widgetWithText(ListTile, 'Contacted').first,
      );
      expect(contacted.enabled, isTrue);
    });

    testWidgets('an unassigned lead cannot move at all', (tester) async {
      await _pump(
        tester,
        _RecordingLeadRepository(_lead(assignedUserId: null)),
      );

      await tester.tap(find.text('Update status'));
      await tester.pumpAndSettle();
      expect(
        find.text('Move lead to'),
        findsNothing,
        reason: 'the control is dead, not merely discouraging',
      );
      expect(find.textContaining('no owner'), findsOneWidget);
      // Nothing to offer here — only a manager can unblock it.
      expect(find.text('Add the missing details'), findsNothing);
    });

    testWidgets('a converted lead offers no edit action', (tester) async {
      // BR-08: converted is a locked historical record, so the server refuses
      // every edit — the action is absent rather than offered and then denied.
      await _pump(
        tester,
        _RecordingLeadRepository(_lead(status: LeadStatus.converted)),
      );
      expect(find.byTooltip('Edit lead'), findsNothing);
    });
  });

  group('Edit lead answers what the gate asked for', () {
    testWidgets('saving sends every field, blanks included', (tester) async {
      // Null means "leave unchanged" server-side, so an edit form that omits a
      // cleared field cannot clear anything.
      final repo = _RecordingLeadRepository(
        _lead(source: 'Referral', interestedService: 'Rooms'),
      );
      await _pump(tester, repo, initialLocation: '/leads/edit/$_leadId');

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Interested service'),
        '',
      );
      await tester.tap(find.text('Save changes'));
      await tester.pumpAndSettle();

      expect(repo.saved, isNotNull);
      expect(repo.saved!.toJson(), containsPair('interestedService', ''));
      expect(repo.saved!.toJson(), containsPair('fullName', 'Tran Nhat Minh'));
    });

    testWidgets('filling the missing service satisfies BR-05', (tester) async {
      final repo = _RecordingLeadRepository(_lead(source: 'Referral'));
      await _pump(tester, repo, initialLocation: '/leads/edit/$_leadId');

      expect(find.textContaining('Still needed'), findsOneWidget);

      await tester.tap(find.widgetWithText(ChoiceChip, 'Rooms'));
      await tester.pumpAndSettle();

      // The hint clears as the field is filled, rather than after a failed save.
      expect(find.textContaining('Still needed'), findsNothing);
      expect(find.textContaining('move to Contacted'), findsOneWidget);

      await tester.tap(find.text('Save changes'));
      await tester.pumpAndSettle();
      expect(repo.saved?.toJson(), containsPair('interestedService', 'Rooms'));
    });

    testWidgets('a legacy source outside the list is not silently erased',
        (tester) async {
      // Rows predating the closed list hold values like `WEBSITE`. Opening the
      // form to fix a phone number must not rewrite the source to null.
      final repo = _RecordingLeadRepository(
        _lead(source: 'WEBSITE', interestedService: 'Rooms'),
      );
      await _pump(tester, repo, initialLocation: '/leads/edit/$_leadId');

      await tester.tap(find.text('Save changes'));
      await tester.pumpAndSettle();

      expect(repo.saved?.toJson(), containsPair('source', 'WEBSITE'));
    });

    testWidgets('clearing both phone and email is refused here, not by the server',
        (tester) async {
      final repo = _RecordingLeadRepository(
        _lead(source: 'Referral', interestedService: 'Rooms'),
      );
      await _pump(tester, repo, initialLocation: '/leads/edit/$_leadId');

      await tester.enterText(find.widgetWithText(TextFormField, 'Phone'), '');
      await tester.tap(find.text('Save changes'));
      await tester.pumpAndSettle();

      expect(repo.saved, isNull);
      expect(
        find.textContaining('phone number or an email address'),
        findsOneWidget,
      );
    });
  });
}
