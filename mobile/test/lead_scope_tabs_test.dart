// "Assigned to me" / "Created by me" as tabs on the lead list.
//
// A sales rep has two pools of leads and they are not refinements of one list:
// a lead the rep creates is unassigned until a manager hands it out, so it is
// invisible in the assigned list by definition. The switch used to live inside
// the advanced filter sheet, which left "where did the lead I just entered go?"
// with no discoverable answer.
import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:leadora_mobile/core/network/pagination_response.dart';
import 'package:leadora_mobile/core/theme/app_theme.dart';
import 'package:leadora_mobile/features/lead/data/lead_models.dart';
import 'package:leadora_mobile/features/lead/data/lead_repository.dart';
import 'package:leadora_mobile/features/lead/presentation/screens/lead_list_screen.dart';

/// Answers with one lead named after the scope it was asked for, so a test can
/// tell which list is on screen.
class _ScopedLeadRepository implements LeadRepository {
  final List<LeadScope> scopesRequested = [];

  /// Scopes that should come back empty, to exercise the empty states.
  Set<LeadScope> empty = const {};

  @override
  Future<PaginationResponse<Lead>> getLeads({
    LeadFilters filters = const LeadFilters(),
    int page = 0,
    int size = 15,
    CancelToken? cancelToken,
  }) async {
    scopesRequested.add(filters.scope);
    if (empty.contains(filters.scope)) {
      return PaginationResponse<Lead>.empty();
    }
    return PaginationResponse<Lead>(
      items: [
        Lead(
          leadId: filters.scope.wire,
          fullName: 'Lead in ${filters.scope.label}',
          status: LeadStatus.neww,
        ),
      ],
      page: 0,
      size: size,
      totalElements: 1,
      totalPages: 1,
      isFirst: true,
      isLast: true,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName} is not faked');
}

Future<void> _pump(WidgetTester tester, _ScopedLeadRepository repo) async {
  tester.view.physicalSize = const Size(400, 1200);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  final router = GoRouter(
    initialLocation: '/leads',
    routes: [
      GoRoute(
        path: '/leads',
        builder: (_, _) => const LeadListScreen(),
        routes: [
          GoRoute(
            path: 'new',
            name: 'leadCreate',
            builder: (_, _) => const Scaffold(body: Text('create')),
          ),
          GoRoute(
            path: 'detail/:id',
            name: 'leadDetail',
            builder: (_, _) => const Scaffold(body: Text('detail')),
          ),
        ],
      ),
    ],
  );
  addTearDown(router.dispose);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [leadRepositoryProvider.overrideWithValue(repo)],
      child: MaterialApp.router(
        theme: AppTheme.light(),
        routerConfig: router,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('both pools are on screen, assigned first', (tester) async {
    final repo = _ScopedLeadRepository();
    await _pump(tester, repo);

    expect(find.widgetWithText(Tab, 'Assigned to me'), findsOneWidget);
    expect(find.widgetWithText(Tab, 'Created by me'), findsOneWidget);
    expect(repo.scopesRequested, [LeadScope.assigned]);
    expect(find.text('Lead in Assigned to me'), findsOneWidget);
  });

  testWidgets('switching tabs refetches with the other scope', (tester) async {
    final repo = _ScopedLeadRepository();
    await _pump(tester, repo);

    await tester.tap(find.text('Created by me'));
    await tester.pumpAndSettle();

    expect(repo.scopesRequested.last, LeadScope.created);
    expect(find.text('Lead in Created by me'), findsOneWidget);
    expect(find.text('Lead in Assigned to me'), findsNothing);
  });

  testWidgets('re-tapping the tab already open costs no request',
      (tester) async {
    final repo = _ScopedLeadRepository();
    await _pump(tester, repo);
    expect(repo.scopesRequested, hasLength(1));

    await tester.tap(find.text('Assigned to me'));
    await tester.pumpAndSettle();

    expect(repo.scopesRequested, hasLength(1));
  });

  testWidgets('the scope survives a filter-sheet reset', (tester) async {
    // Clearing a date range must not quietly move the rep back to the other
    // list while they were tidying up unrelated filters.
    final repo = _ScopedLeadRepository();
    await _pump(tester, repo);

    await tester.tap(find.text('Created by me'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Filters'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Reset'));
    await tester.pumpAndSettle();

    expect(repo.scopesRequested.last, LeadScope.created);
    expect(find.text('Lead in Created by me'), findsOneWidget);
  });

  testWidgets('an empty list explains itself per tab', (tester) async {
    final repo = _ScopedLeadRepository()
      ..empty = {LeadScope.assigned, LeadScope.created};
    await _pump(tester, repo);

    // Not "clear your filters" — no filter is the problem here.
    expect(find.text('No leads assigned to you'), findsOneWidget);

    await tester.tap(find.text('Created by me'));
    await tester.pumpAndSettle();
    expect(find.text('You have not created any leads'), findsOneWidget);
  });
}
