// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appName => 'Leadora';

  @override
  String get commonRetry => 'Retry';

  @override
  String get commonCancel => 'Cancel';

  @override
  String get commonSave => 'Save';

  @override
  String get commonLoading => 'Loading…';

  @override
  String get commonEmpty => 'Nothing here yet';

  @override
  String get commonErrorTitle => 'Something went wrong';

  @override
  String get commonNoConnection => 'No internet connection';

  @override
  String get loginTitle => 'Welcome back';

  @override
  String get loginSubtitle => 'Sign in to your Leadora account';

  @override
  String get loginEmailLabel => 'Email';

  @override
  String get loginPasswordLabel => 'Password';

  @override
  String get loginSubmit => 'Sign in';

  @override
  String get loginForgotPassword => 'Forgot password?';

  @override
  String get loginBiometric => 'Sign in with biometrics';

  @override
  String get loginInvalidCredentials => 'Invalid email or password';

  @override
  String get bookingsTitle => 'Bookings';

  @override
  String get bookingCreate => 'New booking';

  @override
  String get bookingPendingSync => 'Pending sync';

  @override
  String get paymentTitle => 'Payment';

  @override
  String get paymentScanQr => 'Scan the QR code to pay';

  @override
  String get paymentStatusPending => 'Waiting for payment';

  @override
  String get paymentStatusProcessing => 'Processing…';

  @override
  String get paymentStatusPaid => 'Paid';

  @override
  String get paymentStatusFailed => 'Payment failed';

  @override
  String get paymentStatusExpired => 'Payment expired';

  @override
  String get paymentStatusCancelled => 'Payment cancelled';

  @override
  String get notificationsTitle => 'Notifications';

  @override
  String get notificationsMarkAllRead => 'Mark all as read';
}
