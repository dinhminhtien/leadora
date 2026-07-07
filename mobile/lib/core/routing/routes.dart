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
  static const String quotations = '/quotations';
  static const String profile = '/profile';

  // Full-screen routes rendered over the shell (relative sub-paths).
  static const String leadCreateSub = 'new';
  static const String leadDetailSub = 'detail/:id';
  static const String taskDetailSub = 'detail/:id';
  static const String changePasswordSub = 'change-password';

  static String leadDetailPath(String id) => '/leads/detail/$id';
  static String taskDetailPath(String id) => '/tasks/detail/$id';

  // Full-screen routes reached only via notification deep-link (no list/tab yet).
  static const String quotationDetail = '/quotations/:id';
  static const String dealDetail = '/deals/:id';

  static String quotationDetailPath(String id) => '/quotations/$id';
  static String dealDetailPath(String id) => '/deals/$id';

  // Full-screen browse entry points, reached from the Dashboard quick actions
  // and from the header notification bell.
  static const String notifications = '/notifications';
  static const String sla = '/sla';
  static const String reminders = '/reminders';

  /// [highlightId], when set, is read back by the target list screen to
  /// flash + scroll to that row — mirrors the web `?highlight=` param used
  /// after a notification tap (see `useHighlightRow` on web).
  static String slaPath({String? highlightId}) =>
      highlightId == null || highlightId.isEmpty ? sla : '$sla?highlight=$highlightId';

  static String remindersPath({String? highlightId}) => highlightId == null || highlightId.isEmpty
      ? reminders
      : '$reminders?highlight=$highlightId';

  // Interaction Timeline — reached from a deal/customer detail's
  // "Interactions" section (no dedicated tab).
  static const String interactionTimeline = '/interactions/:linkedType/:linkedId';
  static const String logInteraction = '/interactions/:linkedType/:linkedId/log';

  static String interactionTimelinePath(String linkedType, String linkedId, {String? name}) {
    final path = '/interactions/$linkedType/$linkedId';
    final query = name != null && name.trim().isNotEmpty ? {'name': name} : null;
    return query == null ? path : '$path?${Uri(queryParameters: query).query}';
  }

  static String logInteractionPath(
    String linkedType,
    String linkedId, {
    String? name,
    String? type,
  }) {
    final path = '/interactions/$linkedType/$linkedId/log';
    final query = <String, String>{
      if (name != null && name.trim().isNotEmpty) 'name': name,
      if (type != null && type.trim().isNotEmpty) 'type': type,
    };
    return query.isEmpty ? path : '$path?${Uri(queryParameters: query).query}';
  }
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
  static const String quotationDetail = 'quotationDetail';
  static const String dealDetail = 'dealDetail';
  static const String quotations = 'quotations';
  static const String sla = 'sla';
  static const String reminders = 'reminders';
  static const String interactionTimeline = 'interactionTimeline';
  static const String logInteraction = 'logInteraction';
}
