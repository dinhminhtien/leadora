/// Route paths and names. Names are used for type-safe `goNamed`/`pushNamed`
/// and for FCM deep-link resolution (see NotificationService in Phase 6).
class Routes {
  const Routes._();

  static const String splash = '/';

  static const String login = '/login';
  static const String forgotPassword = '/forgot-password';
  static const String resetPassword = '/reset-password';

  // Dashboard shell + tabs: Dashboard · Deals · Leads · Tasks · More.
  static const String dashboard = '/dashboard';
  static const String deals = '/deals';
  static const String leads = '/leads';
  static const String tasks = '/tasks';
  static const String more = '/more';

  // Secondary modules — reached from the More hub, rendered over the shell.
  static const String quotations = '/quotations';
  static const String profile = '/profile';

  // Full-screen routes rendered over the shell (relative sub-paths).
  static const String dealCreateSub = 'new';
  static const String dealDetailSub = 'detail/:id';
  static const String leadCreateSub = 'new';
  static const String leadDetailSub = 'detail/:id';
  static const String taskDetailSub = 'detail/:id';
  static const String taskCreateSub = 'new';
  static const String taskEditSub = 'edit/:id';
  static const String taskResignSub = 'resign/:id';
  static const String changePasswordSub = 'change-password';
  static const String profileEditSub = 'edit';

  static String leadDetailPath(String id) => '/leads/detail/$id';
  static String taskDetailPath(String id) => '/tasks/detail/$id';
  static const String taskCreate = '/tasks/new';
  static String taskEditPath(String id) => '/tasks/edit/$id';
  static String taskResignPath(String id) => '/tasks/resign/$id';

  // Customer profiles — full-screen browse reached from Dashboard quick actions.
  static const String customers = '/customers';
  static const String customerCreateSub = 'new';
  static const String customerDetailSub = 'detail/:id';
  static const String customerEditSub = 'edit/:id';
  static String customerDetailPath(String id) => '/customers/detail/$id';
  static String customerEditPath(String id) => '/customers/edit/$id';

  // Full-screen routes reached only via notification deep-link (no list/tab yet).
  static const String quotationDetail = '/quotations/:id';

  static String quotationDetailPath(String id) => '/quotations/$id';

  /// `detail/` segment (rather than `/deals/:id`) keeps the create route from
  /// being swallowed as a deal whose id is literally "new".
  static String dealDetailPath(String id) => '/deals/detail/$id';
  static const String dealCreate = '/deals/new';

  // Payments & bookings — reached from the More hub.
  static const String payments = '/payments';
  static const String paymentCreateSub = 'new';
  static const String paymentDetailSub = 'detail/:id';
  static const String paymentCreate = '/payments/new';
  static String paymentDetailPath(String id) => '/payments/detail/$id';

  static const String bookings = '/bookings';
  static const String bookingDetailSub = 'detail/:id';
  static String bookingDetailPath(String id) => '/bookings/detail/$id';

  // Sales pipeline (Kanban) — reached from the More hub.
  static const String pipeline = '/pipeline';

  // Full-screen browse entry points, reached from the Dashboard quick actions
  // and from the header notification bell.
  static const String notifications = '/notifications';
  static const String sla = '/sla';
  static const String reminders = '/reminders';

  /// [highlightId], when set, is read back by the target list screen to
  /// flash + scroll to that row — mirrors the web `?highlight=` param used
  /// after a notification tap (see `useHighlightRow` on web).
  static String slaPath({String? highlightId}) =>
      highlightId == null || highlightId.isEmpty
      ? sla
      : '$sla?highlight=$highlightId';

  static String remindersPath({String? highlightId}) =>
      highlightId == null || highlightId.isEmpty
      ? reminders
      : '$reminders?highlight=$highlightId';

  // Interaction Timeline — reached from a deal/customer detail's
  // "Interactions" section (no dedicated tab).
  static const String interactionTimeline =
      '/interactions/:linkedType/:linkedId';
  static const String logInteraction =
      '/interactions/:linkedType/:linkedId/log';

  // Single-interaction detail + edit. Singular `/interaction/...` so it never
  // collides with the `/interactions/:linkedType/:linkedId` timeline pattern.
  static const String interactionDetail = '/interaction/:id';
  static const String editInteraction = '/interaction/:id/edit';

  static String interactionDetailPath(String id) => '/interaction/$id';
  static String editInteractionPath(String id) => '/interaction/$id/edit';

  static String interactionTimelinePath(
    String linkedType,
    String linkedId, {
    String? name,
  }) {
    final path = '/interactions/$linkedType/$linkedId';
    final query = name != null && name.trim().isNotEmpty
        ? {'name': name}
        : null;
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
  static const String more = 'more';
  static const String deals = 'deals';
  static const String dealCreate = 'dealCreate';
  static const String pipeline = 'pipeline';
  static const String payments = 'payments';
  static const String paymentCreate = 'paymentCreate';
  static const String paymentDetail = 'paymentDetail';
  static const String bookings = 'bookings';
  static const String bookingDetail = 'bookingDetail';
  static const String leads = 'leads';
  static const String leadCreate = 'leadCreate';
  static const String leadDetail = 'leadDetail';
  static const String tasks = 'tasks';
  static const String taskDetail = 'taskDetail';
  static const String taskCreate = 'taskCreate';
  static const String taskEdit = 'taskEdit';
  static const String taskResign = 'taskResign';
  static const String customers = 'customers';
  static const String customerCreate = 'customerCreate';
  static const String customerDetail = 'customerDetail';
  static const String customerEdit = 'customerEdit';
  static const String notifications = 'notifications';
  static const String profile = 'profile';
  static const String profileEdit = 'profileEdit';
  static const String changePassword = 'changePassword';
  static const String quotationDetail = 'quotationDetail';
  static const String dealDetail = 'dealDetail';
  static const String quotations = 'quotations';
  static const String sla = 'sla';
  static const String reminders = 'reminders';
  static const String interactionTimeline = 'interactionTimeline';
  static const String logInteraction = 'logInteraction';
  static const String interactionDetail = 'interactionDetail';
  static const String editInteraction = 'editInteraction';
}
