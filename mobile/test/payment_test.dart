import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/features/booking/data/booking_models.dart';
import 'package:leadora_mobile/features/payment/data/payment_models.dart';

void main() {
  group('PaymentStatus', () {
    test('mirrors every backend enum value', () {
      expect(PaymentStatus.fromWire('PENDING'), PaymentStatus.pending);
      expect(PaymentStatus.fromWire('PAID'), PaymentStatus.paid);
      expect(PaymentStatus.fromWire('FAILED'), PaymentStatus.failed);
      expect(PaymentStatus.fromWire('CANCELLED'), PaymentStatus.cancelled);
      expect(PaymentStatus.fromWire('EXPIRED'), PaymentStatus.expired);
    });

    test('degrades to null on an unknown value', () {
      expect(PaymentStatus.fromWire('REFUNDED'), isNull);
      expect(PaymentStatus.fromWire(null), isNull);
    });

    test('only PENDING is actionable', () {
      expect(PaymentStatus.pending.isTerminal, isFalse);
      for (final s in PaymentStatus.values.where((s) => s != PaymentStatus.pending)) {
        expect(s.isTerminal, isTrue, reason: '${s.wire} should be terminal');
      }
    });
  });

  group('Payment.fromJson', () {
    test('an unknown status still renders its raw wire string', () {
      final payment = Payment.fromJson({
        'paymentId': 'p1',
        'amount': 1000,
        'status': 'REFUNDED',
      });

      expect(payment.status, isNull);
      expect(payment.displayStatus, 'REFUNDED');
    });

    test('displays a dash when status is absent entirely', () {
      final payment = Payment.fromJson({'paymentId': 'p1', 'amount': 1000});
      expect(payment.displayStatus, '—');
    });

    test('only a pending payment past its due date is overdue', () {
      final yesterday = DateTime.now().subtract(const Duration(days: 1));

      Payment build(String status) => Payment.fromJson({
        'paymentId': 'p1',
        'amount': 1000,
        'status': status,
        'dueDate': yesterday.toIso8601String(),
      });

      expect(build('PENDING').isOverdue, isTrue);
      // A paid invoice past its due date is settled, not overdue.
      expect(build('PAID').isOverdue, isFalse);
      expect(build('CANCELLED').isOverdue, isFalse);
    });
  });

  group('PaymentFilters.toQuery', () {
    test('omits null filters and always sends the sort pair', () {
      final query = const PaymentFilters().toQuery();

      expect(query.containsKey('status'), isFalse);
      expect(query.containsKey('paymentType'), isFalse);
      expect(query.containsKey('search'), isFalse);
      expect(query['sortBy'], 'createdAt');
      expect(query['sortDir'], 'desc');
    });

    test('sends wire enum values, not labels', () {
      final query = const PaymentFilters(
        search: 'BK-1',
        status: PaymentStatus.paid,
        paymentType: PaymentType.fullPayment,
        sort: PaymentSort.dueSoon,
      ).toQuery();

      expect(query['status'], 'PAID');
      expect(query['paymentType'], 'FULL_PAYMENT');
      expect(query['search'], 'BK-1');
      expect(query['sortBy'], 'dueDate');
      expect(query['sortDir'], 'asc');
    });
  });

  group('GeneratePaymentPayload.toJson', () {
    test('dueDate serializes as a plain LocalDate, not an instant', () {
      final json = GeneratePaymentPayload(
        bookingId: 'b1',
        amount: 500,
        paymentType: PaymentType.deposit,
        // An evening in UTC+7 would roll back a day if sent as a UTC instant.
        dueDate: DateTime(2026, 3, 9, 23, 30),
      ).toJson();

      expect(json['dueDate'], '2026-03-09');
      expect(json['paymentType'], 'DEPOSIT');
    });

    test('omits optional fields that were not supplied', () {
      final json = const GeneratePaymentPayload(
        bookingId: 'b1',
        amount: 500,
        paymentType: PaymentType.deposit,
      ).toJson();

      expect(json.containsKey('notes'), isFalse);
      expect(json.containsKey('paymentMethod'), isFalse);
      expect(json.containsKey('dueDate'), isFalse);
    });
  });

  group('BookingStatus', () {
    test('cancelled and checked-out block payment updates (BR-44)', () {
      expect(BookingStatus.cancelled.blocksPayment, isTrue);
      expect(BookingStatus.checkedOut.blocksPayment, isTrue);
      expect(BookingStatus.confirmed.blocksPayment, isFalse);
      expect(BookingStatus.pending.blocksPayment, isFalse);
    });

    test('degrades to null on an unknown value', () {
      expect(BookingStatus.fromWire('ARCHIVED'), isNull);
    });
  });

  group('Booking.fromJson', () {
    test('computes nights from the stay window', () {
      final booking = Booking.fromJson({
        'bookingId': 'b1',
        'checkInDate': '2026-03-09',
        'checkOutDate': '2026-03-12',
      });

      expect(booking.nights, 3);
    });

    test('nights is null when either end of the stay is missing', () {
      final booking = Booking.fromJson({
        'bookingId': 'b1',
        'checkInDate': '2026-03-09',
      });

      expect(booking.nights, isNull);
    });

    test('parses room lines, tolerating an absent details array', () {
      final withLines = Booking.fromJson({
        'bookingId': 'b1',
        'details': [
          {'bookingDetailId': 'd1', 'productName': 'Deluxe', 'lineTotal': 900},
        ],
      });
      final without = Booking.fromJson({'bookingId': 'b2'});

      expect(withLines.lines, hasLength(1));
      expect(withLines.lines.single.productName, 'Deluxe');
      expect(without.lines, isEmpty);
    });
  });
}
