// User Acceptance Testing (UAT) suite — Leadora CRM Mobile.
//
// Three business workflow scenarios are validated end-to-end through real widget
// interactions using a hermetic UatApiClient that can be configured per-test
// without touching the network.
//
// UAT-01: RBAC — SALES cannot moderate feedback; MANAGER can and the request reaches the API.
// UAT-02: Discount policy — a DRAFT quotation with >10% discount shows the approval-path
//         warning dialog when the Sales rep taps Submit, and the submit POST is recorded.
// UAT-03: Handover readiness — a NEED_CLARIFICATION handover surfaces the note in both the
//         list row and the detail screen after navigation.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:dio/dio.dart';

import 'package:leadora_mobile/core/localization/generated/app_localizations.dart';
import 'package:leadora_mobile/core/network/network_providers.dart';
import 'package:leadora_mobile/core/network/pagination_response.dart';
import 'package:leadora_mobile/core/theme/app_theme.dart';
import 'package:leadora_mobile/features/auth/presentation/providers/auth_controller.dart';
import 'package:leadora_mobile/features/auth/data/dto/auth_user.dart';
import 'package:leadora_mobile/features/feedback/presentation/screens/feedback_detail_screen.dart';
import 'package:leadora_mobile/features/quotation/presentation/screens/quotation_detail_screen.dart';
import 'package:leadora_mobile/features/handover/presentation/screens/handover_list_screen.dart';
import 'package:leadora_mobile/features/handover/presentation/screens/handover_detail_screen.dart';
import 'package:leadora_mobile/features/feedback/presentation/providers/feedback_providers.dart';

import 'responsive_smoke_test.dart'; // FakeApiClient, canned data map

// ─── UAT API client ──────────────────────────────────────────────────────────

/// Extends [FakeApiClient] with per-test response overrides and a recorded-call
/// log so assertions can verify the right endpoints were called.
class UatApiClient extends FakeApiClient {
  /// Per-test path → response body overrides.  Entries here are tried first;
  /// paths not in this map fall through to the smoke-suite canned data.
  final Map<String, Object?> overrides = {};

  /// All POST paths the test triggered.
  final List<String> recordedPosts = [];

  /// All PATCH paths the test triggered.
  final List<String> recordedPatches = [];

  @override
  Future<T> get<T>(
    String path, {
    Map<String, dynamic>? query,
    Map<String, dynamic>? headers,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) async {
    if (overrides.containsKey(path)) return decode(overrides[path]);
    return super.get(
      path,
      query: query,
      headers: headers,
      decode: decode,
      cancelToken: cancelToken,
    );
  }

  @override
  Future<PaginationResponse<T>> getPaged<T>(
    String path, {
    Map<String, dynamic>? query,
    required T Function(Object? item) decodeItem,
    CancelToken? cancelToken,
  }) async {
    if (overrides.containsKey(path)) {
      return PaginationResponse.parse(overrides[path], decodeItem);
    }
    return super.getPaged(
      path,
      query: query,
      decodeItem: decodeItem,
      cancelToken: cancelToken,
    );
  }

  @override
  Future<T> post<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? query,
    Map<String, dynamic>? headers,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) async {
    recordedPosts.add(path);
    if (overrides.containsKey(path)) return decode(overrides[path]);
    return super.post(
      path,
      data: data,
      query: query,
      headers: headers,
      decode: decode,
      cancelToken: cancelToken,
    );
  }

  @override
  Future<T> patch<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? query,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) async {
    recordedPatches.add(path);
    if (overrides.containsKey(path)) return decode(overrides[path]);
    // Moderation endpoints return 204 No Content → decode(null) is correct.
    return decode(null);
  }
}

// ─── Fake auth controllers ────────────────────────────────────────────────────

class SalesAuthController extends AuthController {
  @override
  Future<AuthUser?> build() async => const AuthUser(
        id: 'u1',
        email: 'sales@leadora.vn',
        name: 'Sales Staff',
        roles: ['SALES'],
        permissions: [],
      );
}

class ManagerAuthController extends AuthController {
  @override
  Future<AuthUser?> build() async => const AuthUser(
        id: 'u2',
        email: 'manager@leadora.vn',
        name: 'Manager User',
        roles: ['MANAGER'],
        permissions: [],
      );
}

// ─── Shared test harness helpers ──────────────────────────────────────────────

/// Wraps [screen] in the Riverpod+Material stack the UAT tests need.
///
/// Uses a plain [MaterialApp] with [home] so there are no routing dependencies.
/// Screens that only call [Navigator.push] (not go_router) work correctly here;
/// any go_router navigation (e.g. `context.push`) is only invoked on user tap
/// and is never triggered inside the assertions below.
Widget _uatApp({
  Key? key,
  required Widget screen,
  required UatApiClient client,
  required AuthController Function() authFactory,
}) {
  return ProviderScope(
    key: key,
    overrides: [
      apiClientProvider.overrideWithValue(client),
      authControllerProvider.overrideWith(authFactory),
    ],
    child: MaterialApp(
      theme: AppTheme.light(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: screen,
    ),
  );
}

/// Pumps frames until the async providers resolve, then lets animations
/// finish.  Uses a bounded strategy so infinite animations (skeletons, etc.)
/// do not cause pumpAndSettle to time out.
Future<void> _settle(WidgetTester tester) async {
  await tester.pump();                                    // schedule micro-tasks
  await tester.pump(const Duration(milliseconds: 100));  // short async gap
  await tester.pump(const Duration(milliseconds: 500));  // provider future
  await tester.pump(const Duration(milliseconds: 1000)); // UI rebuild
  await tester.pump(const Duration(milliseconds: 1000)); // animation settle
}

// ─── Test suite ───────────────────────────────────────────────────────────────

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() {
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('User Acceptance Testing (UAT) - Leadora CRM Mobile', () {
    // ── UAT-01 ──────────────────────────────────────────────────────────────
    testWidgets(
      'UAT-01: SALES role cannot moderate feedback; MANAGER role can and '
      'the PATCH reaches the review-status endpoint',
      (WidgetTester tester) async {
        final uatClient = UatApiClient();

        // A pending feedback record returned by the fake API.
        final feedbackPayload = {
          'feedbackId': 'f1',
          'customerName': 'Nguyen Thi Hong Nhung',
          'bookingCode': 'BK-240815',
          'salesStaffName': 'Minh Nguyen',
          'rating': 5,
          'ratingAttitude': 5,
          'ratingSpeed': 4,
          'ratingAccuracy': 5,
          'comment': 'Excellent support from initial query to final check-in.',
          'reviewStatus': 'PENDING',
          'submittedAt': DateTime.now().toUtc().toIso8601String(),
          'createdAt': DateTime.now().toUtc().toIso8601String(),
        };

        uatClient.overrides['/feedbacks/f1'] = feedbackPayload;
        uatClient.overrides['/feedbacks'] = {
          'content': [feedbackPayload],
          'number': 0,
          'size': 10,
          'totalElements': 1,
          'totalPages': 1,
          'first': true,
          'last': true,
        };

        // ── Step 1: Sales rep sees the feedback but no moderation controls ──
        await tester.pumpWidget(
          _uatApp(
            key: const ValueKey('sales-role'),
            screen: const FeedbackDetailScreen(feedbackId: 'f1'),
            client: uatClient,
            authFactory: SalesAuthController.new,
          ),
        );
        await _settle(tester);

        // RBAC gate: moderation section must be absent for SALES.
        expect(find.text('Moderation'), findsNothing,
            reason: 'SALES should not see the Moderation section');
        expect(find.text('Mark reviewed'), findsNothing);
        expect(find.text('Dismiss'), findsNothing);

        // ── Step 2: Manager sees and uses the moderation controls ────────────
        await tester.pumpWidget(
          _uatApp(
            key: const ValueKey('manager-role'),
            screen: const FeedbackDetailScreen(feedbackId: 'f1'),
            client: uatClient,
            authFactory: ManagerAuthController.new,
          ),
        );
        await _settle(tester);

        // Initialize feedback list controller provider state using ProviderScope container
        // and keep it alive by subscribing to it (otherwise AutoDispose immediately disposes it).
        final container = ProviderScope.containerOf(
          tester.element(find.byType(FeedbackDetailScreen)),
        );
        final listener = container.listen(feedbackListControllerProvider, (prev, next) {});
        await container.read(feedbackListControllerProvider.future);

        final scrollableFinder = find.descendant(
          of: find.byType(FeedbackDetailScreen),
          matching: find.byType(Scrollable),
        );
        // Drag to reveal the Moderation section and controls at the bottom of the screen
        await tester.drag(scrollableFinder, const Offset(0.0, -400.0));
        await tester.pump(const Duration(milliseconds: 200));

        expect(find.text('Moderation'), findsOneWidget,
            reason: 'MANAGER should see the Moderation section');
        expect(find.text('Mark reviewed'), findsOneWidget);
        expect(find.text('Dismiss'), findsOneWidget);

        // Tap "Mark reviewed" — triggers PATCH /feedbacks/f1/review-status.
        await tester.tap(find.text('Mark reviewed'));
        await _settle(tester);

        // ── Verification ────────────────────────────────────────────────────
        expect(
          uatClient.recordedPatches.any((p) => p.contains('/feedbacks/f1/review-status')),
          isTrue,
          reason: 'PATCH to the review-status endpoint should have been recorded',
        );
        debugPrint('UAT-01: RBAC enforcement verified — SALES blocked, MANAGER approved');
        listener.close();
      },
    );

    // ── UAT-02 ──────────────────────────────────────────────────────────────
    testWidgets(
      'UAT-02: A DRAFT quotation with >10% discount shows the manager-approval '
      'dialog when submitted, and the POST is sent to /quotations/q1/submit',
      (WidgetTester tester) async {
        final uatClient = UatApiClient();

        // DRAFT quotation with 15% discount — exceeds the 10% auto-approve threshold.
        final draftQuotation = {
          'id': 'q1',
          'quoteNo': 'Q-2026-0142',
          'status': 'draft', // wire value is lowercase per QuotationStatus.fromWire
          'contactName': 'Pham Thi Thu Huong',
          'email': 'huong@corporate-travel.vn',
          'phone': '+84 90 123 4567',
          'roomType': 'Deluxe River View',
          'checkInDate': '2026-08-14T00:00:00.000Z',
          'checkOutDate': '2026-08-17T00:00:00.000Z',
          'nights': 3,
          'numberOfRooms': 40,
          'pricePerNight': 2400000.0,
          'subtotal': 288000000.0,
          'discountPercent': 15.0, // > 10% → PENDING_APPROVAL path
          'totalAmount': 244800000.0,
          'validUntil': '2026-09-07T00:00:00.000Z',
          'createdAt': '2026-08-13T00:00:00.000Z',
        };

        final pendingQuotation =
            Map<String, dynamic>.from(draftQuotation)..['status'] = 'pending_approval';

        uatClient.overrides['/quotations/q1'] = draftQuotation;
        uatClient.overrides['/quotations/q1/submit'] = pendingQuotation;

        await tester.pumpWidget(
          _uatApp(
            screen: const QuotationDetailScreen(quotationId: 'q1'),
            client: uatClient,
            authFactory: SalesAuthController.new,
          ),
        );
        await _settle(tester);

        // ── Step 1: Scroll to the bottom to reveal the "Submit quotation" button ─
        final scrollableFinder = find.descendant(
          of: find.byType(QuotationDetailScreen),
          matching: find.byType(Scrollable),
        );

        // Drag down to reveal bottom content and submit button
        await tester.drag(scrollableFinder, const Offset(0.0, -400.0));
        await tester.pump(const Duration(milliseconds: 200));
        await tester.drag(scrollableFinder, const Offset(0.0, -400.0));
        await tester.pump(const Duration(milliseconds: 200));

        debugPrint('Quotation detail texts after drag: ${tester.widgetList(find.byType(Text)).map((w) => (w as Text).data ?? (w).textSpan?.toPlainText()).where((t) => t != null && t.isNotEmpty).toList()}');

        expect(find.text('Submit quotation'), findsOneWidget,
            reason: 'DRAFT quotation should have the Submit button visible after scrolling');

        // ── Step 2: Tap Submit → confirmation dialog appears ─────────────────
        await tester.tap(find.text('Submit quotation'));
        await _settle(tester);

        expect(find.text('Submit quotation?'), findsOneWidget,
            reason: 'Confirmation dialog title should appear');
        expect(
          find.text(
            'The discount is above 10%, so this goes to a manager for approval '
            'before it can be sent.',
          ),
          findsOneWidget,
          reason: 'Dialog should warn that manager approval is required',
        );

        // ── Step 3: Confirm → POST reaches the submit endpoint ───────────────
        await tester.tap(find.text('Submit'));
        uatClient.overrides['/quotations/q1'] = pendingQuotation; // reflect new state
        await _settle(tester);

        expect(
          uatClient.recordedPosts.contains('/quotations/q1/submit'),
          isTrue,
          reason: 'POST to /quotations/q1/submit should have been recorded',
        );
        debugPrint('UAT-02: Discount approval policy enforced — >10% routes to manager');
      },
    );

    // ── UAT-03 ──────────────────────────────────────────────────────────────
    testWidgets(
      'UAT-03: A NEED_CLARIFICATION handover shows the clarification note '
      'in the list, and the detail screen after navigation',
      (WidgetTester tester) async {
        final uatClient = UatApiClient();

        final handoverPayload = {
          'handoverId': 'h1',
          'bookingId': 'b1',
          'bookingCode': 'BK-2026-0091',
          'customerName': 'Saigon Riverside Hotel',
          'customerPhone': '+84 28 3822 9999',
          'checkInDate': '2026-08-20', // future so "Arriving" chip is absent
          'checkOutDate': '2026-08-23',
          'roomSummary': '40 x Deluxe River View',
          'rooms': [
            {
              'productName': 'Deluxe River View',
              'roomNumber': '812',
              'quantity': 40,
              'nights': 3,
            },
          ],
          'specialRequests': 'Late check-in, two adjoining rooms.',
          'roomPreferences': 'High floor, river side.',
          'vipNotes': 'Managing director travelling with the group.',
          'operationalNotes': 'Coach arrives at the side entrance.',
          'paymentReference': 'PAY-2026-0044',
          'status': 'ACKNOWLEDGED',
          'readinessStatus': 'NEED_CLARIFICATION',
          'clarificationNote':
              'Adjoining rooms unavailable on the high floor — confirm a swap.',
          'submittedAt': DateTime.now().toUtc().subtract(const Duration(days: 2)).toIso8601String(),
          'acknowledgedAt': DateTime.now().toUtc().subtract(const Duration(days: 1)).toIso8601String(),
          'updatedByName': 'Thao Le',
          'createdAt': DateTime.now().toUtc().subtract(const Duration(days: 2)).toIso8601String(),
        };

        uatClient.overrides['/operational-handovers'] = {
          'content': [handoverPayload],
          'number': 0,
          'size': 20,
          'totalElements': 1,
          'totalPages': 1,
          'first': true,
          'last': true,
        };
        uatClient.overrides['/operational-handovers/h1'] = handoverPayload;

        await tester.pumpWidget(
          _uatApp(
            screen: const HandoverListScreen(),
            client: uatClient,
            authFactory: SalesAuthController.new,
          ),
        );
        await _settle(tester);

        // ── Step 1: Clarification note surfaced inline on the list row ────────
        expect(find.text('BK-2026-0091'), findsOneWidget,
            reason: 'Booking code should appear on the list row');
        expect(
          find.text('Adjoining rooms unavailable on the high floor — confirm a swap.'),
          findsOneWidget,
          reason: 'NEED_CLARIFICATION note should be visible inline on the list',
        );

        // ── Step 2: Tap the card → detail screen pushes onto the navigator ────
        await tester.tap(find.byType(HandoverCard));
        // Extra settle time: Navigator.push + new provider resolution needs more frames.
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 200));
        await tester.pump(const Duration(milliseconds: 1000));
        await tester.pump(const Duration(milliseconds: 1000));

        // ── Step 3: Detail screen confirms same note and payment reference ────
        expect(find.byType(HandoverDetailScreen), findsOneWidget,
            reason: 'HandoverDetailScreen should have been pushed');
        // "Clarification requested" is a section card that may be below the fold in the
        // detail ListView — use skipOffstage:false to confirm it's in the widget tree,
        // then scroll it into view for the content assertion.
        expect(find.text('Clarification requested', skipOffstage: false), findsOneWidget,
            reason: 'Section heading should be in the detail widget tree');

        final scrollableFinder = find.descendant(
          of: find.byType(HandoverDetailScreen),
          matching: find.byType(Scrollable),
        );

        // Drag down to reveal clarification notes and payment information
        await tester.drag(scrollableFinder, const Offset(0.0, -300.0));
        await tester.pump(const Duration(milliseconds: 200));
        await tester.drag(scrollableFinder, const Offset(0.0, -300.0));
        await tester.pump(const Duration(milliseconds: 200));

        debugPrint('Detail screen texts after drag: ${tester.widgetList(find.byType(Text)).map((w) => (w as Text).data ?? (w).textSpan?.toPlainText()).where((t) => t != null && t.isNotEmpty).toList()}');

        expect(find.text('Clarification requested'), findsOneWidget,
            reason: 'Section heading should appear on the detail screen after scroll');
        expect(
          find.text('Adjoining rooms unavailable on the high floor — confirm a swap.'),
          findsOneWidget,
          reason: 'Clarification note should also appear on the detail screen',
        );
        expect(find.text('PAY-2026-0044'), findsOneWidget,
            reason: 'Payment reference should appear in the Payment section');
        debugPrint('UAT-03: Handover clarification surfaced in list and detail');
      },
    );
  });
}
