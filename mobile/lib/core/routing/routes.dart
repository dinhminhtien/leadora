/// Route paths and names. Names are used for type-safe `goNamed`/`pushNamed`
/// and for FCM deep-link resolution (see NotificationService in Phase 6).
class Routes {
  const Routes._();

  static const String splash = '/';

  static const String login = '/login';
  static const String forgotPassword = '/forgot-password';
  static const String resetPassword = '/reset-password';

  // Dashboard shell + tabs.
  static const String dashboard = '/dashboard';
  static const String leads = '/leads';
  static const String tasks = '/tasks';
  static const String notifications = '/notifications';
  static const String profile = '/profile';

  // Full-screen routes rendered over the shell (relative sub-paths).
  static const String leadCreateSub = 'new';
  static const String leadDetailSub = 'detail/:id';
  static const String taskDetailSub = 'detail/:id';
  static const String changePasswordSub = 'change-password';

  static String leadDetailPath(String id) => '/leads/detail/$id';
  static String taskDetailPath(String id) => '/tasks/detail/$id';
}

/// Route *names* (stable identifiers independent of path shape).
class RouteNames {
  const RouteNames._();

  static const String splash = 'splash';
  static const String login = 'login';
  static const String forgotPassword = 'forgotPassword';
  static const String resetPassword = 'resetPassword';

  static const String dashboard = 'dashboard';
  static const String leads = 'leads';
  static const String leadCreate = 'leadCreate';
  static const String leadDetail = 'leadDetail';
  static const String tasks = 'tasks';
  static const String taskDetail = 'taskDetail';
  static const String notifications = 'notifications';
  static const String profile = 'profile';
  static const String changePassword = 'changePassword';
}
