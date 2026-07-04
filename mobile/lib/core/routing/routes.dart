/// Route paths and names. Names are used for type-safe `goNamed`/`pushNamed`
/// and for FCM deep-link resolution (see NotificationService in Phase 6).
class Routes {
  const Routes._();

  static const String splash = '/';

  static const String login = '/login';
  static const String forgotPassword = '/forgot-password';

  // Dashboard shell + tabs.
  static const String dashboard = '/dashboard';
  static const String bookings = '/bookings';
  static const String notifications = '/notifications';
  static const String profile = '/profile';

  // Detail routes (deep-linkable). Nested under the bookings tab branch.
  static String bookingDetailPath(String id) => '/bookings/detail/$id';
  static String paymentPath(String bookingId) =>
      '/bookings/detail/$bookingId/payment';
}

/// Route *names* (stable identifiers independent of path shape).
class RouteNames {
  const RouteNames._();

  static const String splash = 'splash';
  static const String login = 'login';
  static const String forgotPassword = 'forgotPassword';
  static const String dashboard = 'dashboard';
  static const String bookings = 'bookings';
  static const String bookingDetail = 'bookingDetail';
  static const String payment = 'payment';
  static const String notifications = 'notifications';
  static const String profile = 'profile';
}
