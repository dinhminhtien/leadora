import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/screens/forgot_password_screen.dart';
import '../../features/auth/presentation/screens/login_screen.dart';
import '../../features/auth/presentation/screens/reset_password_screen.dart';
import '../../features/dashboard/presentation/screens/dashboard_screen.dart';
import '../../features/dashboard/presentation/screens/dashboard_shell.dart';
import '../../features/lead/presentation/screens/create_lead_screen.dart';
import '../../features/lead/presentation/screens/lead_detail_screen.dart';
import '../../features/lead/presentation/screens/lead_list_screen.dart';
import '../../features/notification/presentation/screens/notification_list_screen.dart';
import '../../features/profile/presentation/screens/change_password_screen.dart';
import '../../features/profile/presentation/screens/profile_screen.dart';
import '../../features/task/presentation/screens/task_detail_screen.dart';
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
final _shellNotifsKey = GlobalKey<NavigatorState>(debugLabel: 'shell-notifs');
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
                    path: Routes.taskDetailSub,
                    name: RouteNames.taskDetail,
                    parentNavigatorKey: _rootNavigatorKey,
                    builder: (_, state) =>
                        TaskDetailScreen(taskId: state.pathParameters['id']!),
                  ),
                ],
              ),
            ],
          ),
          // Tab 4 — Notifications.
          StatefulShellBranch(
            navigatorKey: _shellNotifsKey,
            routes: [
              GoRoute(
                path: Routes.notifications,
                name: RouteNames.notifications,
                builder: (_, _) => const NotificationListScreen(),
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
