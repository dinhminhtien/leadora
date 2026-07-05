// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Vietnamese (`vi`).
class AppLocalizationsVi extends AppLocalizations {
  AppLocalizationsVi([String locale = 'vi']) : super(locale);

  @override
  String get appName => 'Leadora';

  @override
  String get commonRetry => 'Thử lại';

  @override
  String get commonCancel => 'Hủy';

  @override
  String get commonSave => 'Lưu';

  @override
  String get commonLoading => 'Đang tải…';

  @override
  String get commonEmpty => 'Chưa có dữ liệu';

  @override
  String get commonErrorTitle => 'Đã xảy ra lỗi';

  @override
  String get commonNoConnection => 'Không có kết nối mạng';

  @override
  String get loginTitle => 'Chào mừng trở lại';

  @override
  String get loginSubtitle => 'Đăng nhập vào tài khoản Leadora của bạn';

  @override
  String get loginEmailLabel => 'Email';

  @override
  String get loginPasswordLabel => 'Mật khẩu';

  @override
  String get loginSubmit => 'Đăng nhập';

  @override
  String get loginForgotPassword => 'Quên mật khẩu?';

  @override
  String get loginBiometric => 'Đăng nhập bằng sinh trắc học';

  @override
  String get loginInvalidCredentials => 'Email hoặc mật khẩu không đúng';

  @override
  String get bookingsTitle => 'Đặt phòng';

  @override
  String get bookingCreate => 'Đặt phòng mới';

  @override
  String get bookingPendingSync => 'Chờ đồng bộ';

  @override
  String get paymentTitle => 'Thanh toán';

  @override
  String get paymentScanQr => 'Quét mã QR để thanh toán';

  @override
  String get paymentStatusPending => 'Đang chờ thanh toán';

  @override
  String get paymentStatusProcessing => 'Đang xử lý…';

  @override
  String get paymentStatusPaid => 'Đã thanh toán';

  @override
  String get paymentStatusFailed => 'Thanh toán thất bại';

  @override
  String get paymentStatusExpired => 'Thanh toán hết hạn';

  @override
  String get paymentStatusCancelled => 'Đã hủy thanh toán';

  @override
  String get notificationsTitle => 'Thông báo';

  @override
  String get notificationsMarkAllRead => 'Đánh dấu tất cả đã đọc';

  @override
  String get forgotPasswordTitle => 'Quên mật khẩu';

  @override
  String get forgotPasswordSubtitle =>
      'Nhập email của bạn để nhận liên kết đặt lại mật khẩu';

  @override
  String get forgotPasswordEmailLabel => 'Địa chỉ Email';

  @override
  String get forgotPasswordSubmit => 'Gửi liên kết khôi phục';

  @override
  String get forgotPasswordSuccess =>
      'Gửi liên kết thành công. Vui lòng kiểm tra hộp thư điện tử của bạn.';

  @override
  String get resetPasswordTitle => 'Đặt lại mật khẩu';

  @override
  String get resetPasswordSubtitle => 'Nhập mật khẩu bảo mật mới của bạn';

  @override
  String get resetPasswordLabel => 'Mật khẩu mới';

  @override
  String get resetPasswordConfirmLabel => 'Xác nhận mật khẩu mới';

  @override
  String get resetPasswordSubmit => 'Cập nhật mật khẩu';

  @override
  String get resetPasswordSuccess =>
      'Cập nhật mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.';

  @override
  String get resetPasswordMismatch => 'Mật khẩu xác nhận không khớp';
}
