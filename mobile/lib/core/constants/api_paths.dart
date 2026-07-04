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
  static const String notificationsUnreadCount = '/notifications/unread-count';
  static String notificationRead(String id) => '/notifications/$id/read';
  static const String notificationsReadAll = '/notifications/read-all';

  // --- Customers (CustomerController) ---
  static const String customers = '/customers';

  // --- Interactions (InteractionTimelineController) ---
  static const String interactions = '/interactions';
}
