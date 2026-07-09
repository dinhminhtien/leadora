// Responsive smoke suite: renders every routable screen across the supported
// phone/tablet viewports and text scales with the network layer faked, and
// fails on any layout exception (RenderFlex/pixel overflow), decode error, or
// error-state fallback. This is the executable form of the "no overflow on
// any screen size" requirement.
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:leadora_mobile/core/localization/generated/app_localizations.dart';
import 'package:leadora_mobile/core/network/api_client.dart';
import 'package:leadora_mobile/core/network/network_providers.dart';
import 'package:leadora_mobile/core/network/pagination_response.dart';
import 'package:leadora_mobile/core/theme/app_theme.dart';
import 'package:leadora_mobile/core/widgets/splash_screen.dart';
import 'package:leadora_mobile/features/auth/data/dto/auth_user.dart';
import 'package:leadora_mobile/features/auth/presentation/providers/auth_controller.dart';
import 'package:leadora_mobile/features/auth/presentation/screens/forgot_password_screen.dart';
import 'package:leadora_mobile/features/auth/presentation/screens/login_screen.dart';
import 'package:leadora_mobile/features/auth/presentation/screens/reset_password_screen.dart';
import 'package:leadora_mobile/features/customer/presentation/screens/customer_detail_screen.dart';
import 'package:leadora_mobile/features/customer/presentation/screens/customer_form_screen.dart';
import 'package:leadora_mobile/features/customer/presentation/screens/customer_list_screen.dart';
import 'package:leadora_mobile/features/dashboard/presentation/screens/dashboard_screen.dart';
import 'package:leadora_mobile/features/dashboard/presentation/screens/more_screen.dart';
import 'package:leadora_mobile/features/deal/presentation/screens/create_deal_screen.dart';
import 'package:leadora_mobile/features/deal/presentation/screens/deal_detail_screen.dart';
import 'package:leadora_mobile/features/deal/presentation/screens/deal_list_screen.dart';
import 'package:leadora_mobile/features/interaction/presentation/screens/interaction_timeline_screen.dart';
import 'package:leadora_mobile/features/interaction/presentation/screens/log_interaction_screen.dart';
import 'package:leadora_mobile/features/lead/presentation/screens/create_lead_screen.dart';
import 'package:leadora_mobile/features/lead/presentation/screens/lead_detail_screen.dart';
import 'package:leadora_mobile/features/lead/presentation/screens/lead_list_screen.dart';
import 'package:leadora_mobile/features/notification/presentation/screens/notification_list_screen.dart';
import 'package:leadora_mobile/features/profile/presentation/screens/change_password_screen.dart';
import 'package:leadora_mobile/features/profile/presentation/screens/edit_profile_screen.dart';
import 'package:leadora_mobile/features/profile/presentation/screens/profile_screen.dart';
import 'package:leadora_mobile/features/quotation/presentation/screens/quotation_detail_screen.dart';
import 'package:leadora_mobile/features/quotation/presentation/screens/quotation_list_screen.dart';
import 'package:leadora_mobile/features/reminder/presentation/screens/reminder_list_screen.dart';
import 'package:leadora_mobile/features/sla/presentation/screens/sla_list_screen.dart';
import 'package:leadora_mobile/features/task/presentation/screens/task_detail_screen.dart';
import 'package:leadora_mobile/features/task/presentation/screens/task_form_screen.dart';
import 'package:leadora_mobile/features/booking/presentation/screens/booking_detail_screen.dart';
import 'package:leadora_mobile/features/booking/presentation/screens/booking_list_screen.dart';
import 'package:leadora_mobile/features/deal/presentation/screens/pipeline_screen.dart';
import 'package:leadora_mobile/features/payment/presentation/screens/generate_payment_screen.dart';
import 'package:leadora_mobile/features/payment/presentation/screens/payment_detail_screen.dart';
import 'package:leadora_mobile/features/payment/presentation/screens/payment_list_screen.dart';
import 'package:leadora_mobile/features/task/presentation/screens/task_list_screen.dart';
import 'package:leadora_mobile/shared/widgets/error_state.dart';

// ── Canned wire data ─────────────────────────────────────────────────────────

final _now = DateTime.now().toUtc();
String _iso(Duration offset) => _now.add(offset).toIso8601String();

Map<String, dynamic> _task(String id, {String? title, bool overdue = false}) =>
    {
      'taskId': id,
      'title': title ?? 'Call: Follow up on quotation',
      'status': 'OPEN',
      'priority': 'HIGH',
      'description': 'Discuss the extended-stay corporate rate before renewal.',
      'assignedUserId': 'u1',
      'assignedUserName': 'Minh Nguyen',
      'createdByName': 'Lan Tran',
      'customerId': 'c1',
      'customerName': 'Saigon Riverside Hotel',
      'customerPhone': '+84 28 3822 9999',
      'customerEmail': 'booking@riverside.vn',
      'startAt': _iso(const Duration(hours: 2)),
      'endAt': _iso(Duration(hours: overdue ? -30 : 26)),
      'createdAt': _iso(const Duration(days: -2)),
      'isOverdue': overdue,
    };

Map<String, dynamic> _paged(List<Map<String, dynamic>> content) => {
  'content': content,
  'number': 0,
  'size': 20,
  'totalElements': content.length,
  'totalPages': 1,
  'first': true,
  'last': true,
};

/// Mirrors `DealResponse`: `stage` is the lossy display label, `stageCode` the
/// authoritative wire enum, `status` the lowercase string the mapper emits.
final Map<String, dynamic> _deal = {
  'id': 'd1',
  'title': 'Annual corporate room block',
  'status': 'active',
  'contactName': 'Pham Thi Thu Huong',
  'email': 'huong.pham@corporate-travel.vn',
  'phone': '+84 90 123 4567',
  'value': 480000000,
  'probability': 60,
  'stage': 'Negotiation',
  'stageCode': 'NEGOTIATION',
  'owner': 'Minh Nguyen',
  'expectedClose': _iso(const Duration(days: 30)),
  'createdAt': _iso(const Duration(days: -20)),
  'notes': 'Waiting on legal review of the master service agreement.',
};

final Map<String, dynamic> _booking = {
  'bookingId': 'b1',
  'bookingCode': 'BK-2026-0091',
  'customerId': 'c1',
  'customerName': 'Saigon Riverside Hotel',
  'assignedUserName': 'Minh Nguyen',
  'checkInDate': '2026-08-14',
  'checkOutDate': '2026-08-17',
  'status': 'CONFIRMED',
  'specialRequests': 'Late check-in, two adjoining rooms.',
  'totalAmount': 259200000,
  'details': [
    {
      'bookingDetailId': 'bd1',
      'productName': 'Deluxe River View',
      'roomNumber': '1204',
      'quantity': 40,
      'unitPrice': 2400000,
      'nights': 3,
      'lineTotal': 288000000,
    },
  ],
  'createdAt': _iso(const Duration(days: -3)),
  'updatedAt': _iso(const Duration(days: -1)),
};

final Map<String, dynamic> _payment = {
  'paymentId': 'p1',
  'bookingId': 'b1',
  'bookingCode': 'BK-2026-0091',
  'customerId': 'c1',
  'customerName': 'Saigon Riverside Hotel',
  'createdByName': 'Minh Nguyen',
  'paymentMethod': 'Bank transfer',
  'gatewayProvider': 'VNPay',
  'gatewayTransactionId': 'VNP-88213',
  'amount': 77760000,
  'paymentType': 'DEPOSIT',
  'status': 'PENDING',
  'dueDate': '2026-08-01',
  'notes': 'Deposit is 30% of the total booking value.',
  'createdAt': _iso(const Duration(days: -2)),
  'updatedAt': _iso(const Duration(days: -2)),
};

final Map<String, dynamic> _quotation = {
  'id': 'q1',
  'quoteNo': 'Q-2026-0142',
  'status': 'SENT',
  'dealId': 'd1',
  'dealName': 'Annual corporate room block',
  'contactName': 'Pham Thi Thu Huong',
  'roomType': 'Deluxe River View',
  'checkInDate': _iso(const Duration(days: 14)),
  'checkOutDate': _iso(const Duration(days: 17)),
  'nights': 3,
  'numberOfRooms': 40,
  'pricePerNight': 2400000,
  'subtotal': 288000000,
  'discountPercent': 10,
  'totalAmount': 259200000,
  'validUntil': _iso(const Duration(days: 7)),
  'createdAt': _iso(const Duration(days: -1)),
};

final Map<String, Object?> _cannedByPath = {
  '/reporting/dashboard-summary': {
    'activeLeadsCount': 24,
    'totalLeadsCount': 61,
    'activeDealsCount': 12,
    'activeDealsValue': 1250000000,
    'weightedPipelineValue': 860000000,
    'totalDealsValue': 2400000000,
    'pendingTasksCount': 8,
    'overdueTasksCount': 2,
    'funnelStages': [
      {'stage': 'NEW', 'count': 14, 'value': 400000000},
      {'stage': 'CONTACTED', 'count': 9, 'value': 350000000},
      {'stage': 'QUALIFIED', 'count': 5, 'value': 300000000},
      {'stage': 'CONVERTED', 'count': 3, 'value': 200000000},
    ],
  },
  '/tasks': _paged([
    _task('t1'),
    _task(
      't2',
      title: 'Meeting: Site inspection — grand ballroom',
      overdue: true,
    ),
    _task('t3', title: 'Email: Send revised group-booking quote'),
  ]),
  '/tasks/t1': _task('t1'),
  '/leads': _paged([
    {
      'leadId': 'l1',
      'fullName': 'Pham Thi Thu Huong',
      'status': 'CONTACTED',
      'email': 'huong.pham@corporate-travel.vn',
      'phone': '+84 90 123 4567',
      'companyName': 'Corporate Travel Vietnam JSC',
      'isCorporate': true,
      'source': 'WEBSITE',
      'assignedUserName': 'Minh Nguyen',
      'createdAt': _iso(const Duration(days: -6)),
    },
  ]),
  '/leads/l1': {
    'leadId': 'l1',
    'fullName': 'Pham Thi Thu Huong',
    'status': 'CONTACTED',
    'email': 'huong.pham@corporate-travel.vn',
    'phone': '+84 90 123 4567',
    'companyName': 'Corporate Travel Vietnam JSC',
    'address': '12 Nguyen Hue Blvd, District 1, HCMC',
    'isCorporate': true,
    'source': 'WEBSITE',
    'notes': 'Interested in a quarterly MICE package for ~120 delegates.',
    'assignedUserName': 'Minh Nguyen',
    'createdByName': 'Lan Tran',
    'createdAt': _iso(const Duration(days: -6)),
  },
  '/customers/list': _paged([
    {
      'customerId': 'c1',
      'customerType': 'CORPORATE',
      'fullName': 'Saigon Riverside Hotel',
      'status': 'ACTIVE',
      'email': 'booking@riverside.vn',
      'phone': '+84 28 3822 9999',
      'companyName': 'Riverside Hospitality Group',
      'assignedUserName': 'Minh Nguyen',
      'createdAt': _iso(const Duration(days: -90)),
    },
  ]),
  '/customers/c1': {
    'customerId': 'c1',
    'customerType': 'CORPORATE',
    'fullName': 'Saigon Riverside Hotel',
    'status': 'ACTIVE',
    'email': 'booking@riverside.vn',
    'phone': '+84 28 3822 9999',
    'companyName': 'Riverside Hospitality Group',
    'taxCode': '0312345678',
    'address': '18 Ton Duc Thang, District 1, HCMC',
    'assignedUserName': 'Minh Nguyen',
    'createdByName': 'Lan Tran',
    'createdAt': _iso(const Duration(days: -90)),
  },
  '/customers/stats': {
    'total': 42,
    'active': 35,
    'inactive': 7,
    'individual': 20,
    'corporate': 22,
  },
  '/customers/c1/history': [
    {
      'type': 'DEAL',
      'id': 'd1',
      'title': 'Annual corporate room block',
      'stage': 'NEGOTIATION',
      'amount': 480000000,
      'createdAt': _iso(const Duration(days: -20)),
    },
  ],
  '/quotations': [_quotation],
  '/quotations/q1': _quotation,
  '/deals': [_deal],
  '/deals/d1': _deal,
  '/payments': _paged([_payment]),
  '/payments/p1': _payment,
  '/bookings': _paged([_booking]),
  '/bookings/b1': _booking,
  '/notifications': [
    {
      'id': 'n1',
      'title': 'Task overdue: Call Saigon Riverside',
      'message': 'The follow-up call is 6 hours past its deadline.',
      'isRead': false,
      'type': 'TASK_OVERDUE',
      'relatedEntity': 'TASK',
      'relatedId': 't1',
      'createdAt': _iso(const Duration(hours: -6)),
    },
    {
      'id': 'n2',
      'title': 'Quotation viewed by customer',
      'message': 'Q-2026-0142 was opened by the recipient.',
      'isRead': true,
      'type': 'QUOTATION',
      'relatedEntity': 'QUOTATION',
      'relatedId': 'q1',
      'createdAt': _iso(const Duration(days: -1)),
    },
  ],
  '/sla/monitoring': [
    {
      'trackingId': 'sla1',
      'entityType': 'LEAD',
      'entityId': 'l1',
      'activityType': 'FIRST_RESPONSE',
      'displayStatus': 'AT_RISK',
      'hoursRemaining': 3,
      'startedAt': _iso(const Duration(hours: -21)),
      'deadlineAt': _iso(const Duration(hours: 3)),
    },
  ],
  '/reminders': [
    {
      'reminderId': 'r1',
      'title': 'Prepare site-visit agenda',
      'priority': 'HIGH',
      'status': 'PENDING',
      'description': 'Ballroom + breakout rooms walkthrough with the client.',
      'remindAt': _iso(const Duration(hours: 20)),
      'relatedEntity': 'TASK',
      'relatedId': 't1',
      'assignedUserName': 'Minh Nguyen',
      'createdAt': _iso(const Duration(days: -1)),
    },
  ],
  '/interaction-timeline': [
    {
      'id': 'i1',
      'type': 'CALL',
      'description': 'Intro call — discussed rate expectations and dates.',
      'agentName': 'Minh Nguyen',
      'linkedName': 'Saigon Riverside Hotel',
      'linkedType': 'CUSTOMER',
      'linkedId': 'c1',
      'occurredAt': _iso(const Duration(days: -3)),
      'createdAt': _iso(const Duration(days: -3)),
    },
  ],
  '/users': [
    {
      'userId': 'u1',
      'fullName': 'Minh Nguyen',
      'email': 'minh@leadora.vn',
      'roleName': 'SALES',
    },
    {
      'userId': 'u2',
      'fullName': 'Lan Tran',
      'email': 'lan@leadora.vn',
      'roleName': 'MANAGER',
    },
  ],
  '/profile/me': {
    'userId': 'u1',
    'fullName': 'Minh Nguyen',
    'email': 'minh@leadora.vn',
    'phone': '+84 91 222 3344',
    'roleName': 'SALES',
    'status': 'ACTIVE',
    'lastLoginAt': _iso(const Duration(hours: -8)),
    'createdAt': _iso(const Duration(days: -200)),
  },
  '/auth/profile': {
    'id': 'u1',
    'email': 'minh@leadora.vn',
    'name': 'Minh Nguyen',
    'roles': ['SALES'],
    'permissions': <String>[],
  },
};

// ── Fakes ────────────────────────────────────────────────────────────────────

/// In-memory [ApiClient]: serves the canned map above through the real
/// repository decode pipeline, so model wire-format bugs also fail this suite.
class FakeApiClient implements ApiClient {
  Object? _lookup(String path) {
    final hit = _cannedByPath[path];
    if (hit == null && !_cannedByPath.containsKey(path)) {
      throw StateError(
        'No canned response for "$path" — add one to the smoke suite.',
      );
    }
    return hit;
  }

  @override
  Dio get raw =>
      throw UnsupportedError('raw Dio is not available in widget tests');

  @override
  Future<T> get<T>(
    String path, {
    Map<String, dynamic>? query,
    Map<String, dynamic>? headers,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) async => decode(_lookup(path));

  @override
  Future<PaginationResponse<T>> getPaged<T>(
    String path, {
    Map<String, dynamic>? query,
    required T Function(Object? item) decodeItem,
    CancelToken? cancelToken,
  }) async => PaginationResponse.parse(_lookup(path), decodeItem);

  @override
  Future<T> post<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? query,
    Map<String, dynamic>? headers,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) async => decode(_lookup(path));

  @override
  Future<T> put<T>(
    String path, {
    Object? data,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) async => decode(_lookup(path));

  @override
  Future<T> patch<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? query,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) async => decode(_lookup(path));

  @override
  Future<T> delete<T>(
    String path, {
    Object? data,
    required T Function(Object? data) decode,
    CancelToken? cancelToken,
  }) async => decode(_lookup(path));

  @override
  Future<T> upload<T>(
    String path, {
    required FormData formData,
    required T Function(Object? data) decode,
    ProgressCallback? onSendProgress,
    Map<String, dynamic>? headers,
    CancelToken? cancelToken,
  }) async => decode(_lookup(path));
}

/// Signed-in [AuthController] that never touches secure storage or Supabase.
class FakeAuthController extends AuthController {
  @override
  Future<AuthUser?> build() async =>
      AuthUser.fromJson(_cannedByPath['/auth/profile'] as Map<String, dynamic>);
}

// ── Harness ──────────────────────────────────────────────────────────────────

/// Viewports: small/normal/large phones, phablet, tablet, and landscape.
const _viewports = <Size>[
  Size(320, 568),
  Size(360, 640),
  Size(390, 844),
  Size(412, 915),
  Size(480, 853),
  Size(600, 960),
  Size(720, 1280),
  Size(844, 390), // phone landscape
];

const _textScales = <double>[1.0, 1.3];

/// Layout/build errors reported while pumping — collected (with the
/// error-causing widget's source location) instead of thrown, so a failure
/// names the exact widget, viewport, and text scale.
Future<List<FlutterErrorDetails>> _pumpScreen(
  WidgetTester tester,
  Widget screen, {
  required Size size,
  required double scale,
  required Brightness brightness,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1.0;

  final collected = <FlutterErrorDetails>[];
  final previousOnError = FlutterError.onError;
  FlutterError.onError = collected.add;

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(FakeApiClient()),
        authControllerProvider.overrideWith(FakeAuthController.new),
      ],
      child: MaterialApp(
        theme: AppTheme.light(),
        darkTheme: AppTheme.dark(),
        themeMode: brightness == Brightness.dark
            ? ThemeMode.dark
            : ThemeMode.light,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(scale)),
          child: child!,
        ),
        home: screen,
      ),
    ),
  );
  // Let futures resolve and entrance animations run their course. Bounded
  // pumps (never pumpAndSettle): skeleton shimmer and spinners are infinite.
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pump(const Duration(milliseconds: 800));

  FlutterError.onError = previousOnError;
  return collected;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() {
    // Offline test environment: use the bundled fallback font instead of
    // fetching Inter over HTTP (which flutter_test blocks).
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  /// One test per screen; inside, every viewport × text scale is rendered.
  /// Any RenderFlex overflow or build exception fails with the combination
  /// named in the failure reason.
  void smokeTest(String name, Widget Function() build, {String? expectText}) {
    testWidgets('$name renders at all sizes and scales', (tester) async {
      addTearDown(tester.view.reset);
      for (final size in _viewports) {
        for (final scale in _textScales) {
          for (final brightness in [Brightness.light, Brightness.dark]) {
            final errors = await _pumpScreen(
              tester,
              build(),
              size: size,
              scale: scale,
              brightness: brightness,
            );
            expect(
              errors,
              isEmpty,
              reason:
                  '$name @ ${size.width.toInt()}x${size.height.toInt()} '
                  'scale $scale ($brightness) reported:\n'
                  '${errors.map((d) => d.toString()).join('\n────\n')}',
            );
            // An error surface means a provider failed (bad canned data or a
            // decode bug) — that must fail loudly, not render. Keyed on the
            // widget, not on "Try again": non-retriable errors (403/404) render
            // no retry button, so the text alone would miss them.
            expect(
              find.byType(ErrorStateView),
              findsNothing,
              reason:
                  '$name @ ${size.width.toInt()}x${size.height.toInt()} '
                  'scale $scale ($brightness) rendered its error state',
            );
          }
        }
      }
      if (expectText != null) {
        expect(
          find.textContaining(expectText),
          findsWidgets,
          reason: '$name should render canned content "$expectText"',
        );
      }
    });
  }

  // Auth + splash
  smokeTest('SplashScreen', () => const SplashScreen());
  smokeTest('LoginScreen', () => const LoginScreen());
  smokeTest('ForgotPasswordScreen', () => const ForgotPasswordScreen());
  smokeTest(
    'ResetPasswordScreen',
    () => const ResetPasswordScreen(token: 'tok'),
  );

  // Dashboard
  smokeTest(
    'DashboardScreen',
    () => const DashboardScreen(),
    expectText: 'ACTIVE LEADS',
  ); // KPI labels render uppercased

  // Leads
  smokeTest(
    'LeadListScreen',
    () => const LeadListScreen(),
    expectText: 'Pham Thi Thu Huong',
  );
  smokeTest('CreateLeadScreen', () => const CreateLeadScreen());
  smokeTest(
    'LeadDetailScreen',
    () => const LeadDetailScreen(leadId: 'l1'),
    expectText: 'Pham Thi Thu Huong',
  );

  // Tasks
  smokeTest(
    'TaskListScreen',
    () => const TaskListScreen(),
    expectText: 'Follow up on quotation',
  );
  smokeTest(
    'TaskDetailScreen',
    () => const TaskDetailScreen(taskId: 't1'),
    expectText: 'Follow up on quotation',
  );
  smokeTest(
    'TaskFormScreen (create)',
    () => const TaskFormScreen(mode: TaskFormMode.create),
  );
  smokeTest(
    'TaskFormLoader (edit)',
    () => const TaskFormLoader(mode: TaskFormMode.edit, taskId: 't1'),
  );
  smokeTest(
    'TaskFormLoader (resign)',
    () => const TaskFormLoader(mode: TaskFormMode.resign, taskId: 't1'),
  );

  // Customers
  smokeTest(
    'CustomerListScreen',
    () => const CustomerListScreen(),
    expectText: 'Saigon Riverside Hotel',
  );
  smokeTest(
    'CustomerDetailScreen',
    () => const CustomerDetailScreen(customerId: 'c1'),
    expectText: 'Saigon Riverside Hotel',
  );
  smokeTest(
    'CustomerFormScreen (create)',
    () => const CustomerFormScreen(mode: CustomerFormMode.create),
  );
  smokeTest(
    'CustomerFormLoader (edit)',
    () => const CustomerFormLoader(customerId: 'c1'),
  );

  // Quotations & deals
  smokeTest(
    'QuotationListScreen',
    () => const QuotationListScreen(),
    expectText: 'Q-2026-0142',
  );
  smokeTest(
    'QuotationDetailScreen',
    () => const QuotationDetailScreen(quotationId: 'q1'),
    expectText: 'Q-2026-0142',
  );
  smokeTest(
    'DealDetailScreen',
    () => const DealDetailScreen(dealId: 'd1'),
    expectText: 'Annual corporate room block',
  );
  smokeTest(
    'DealListScreen',
    () => const DealListScreen(),
    expectText: 'Annual corporate room block',
  );
  smokeTest('CreateDealScreen', () => const CreateDealScreen());
  smokeTest(
    'PipelineScreen',
    () => const PipelineScreen(),
    expectText: 'Annual corporate room block',
  );

  // Reservations
  smokeTest(
    'PaymentListScreen',
    () => const PaymentListScreen(),
    expectText: 'Saigon Riverside Hotel',
  );
  smokeTest(
    'PaymentDetailScreen',
    () => const PaymentDetailScreen(paymentId: 'p1'),
    expectText: 'BK-2026-0091',
  );
  smokeTest('GeneratePaymentScreen', () => const GeneratePaymentScreen());
  smokeTest(
    'BookingListScreen',
    () => const BookingListScreen(),
    expectText: 'BK-2026-0091',
  );
  smokeTest(
    // The room lines sit below the fold on a 320x568 viewport and a lazy
    // ListView never builds them, so assert on the header instead.
    'BookingDetailScreen',
    () => const BookingDetailScreen(bookingId: 'b1'),
    expectText: 'BK-2026-0091',
  );

  // More hub
  smokeTest('MoreScreen', () => const MoreScreen(), expectText: 'Quotations');

  // Interactions
  smokeTest(
    'InteractionTimelineScreen',
    () => const InteractionTimelineScreen(
      linkedType: 'CUSTOMER',
      linkedId: 'c1',
      linkedName: 'Saigon Riverside Hotel',
    ),
  );
  smokeTest(
    'LogInteractionScreen',
    () => const LogInteractionScreen(
      linkedType: 'CUSTOMER',
      linkedId: 'c1',
      linkedName: 'Saigon Riverside Hotel',
    ),
  );

  // Browse entry points
  smokeTest(
    'NotificationListScreen',
    () => const NotificationListScreen(),
    expectText: 'Task overdue',
  );
  smokeTest('SlaListScreen', () => const SlaListScreen());
  smokeTest(
    'ReminderListScreen',
    () => const ReminderListScreen(),
    expectText: 'Prepare site-visit agenda',
  );

  // Profile
  smokeTest(
    'ProfileScreen',
    () => const ProfileScreen(),
    expectText: 'Minh Nguyen',
  );
  smokeTest('EditProfileLoader', () => const EditProfileLoader());
  smokeTest('ChangePasswordScreen', () => const ChangePasswordScreen());

  // A wrapped button label is not a layout exception, so the smoke suite above
  // cannot see it. Pin the symptom directly: on the narrowest phone at an
  // accessibility text scale, the two lifecycle buttons must still share a
  // baseline and the primary label must stay on one line.
  testWidgets('TaskDetailScreen actions never wrap at 320dp / scale 1.3', (
    tester,
  ) async {
    addTearDown(tester.view.reset);

    final errors = await _pumpScreen(
      tester,
      const TaskDetailScreen(taskId: 't1'),
      size: const Size(320, 568),
      scale: 1.3,
      brightness: Brightness.light,
    );
    expect(errors, isEmpty);

    final complete = find.widgetWithText(FilledButton, 'Mark complete');
    final cancel = find.byType(OutlinedButton);
    expect(complete, findsOneWidget);
    expect(cancel, findsOneWidget);

    expect(
      tester.getSize(cancel).height,
      tester.getSize(complete).height,
      reason:
          'Cancel and Mark-complete must share a baseline; a wrapped '
          '"Cancel task" label used to make the outlined button taller.',
    );

    expect(
      tester.widget<Text>(find.text('Mark complete')).maxLines,
      1,
      reason: 'The primary label must ellipsize rather than wrap.',
    );

    // Cancel is icon-only now; its accessible name comes from the tooltip and
    // the icon's semantic label, not a visible Text.
    expect(find.byTooltip('Cancel task'), findsOneWidget);
    expect(find.text('Cancel task'), findsNothing);

    // Icon-only must still clear the 48dp minimum tap target.
    expect(tester.getSize(cancel).width, greaterThanOrEqualTo(48));
    expect(tester.getSize(cancel).height, greaterThanOrEqualTo(48));
  });
}
