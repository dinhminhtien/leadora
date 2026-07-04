/// Centralized REST path constants.
///
/// Paths are relative to [Env.apiBaseUrl] (which already ends in `/api/v1`).
/// Grounded in the actual Spring Boot controllers in the backend module:
///   AuthController, BookingController, NotificationController, etc.
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

  // --- Leads (LeadController) ---
  static const String leads = '/leads';
  static String leadById(String id) => '/leads/$id';

  // --- Follow-up tasks (TaskController) ---
  static const String tasks = '/tasks';
  static String taskById(String id) => '/tasks/$id';
  static String taskResolve(String id) => '/tasks/$id/resolve';

  /// NOTE: The backend currently issues a single 24h HS256 access token and
  /// exposes NO refresh endpoint. This constant marks the seam where a real
  /// `/auth/refresh` would live; see [TokenRefresher] for the fallback.
  static const String refresh = '/auth/refresh';

  // --- Booking (BookingController) ---
  static const String bookings = '/bookings';
  static String bookingById(String id) => '/bookings/$id';
  static String bookingStatus(String id) => '/bookings/$id/status';

  // --- Payment (lives under bookings/quotations on this backend) ---
  static String paymentByBooking(String bookingId) =>
      '/bookings/$bookingId/payment';
  static String paymentStatus(String bookingId) =>
      '/bookings/$bookingId/payment/status';

  // --- Notifications (NotificationController) ---
  static const String notifications = '/notifications';
  static String notificationById(String id) => '/notifications/$id';
  static String notificationRead(String id) => '/notifications/$id/read';
  static const String notificationsMarkAllRead = '/notifications/mark-all-read';

  // --- Customers (CustomerController) ---
  static const String customers = '/customers';

  // --- Interactions (InteractionTimelineController) ---
  static const String interactions = '/interactions';
}
