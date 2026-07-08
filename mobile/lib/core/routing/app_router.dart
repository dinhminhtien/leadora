import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/screens/forgot_password_screen.dart';
import '../../features/auth/presentation/screens/login_screen.dart';
import '../../features/auth/presentation/screens/reset_password_screen.dart';
import '../../features/customer/data/customer_models.dart';
import '../../features/customer/presentation/screens/customer_detail_screen.dart';
import '../../features/customer/presentation/screens/customer_form_screen.dart';
import '../../features/customer/presentation/screens/customer_list_screen.dart';
import '../../features/dashboard/presentation/screens/dashboard_screen.dart';
import '../../features/dashboard/presentation/screens/dashboard_shell.dart';
import '../../features/deal/presentation/screens/deal_detail_screen.dart';
import '../../features/interaction/presentation/screens/interaction_timeline_screen.dart';
import '../../features/interaction/presentation/screens/log_interaction_screen.dart';
import '../../features/lead/presentation/screens/create_lead_screen.dart';
import '../../features/lead/presentation/screens/lead_detail_screen.dart';
import '../../features/lead/presentation/screens/lead_list_screen.dart';
import '../../features/notification/presentation/screens/notification_list_screen.dart';
import '../../features/profile/presentation/screens/change_password_screen.dart';
import '../../features/profile/presentation/screens/profile_screen.dart';
import '../../features/quotation/presentation/screens/quotation_detail_screen.dart';
import '../../features/quotation/presentation/screens/quotation_list_screen.dart';
import '../../features/reminder/presentation/screens/reminder_list_screen.dart';
import '../../features/sla/presentation/screens/sla_list_screen.dart';
import '../../features/task/data/task_models.dart';
import '../../features/task/presentation/screens/task_detail_screen.dart';
import '../../features/task/presentation/screens/task_form_screen.dart';
import '../../features/task/presentation/screens/task_list_screen.dart';
import '../widgets/splash_screen.dart';
import 'app_session.dart';
import 'routes.dart';

/// Navigator keys — a root key plus one per shell branch so nested navigation
/// is isolated per tab.
final _rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');
final _shellHomeKey = GlobalKey<NavigatorState>(debugLabel: 'shell-home');
final _shellLeadsKey = GlobalKey<NavigatorState>(debugLabel: 'shell-leads');
final _shellTasksKey = GlobalKey<NavigatorState>(debugLabel: 'shell-tasks');
final _shellQuotationsKey = GlobalKey<NavigatorState>(debugLabel: 'shell-quotations');
final _shellProfileKey = GlobalKey<NavigatorState>(debugLabel: 'shell-profile');

/// The app router. Depends on [appSessionProvider] so the redirect guard and
/// `refreshListenable` react to auth changes. Built once and kept alive.
final routerProvider = Provider<GoRouter>((ref) {
  final session = ref.watch(appSessionProvider);

  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: Routes.splash,
    refreshListenable: session,
    debugLogDiagnostics: true,
    redirect: (context, state) => _guard(session, state),
    routes: [
      GoRoute(
        path: Routes.splash,
        name: RouteNames.splash,
        builder: (_, _) => const SplashScreen(),
      ),
      GoRoute(
        path: Routes.login,
        name: RouteNames.login,
        builder: (_, _) => const LoginScreen(),
      ),
      GoRoute(
        path: Routes.forgotPassword,
        name: RouteNames.forgotPassword,
        builder: (_, _) => const ForgotPasswordScreen(),
      ),
      GoRoute(
        path: Routes.resetPassword,
        name: RouteNames.resetPassword,
        builder: (_, state) {
          final token = state.uri.queryParameters['token'] ?? '';
          return ResetPasswordScreen(token: token);
        },
      ),

      // Full-screen routes reached only via notification deep-link — no
      // list/tab entry point exists yet (see NotificationListScreen._relatedRoute).
      GoRoute(
        path: Routes.quotationDetail,
        name: RouteNames.quotationDetail,
        builder: (_, state) =>
            QuotationDetailScreen(quotationId: state.pathParameters['id']!),
      ),
      GoRoute(
        path: Routes.dealDetail,
        name: RouteNames.dealDetail,
        builder: (_, state) => DealDetailScreen(dealId: state.pathParameters['id']!),
      ),

      // Browse entry points — reached from the Dashboard quick actions and
      // (notifications) the header bell.
      GoRoute(
        path: Routes.notifications,
        name: RouteNames.notifications,
        builder: (_, _) => const NotificationListScreen(),
      ),
      GoRoute(
        path: Routes.sla,
        name: RouteNames.sla,
        builder: (_, state) =>
            SlaListScreen(highlightId: state.uri.queryParameters['highlight']),
      ),
      GoRoute(
        path: Routes.reminders,
        name: RouteNames.reminders,
        builder: (_, state) =>
            ReminderListScreen(highlightId: state.uri.queryParameters['highlight']),
      ),

      // Customer profiles — full-screen browse (no dedicated tab), reached from
      // the Dashboard quick actions. List → detail → edit, plus create.
      GoRoute(
        path: Routes.customers,
        name: RouteNames.customers,
        builder: (_, _) => const CustomerListScreen(),
        routes: [
          GoRoute(
            path: Routes.customerCreateSub,
            name: RouteNames.customerCreate,
            builder: (_, _) =>
                const CustomerFormScreen(mode: CustomerFormMode.create),
          ),
          GoRoute(
            path: Routes.customerDetailSub,
            name: RouteNames.customerDetail,
            builder: (_, state) => CustomerDetailScreen(
              customerId: state.pathParameters['id']!,
              initial: state.extra as Customer?,
            ),
          ),
          GoRoute(
            path: Routes.customerEditSub,
            name: RouteNames.customerEdit,
            builder: (_, state) => CustomerFormLoader(
              customerId: state.pathParameters['id']!,
              initial: state.extra as Customer?,
            ),
          ),
        ],
      ),

      // Interaction Timeline — reached from a deal/customer detail's
      // "Interactions" section (see InteractionSummaryCard).
      GoRoute(
        path: Routes.interactionTimeline,
        name: RouteNames.interactionTimeline,
        builder: (_, state) => InteractionTimelineScreen(
          linkedType: state.pathParameters['linkedType']!,
          linkedId: state.pathParameters['linkedId']!,
          linkedName: state.uri.queryParameters['name'],
        ),
      ),
      GoRoute(
        path: Routes.logInteraction,
        name: RouteNames.logInteraction,
        builder: (_, state) => LogInteractionScreen(
          linkedType: state.pathParameters['linkedType']!,
          linkedId: state.pathParameters['linkedId']!,
          linkedName: state.uri.queryParameters['name'],
          initialType: state.uri.queryParameters['type'],
        ),
      ),

      // Authenticated tabbed area.
      StatefulShellRoute.indexedStack(
        builder: (_, _, navigationShell) =>
            DashboardShell(navigationShell: navigationShell),
        branches: [
          // Tab 1 — Dashboard.
          StatefulShellBranch(
            navigatorKey: _shellHomeKey,
            routes: [
              GoRoute(
                path: Routes.dashboard,
                name: RouteNames.dashboard,
                builder: (_, _) => const DashboardScreen(),
              ),
            ],
          ),
          // Tab 2 — Leads (+ full-screen create/detail).
          StatefulShellBranch(
            navigatorKey: _shellLeadsKey,
            routes: [
              GoRoute(
                path: Routes.leads,
                name: RouteNames.leads,
                builder: (_, _) => const LeadListScreen(),
                routes: [
                  GoRoute(
                    path: Routes.leadCreateSub,
                    name: RouteNames.leadCreate,
                    parentNavigatorKey: _rootNavigatorKey,
                    builder: (_, _) => const CreateLeadScreen(),
                  ),
                  GoRoute(
                    path: Routes.leadDetailSub,
                    name: RouteNames.leadDetail,
                    parentNavigatorKey: _rootNavigatorKey,
                    builder: (_, state) =>
                        LeadDetailScreen(leadId: state.pathParameters['id']!),
                  ),
                ],
              ),
            ],
          ),
          // Tab 3 — Follow-up tasks (+ full-screen detail).
          StatefulShellBranch(
            navigatorKey: _shellTasksKey,
            routes: [
              GoRoute(
                path: Routes.tasks,
                name: RouteNames.tasks,
                builder: (_, _) => const TaskListScreen(),
                routes: [
                  GoRoute(
                    path: Routes.taskCreateSub,
                    name: RouteNames.taskCreate,
                    parentNavigatorKey: _rootNavigatorKey,
                    builder: (_, _) =>
                        const TaskFormScreen(mode: TaskFormMode.create),
                  ),
                  GoRoute(
                    path: Routes.taskDetailSub,
                    name: RouteNames.taskDetail,
                    parentNavigatorKey: _rootNavigatorKey,
                    builder: (_, state) =>
                        TaskDetailScreen(taskId: state.pathParameters['id']!),
                  ),
                  GoRoute(
                    path: Routes.taskEditSub,
                    name: RouteNames.taskEdit,
                    parentNavigatorKey: _rootNavigatorKey,
                    builder: (_, state) => TaskFormLoader(
                      mode: TaskFormMode.edit,
                      taskId: state.pathParameters['id']!,
                      initial: state.extra as Task?,
                    ),
                  ),
                  GoRoute(
                    path: Routes.taskResignSub,
                    name: RouteNames.taskResign,
                    parentNavigatorKey: _rootNavigatorKey,
                    builder: (_, state) => TaskFormLoader(
                      mode: TaskFormMode.resign,
                      taskId: state.pathParameters['id']!,
                      initial: state.extra as Task?,
                    ),
                  ),
                ],
              ),
            ],
          ),
          // Tab 4 — Quotations.
          StatefulShellBranch(
            navigatorKey: _shellQuotationsKey,
            routes: [
              GoRoute(
                path: Routes.quotations,
                name: RouteNames.quotations,
                builder: (_, _) => const QuotationListScreen(),
              ),
            ],
          ),
          // Tab 5 — Profile (+ full-screen change password).
          StatefulShellBranch(
            navigatorKey: _shellProfileKey,
            routes: [
              GoRoute(
                path: Routes.profile,
                name: RouteNames.profile,
                builder: (_, _) => const ProfileScreen(),
                routes: [
                  GoRoute(
                    path: Routes.changePasswordSub,
                    name: RouteNames.changePassword,
                    parentNavigatorKey: _rootNavigatorKey,
                    builder: (_, _) => const ChangePasswordScreen(),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    ],
  );
});

/// Session-aware guard.
///
/// - While auth is [AuthStatus.unknown] → pin to splash (no login flash).
/// - Unauthenticated + not on an auth route → send to login.
/// - Authenticated + on splash/login → send to dashboard.
String? _guard(AppSession session, GoRouterState state) {
  final loc = state.matchedLocation;
  final onSplash = loc == Routes.splash;
  final onAuthRoute =
      loc == Routes.login ||
      loc == Routes.forgotPassword ||
      loc == Routes.resetPassword;

  if (!session.isResolved) {
    return onSplash || onAuthRoute ? null : Routes.splash;
  }

  if (!session.isAuthenticated) {
    return onAuthRoute ? null : Routes.login;
  }

  // Authenticated: bounce away from splash/login/forgot.
  if (onSplash || loc == Routes.login || loc == Routes.forgotPassword) {
    return Routes.dashboard;
  }
  return null;
}
