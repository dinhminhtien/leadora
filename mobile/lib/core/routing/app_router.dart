import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../widgets/placeholder_screen.dart';
import '../widgets/splash_screen.dart';
import '../../features/auth/presentation/screens/login_screen.dart';
import '../../features/dashboard/presentation/screens/dashboard_shell.dart';
import 'app_session.dart';
import 'routes.dart';

/// Navigator keys — a root key plus one per shell branch so nested navigation
/// is isolated per tab.
final _rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');
final _shellHomeKey = GlobalKey<NavigatorState>(debugLabel: 'shell-home');
final _shellBookingsKey = GlobalKey<NavigatorState>(debugLabel: 'shell-bookings');
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
        builder: (_, _) =>
            const PlaceholderScreen(title: 'Forgot password'),
      ),

      // Authenticated tabbed area.
      StatefulShellRoute.indexedStack(
        builder: (_, _, navigationShell) =>
            DashboardShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            navigatorKey: _shellHomeKey,
            routes: [
              GoRoute(
                path: Routes.dashboard,
                name: RouteNames.dashboard,
                builder: (_, _) => const PlaceholderScreen(
                  title: 'Dashboard',
                  icon: Icons.dashboard_rounded,
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            navigatorKey: _shellBookingsKey,
            routes: [
              GoRoute(
                path: Routes.bookings,
                name: RouteNames.bookings,
                builder: (_, _) => const PlaceholderScreen(
                  title: 'Bookings',
                  icon: Icons.event_note_rounded,
                ),
                routes: [
                  GoRoute(
                    path: 'detail/:id', // /bookings/detail/:id
                    name: RouteNames.bookingDetail,
                    builder: (_, state) => PlaceholderScreen(
                      title: 'Booking ${state.pathParameters['id']}',
                      icon: Icons.receipt_long_rounded,
                    ),
                    routes: [
                      GoRoute(
                        path: 'payment', // /bookings/detail/:id/payment
                        name: RouteNames.payment,
                        parentNavigatorKey: _rootNavigatorKey, // full-screen
                        builder: (_, state) => PlaceholderScreen(
                          title: 'Payment',
                          icon: Icons.qr_code_2_rounded,
                          subtitle:
                              'Booking ${state.pathParameters['id']} payment',
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            navigatorKey: _shellNotifsKey,
            routes: [
              GoRoute(
                path: Routes.notifications,
                name: RouteNames.notifications,
                builder: (_, _) => const PlaceholderScreen(
                  title: 'Notifications',
                  icon: Icons.notifications_rounded,
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            navigatorKey: _shellProfileKey,
            routes: [
              GoRoute(
                path: Routes.profile,
                name: RouteNames.profile,
                builder: (_, _) => const PlaceholderScreen(
                  title: 'Profile',
                  icon: Icons.person_rounded,
                ),
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
  final onAuthRoute = loc == Routes.login || loc == Routes.forgotPassword;

  if (!session.isResolved) {
    return onSplash ? null : Routes.splash;
  }

  if (!session.isAuthenticated) {
    return onAuthRoute ? null : Routes.login;
  }

  // Authenticated: bounce away from splash/auth routes.
  if (onSplash || onAuthRoute) return Routes.dashboard;
  return null;
}
