// UC-8.5 E6 — converting a lead whose contact details already belong to a
// customer.
//
// The server refuses with 409 `DUPLICATE_CUSTOMER_EMAIL`/`_PHONE` and puts that
// customer's id in `details`. Mobile carried `linkLeadToCustomer` and its payload
// with a doc comment saying they were "reached from the 409 convertLead returns",
// and nothing ever called them — so on a phone the refusal was a snackbar and a
// dead end, while the web offered to attach the lead to the existing profile.
//
// These tests pin the branch down at the two points that matter: that the
// affordance appears only for a refusal it can actually fix, and that taking it
// posts the id the server handed back.
import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:leadora_mobile/core/network/api_exception.dart';
import 'package:leadora_mobile/core/network/pagination_response.dart';
import 'package:leadora_mobile/core/theme/app_theme.dart';
import 'package:leadora_mobile/features/auth/data/dto/auth_user.dart';
import 'package:leadora_mobile/features/auth/presentation/providers/auth_controller.dart';
import 'package:leadora_mobile/features/lead/data/lead_models.dart';
import 'package:leadora_mobile/features/lead/data/lead_repository.dart';
import 'package:leadora_mobile/features/lead/presentation/providers/lead_providers.dart';
import 'package:leadora_mobile/features/lead/presentation/screens/lead_detail_screen.dart';

const _leadId = 'lead-1';
const _existingCustomerId = 'customer-9';

/// A QUALIFIED, assigned lead — the state in which conversion is offered without
/// a manager override, so the tests exercise the duplicate branch and not BR-07.
const _lead = Lead(
  leadId: _leadId,
  fullName: 'Tran Nhat Minh',
  status: LeadStatus.qualified,
  email: 'minh@example.com',
  phone: '0912345678',
  assignedUserId: 'user-1',
  assignedUserName: 'Sam Staff',
);

const _staff = AuthUser(
  id: 'user-1',
  email: 'sam@leadora.vn',
  name: 'Sam Staff',
  roles: ['SALES'],
);

/// Fails `convertLead` with [convertError] and records what `linkLeadToCustomer`
/// was called with.
class _FakeLeadRepository implements LeadRepository {
  _FakeLeadRepository({required this.convertError});

  final Object convertError;
  LinkLeadToCustomerPayload? linkedWith;
  String? linkedLeadId;

  @override
  Future<String> convertLead(String leadId, ConvertLeadPayload payload) async {
    throw convertError;
  }

  @override
  Future<String> linkLeadToCustomer(
    String leadId,
    LinkLeadToCustomerPayload payload,
  ) async {
    linkedLeadId = leadId;
    linkedWith = payload;
    return payload.customerId;
  }

  @override
  Future<Lead> getLead(String leadId) async => _lead;

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

ApiException _duplicateCustomer({
  String code = 'DUPLICATE_CUSTOMER_EMAIL',
  String? details = _existingCustomerId,
}) {
  return ApiException(
    message: "A customer with email 'minh@example.com' already exists.",
    errorCode: code,
    details: details,
    statusCode: 409,
  );
}

Future<void> _pumpDetail(WidgetTester tester, _FakeLeadRepository repo) async {
  // Tall enough that the sheet builds every control; the default 600dp surface
  // clips the buttons under the eligibility note.
  tester.view.physicalSize = const Size(400, 1800);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        leadRepositoryProvider.overrideWithValue(repo),
        currentUserProvider.overrideWithValue(_staff),
        leadDetailProvider(_leadId).overrideWith((ref) async => _lead),
      ],
      child: MaterialApp(
        theme: AppTheme.light(),
        home: const LeadDetailScreen(leadId: _leadId),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// Opens the convert sheet and presses Confirm, which is what provokes the 409.
Future<void> _attemptConversion(WidgetTester tester) async {
  await tester.tap(find.text('Convert to customer').last);
  await tester.pumpAndSettle();
  await tester.tap(find.text('Confirm conversion'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('a duplicate-customer refusal offers to link, not just complain',
      (tester) async {
    final repo = _FakeLeadRepository(convertError: _duplicateCustomer());
    await _pumpDetail(tester, repo);
    await _attemptConversion(tester);

    // The refusal explains itself and, crucially, leads somewhere.
    expect(find.textContaining('already exists'), findsOneWidget);
    expect(find.text('Link to existing customer'), findsOneWidget);
    // Converting again could only fail identically, so that button steps aside.
    expect(find.text('Confirm conversion'), findsNothing);
  });

  testWidgets('linking posts the customer id the server handed back',
      (tester) async {
    final repo = _FakeLeadRepository(convertError: _duplicateCustomer());
    await _pumpDetail(tester, repo);
    await _attemptConversion(tester);

    await tester.tap(find.text('Link to existing customer'));
    await tester.pumpAndSettle();

    expect(repo.linkedLeadId, _leadId);
    expect(repo.linkedWith?.customerId, _existingCustomerId);
    // The lead is QUALIFIED, so there is no BR-07 exception to record.
    expect(repo.linkedWith?.reason, isNull);
  });

  testWidgets('a phone collision is the same branch, worded for the phone',
      (tester) async {
    final repo = _FakeLeadRepository(
      convertError: _duplicateCustomer(code: 'DUPLICATE_CUSTOMER_PHONE'),
    );
    await _pumpDetail(tester, repo);
    await _attemptConversion(tester);

    expect(find.textContaining('phone number'), findsOneWidget);
    expect(find.text('Link to existing customer'), findsOneWidget);
  });

  testWidgets('an unrelated failure gets no link button', (tester) async {
    final repo = _FakeLeadRepository(
      convertError: const ApiException(
        message: 'Something else went wrong.',
        errorCode: 'LEAD_LOCKED',
        statusCode: 422,
      ),
    );
    await _pumpDetail(tester, repo);
    await _attemptConversion(tester);

    expect(find.text('Link to existing customer'), findsNothing);
    expect(find.text('Something else went wrong.'), findsOneWidget);
  });

  testWidgets('a 409 carrying no customer id is reported, not offered',
      (tester) async {
    // Without an id there is nothing to link to; a button here would post
    // nothing and fail a second time.
    final repo = _FakeLeadRepository(
      convertError: _duplicateCustomer(details: null),
    );
    await _pumpDetail(tester, repo);
    await _attemptConversion(tester);

    expect(find.text('Link to existing customer'), findsNothing);
  });
}
