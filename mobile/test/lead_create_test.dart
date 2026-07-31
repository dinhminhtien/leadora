// UC-24.16 Create Quick Lead — what the form insists on, and what it merely
// mentions.
//
// The point of the rework is that a name plus one way to reach the person is the
// whole requirement, because that is all the server enforces. Everything else
// folds away. These tests pin both halves down: that the short path really does
// submit, and that the fields it hides are still validated and still warned
// about rather than silently dropped.
import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:leadora_mobile/core/network/pagination_response.dart';
import 'package:leadora_mobile/core/theme/app_theme.dart';
import 'package:leadora_mobile/features/lead/data/lead_models.dart';
import 'package:leadora_mobile/features/lead/data/lead_repository.dart';
import 'package:leadora_mobile/features/lead/data/lead_suggestions.dart';
import 'package:leadora_mobile/features/lead/presentation/screens/create_lead_screen.dart';

class _RecordingLeadRepository implements LeadRepository {
  CreateLeadPayload? created;

  @override
  Future<Lead> createLead(CreateLeadPayload payload) async {
    created = payload;
    return Lead(
      leadId: 'new-1',
      fullName: payload.fullName,
      status: LeadStatus.neww,
    );
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

Future<void> _pumpForm(
  WidgetTester tester,
  _RecordingLeadRepository repo,
) async {
  tester.view.physicalSize = const Size(400, 2400);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  // A real router, because the screen pops itself on success. Started one level
  // deep so there is something to pop back to, exactly as the app reaches it.
  final router = GoRouter(
    initialLocation: '/leads/new',
    routes: [
      GoRoute(
        path: '/leads',
        builder: (_, _) => const Scaffold(body: Text('lead list')),
        routes: [
          GoRoute(path: 'new', builder: (_, _) => const CreateLeadScreen()),
        ],
      ),
    ],
  );
  addTearDown(router.dispose);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        leadRepositoryProvider.overrideWithValue(repo),
        // The catalogue is a network call; the form must not depend on it having
        // answered, so the tests run on the offline fallback.
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

Future<void> _submit(WidgetTester tester) async {
  await tester.tap(find.text('Create lead'));
  await tester.pumpAndSettle();
}

Finder _field(String label) => find.widgetWithText(TextFormField, label);

/// Only rendered once the lead is an organization.
const _companyLabel = 'Company / Organization *';

void main() {
  testWidgets('a name and a phone is enough to save', (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.enterText(_field('Full name *'), 'Tran Nhat Minh');
    await tester.enterText(_field('Phone'), '0912345678');
    await _submit(tester);

    expect(repo.created, isNotNull);
    expect(repo.created!.toJson(), containsPair('fullName', 'Tran Nhat Minh'));
    expect(repo.created!.toJson(), containsPair('phone', '0912345678'));
    // Nothing was invented for the fields the user never opened. A default here
    // is what made 'Website Inquiry' unreadable on the web.
    expect(repo.created!.toJson().containsKey('source'), isFalse);
    expect(
      repo.created!.toJson().containsKey('interestedService'),
      isFalse,
    );
  });

  testWidgets('a name and an email is equally enough', (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.enterText(_field('Full name *'), 'Le Thi B');
    await tester.enterText(_field('Email'), 'b@example.com');
    await _submit(tester);

    expect(repo.created, isNotNull);
    expect(repo.created!.toJson(), containsPair('email', 'b@example.com'));
  });

  testWidgets('a name on its own is refused, and says why', (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.enterText(_field('Full name *'), 'Nguyen Van A');
    await _submit(tester);

    expect(repo.created, isNull, reason: 'nothing should reach the server');
    expect(
      find.textContaining('phone number or an email address'),
      findsOneWidget,
    );
  });

  testWidgets('the phone is normalised to what the column accepts',
      (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.enterText(_field('Full name *'), 'Pham C');
    await tester.enterText(_field('Phone'), '+84 912 345 678');
    await _submit(tester);

    expect(repo.created?.toJson(), containsPair('phone', '0912345678'));
  });

  testWidgets('BR-05 is stated up front, not after the save', (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    // Visible before anything is typed: the lead will save, and then stick.
    expect(find.textContaining('before moving this lead past New'),
        findsOneWidget);
  });

  testWidgets('source is picked from the list, never typed', (tester) async {
    // A closed list is the whole point: typing it is what produced
    // 'Website Inquiry', 'WEBSITE' and 'Website' as three separate sources.
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.tap(find.text('More details'));
    await tester.pumpAndSettle();

    await tester.tap(find.byType(DropdownButtonFormField<String?>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Referral').last);
    await tester.pumpAndSettle();

    await tester.enterText(_field('Full name *'), 'Do D');
    await tester.enterText(_field('Phone'), '0912345678');
    await _submit(tester);

    expect(repo.created?.toJson(), containsPair('source', 'Referral'));
  });

  testWidgets('"Not specified" is an option, not an implied default',
      (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.tap(find.text('More details'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(DropdownButtonFormField<String?>));
    await tester.pumpAndSettle();

    // Every channel the web offers, plus an explicit way to say nothing.
    for (final option in kLeadSourceOptions) {
      expect(find.text(option), findsWidgets, reason: option);
    }
    expect(find.text('Not specified'), findsWidgets);
  });

  testWidgets('the company field belongs to organizations only',
      (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.tap(find.text('More details'));
    await tester.pumpAndSettle();
    // An individual has no company, so asking for one is a question with no
    // answer on every individual lead.
    expect(_field(_companyLabel), findsNothing);

    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    expect(_field(_companyLabel), findsOneWidget);
  });

  testWidgets('switching back to individual drops the company typed under it',
      (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.enterText(_field('Full name *'), 'Someone');
    await tester.enterText(_field('Phone'), '0912345678');
    await tester.tap(find.text('More details'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    await tester.enterText(_field(_companyLabel), 'Novax');
    await tester.tap(find.byType(SwitchListTile)); // back to individual
    await tester.pumpAndSettle();

    await _submit(tester);

    // Otherwise a company the form no longer shows rides along on an
    // individual lead.
    expect(repo.created?.toJson().containsKey('companyName'), isFalse);
    expect(repo.created?.toJson(), containsPair('isCorporate', false));
  });

  testWidgets('an organization still has to name its company', (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.enterText(_field('Full name *'), 'ACME rep');
    await tester.enterText(_field('Phone'), '0912345678');

    await tester.tap(find.text('More details'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();

    await _submit(tester);

    expect(repo.created, isNull);
    expect(
      find.textContaining('Company name is required'),
      findsOneWidget,
    );
  });

  testWidgets('a refusal from a folded-away field is not a dead end',
      (tester) async {
    // The fields stay mounted while folded, so they validate — but their error
    // text folds away with them. Refusing to save while showing nothing makes
    // the button look broken, so the section opens and the reason is spoken.
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.enterText(_field('Full name *'), 'ACME rep');
    await tester.enterText(_field('Phone'), '0912345678');
    await tester.tap(find.text('More details'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(SwitchListTile)); // corporate, company blank
    await tester.pumpAndSettle();
    await tester.tap(find.text('More details')); // fold it back up
    await tester.pumpAndSettle();

    await _submit(tester);

    expect(repo.created, isNull);
    expect(
      find.widgetWithText(SnackBar, 'Company name is required for an organization lead'),
      findsOneWidget,
      reason: 'the user must be told why nothing happened',
    );
    // …and the section that holds the offending field is open again.
    expect(find.byType(SwitchListTile).hitTestable(), findsOneWidget);
  });

  testWidgets('the filled-count follows every optional field', (tester) async {
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.tap(find.text('More details'));
    await tester.pumpAndSettle();
    expect(find.widgetWithText(Badge, '1'), findsNothing);

    // Interested service and Notes are plain text fields, so only their
    // onChanged can move the count — Notes had none, and the header quietly
    // under-reported everything typed into it.
    await tester.enterText(_field('Interested service'), 'Rooms');
    await tester.pumpAndSettle();
    expect(find.widgetWithText(Badge, '1'), findsOneWidget);

    await tester.enterText(_field('Notes'), 'Wants a sea view');
    await tester.pumpAndSettle();
    expect(find.widgetWithText(Badge, '2'), findsOneWidget);
  });

  testWidgets('a collapsed section still validates what is inside it',
      (tester) async {
    // The fields stay mounted while folded away, so a corporate lead whose
    // company was cleared and then hidden cannot slip past.
    final repo = _RecordingLeadRepository();
    await _pumpForm(tester, repo);

    await tester.enterText(_field('Full name *'), 'ACME rep');
    await tester.enterText(_field('Phone'), '0912345678');
    await tester.tap(find.text('More details'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    // Fold it back up with the company still blank.
    await tester.tap(find.text('More details'));
    await tester.pumpAndSettle();

    await _submit(tester);

    expect(repo.created, isNull, reason: 'the hidden rule still applies');
  });
}
