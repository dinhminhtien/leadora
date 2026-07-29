/// Centralized REST path constants.
///
/// Paths are relative to [Env.apiBaseUrl] (which already ends in `/api/v1`).
/// Grounded in the actual Spring Boot controllers in the backend module:
///   AuthController, TaskController, NotificationController, etc. Only the
/// endpoints the mobile client actually consumes are listed here.
class ApiPaths {
  const ApiPaths._();

  // --- Auth (com.novax.leadora.api.controller.AuthController) ---
  static const String login = '/auth/login';
  static const String logout = '/auth/logout';
  static const String forgotPassword = '/auth/forgot-password';
  static const String resetPassword = '/auth/reset-password';
  static const String profile = '/auth/profile';
  static const String oauthVerify = '/auth/oauth/verify';

  // --- Self-service profile (ProfileController) ---
  static const String profileMe = '/profile/me';
  static const String changePassword = '/profile/me/change-password';

  // --- Reporting / dashboard (ReportingController) ---
  static const String dashboardSummary = '/reporting/dashboard-summary';

  // --- Users (UserController) — flat assignee directory ---
  static const String users = '/users';

  // --- Leads (LeadController) ---
  static const String leads = '/leads';
  static String leadById(String id) => '/leads/$id';
  static String leadConvert(String id) => '/leads/$id/convert';
  static String leadLinkCustomer(String id) => '/leads/$id/link-customer';

  // --- Follow-up tasks (TaskController) ---
  static const String tasks = '/tasks';
  static String taskById(String id) => '/tasks/$id';
  static String taskResolve(String id) => '/tasks/$id/resolve';
  static String taskResign(String id) => '/tasks/$id/resign';

  /// NOTE: The backend currently issues a single 24h HS256 access token and
  /// exposes NO refresh endpoint. This constant marks the seam where a real
  /// `/auth/refresh` would live; see [TokenRefresher] for the fallback.
  static const String refresh = '/auth/refresh';

  // --- Notifications (NotificationController) ---
  static const String notifications = '/notifications';
  static String notificationById(String id) => '/notifications/$id';
  static String notificationRead(String id) => '/notifications/$id/read';
  static const String notificationsMarkAllRead = '/notifications/mark-all-read';

  // --- FCM Device Tokens (DeviceTokenController) ---
  static const String deviceTokens = '/device-tokens';

  // --- Customers (CustomerController) ---
  static const String customers = '/customers';
  static const String customersList = '/customers/list';
  static const String customersStats = '/customers/stats';
  static String customerById(String id) => '/customers/$id';
  static String customerHistory(String id) => '/customers/$id/history';

  // --- Interaction Timeline (InteractionTimelineController) ---
  static const String interactionTimeline = '/interaction-timeline';
  static String interactionTimelineById(String id) =>
      '/interaction-timeline/$id';
  static String interactionTimelineAuditLogs(String id) =>
      '/interaction-timeline/$id/audit-logs';

  // --- Quotations (QuotationController) ---
  static const String quotations = '/quotations';
  static String quotationById(String id) => '/quotations/$id';
  static String quotationTrackResponse(String id) =>
      '/quotations/$id/track-response';

  /// Write flows the web app has and mobile is reaching parity with. All are POST
  /// (QuotationController), all SALES/MANAGER except process-approval (MANAGER only).
  static String quotationSubmit(String id) => '/quotations/$id/submit';
  static String quotationRevise(String id) => '/quotations/$id/revise';
  static String quotationSend(String id) => '/quotations/$id/send';
  static String quotationConvert(String id) => '/quotations/$id/convert';
  static String quotationClose(String id) => '/quotations/$id/close';
  static const String quotationPendingApprovals = '/quotations/pending-approvals';
  static String quotationProcessApproval(String id) => '/quotations/$id/process-approval';

  // --- Room availability requests (RoomRequestController) ---
  // This CRM owns no room inventory: Sales asks, the Reservation team answers from the
  // hotel's real PMS. Sending a quotation and converting it to a booking are both gated
  // on a confirmed answer.
  static const String roomRequests = '/room-requests';
  static String roomRequestsByQuotation(String quotationId) =>
      '/room-requests/by-quotation/$quotationId';

  // --- Deals (DealController) ---
  static const String deals = '/deals';
  static String dealById(String id) => '/deals/$id';
  static String dealStatus(String id) => '/deals/$id/status';

  /// Where the deal stands in the Sales lifecycle: active quotation, active
  /// booking and payment state, resolved server-side.
  static String dealWorkflow(String id) => '/deals/$id/workflow';

  // --- Bookings (BookingController) ---
  static const String bookings = '/bookings';
  static String bookingById(String id) => '/bookings/$id';


  // --- Operational handover (OperationalHandoverController) ---
  // Sales/Reservation hand a confirmed booking to the Front Office. Read is open to FO
  // too; create/update is SALES/RESERVATION/MANAGER/ADMIN.
  static const String operationalHandovers = '/operational-handovers';
  static String operationalHandoverById(String id) => '/operational-handovers/$id';


  // --- Payments (PaymentController) ---
  static const String payments = '/payments';
  static String paymentById(String id) => '/payments/$id';
  static String paymentStatus(String id) => '/payments/$id/status';
  static String paymentCancel(String id) => '/payments/$id/cancel';

  // --- SLA (SlaController) ---
  static const String slaMonitoring = '/sla/monitoring';

  // --- Reminders (ReminderController) ---
  static const String reminders = '/reminders';
  static String reminderDismiss(String id) => '/reminders/$id/dismiss';
}
