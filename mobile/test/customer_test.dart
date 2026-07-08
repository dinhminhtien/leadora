import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/features/customer/data/customer_models.dart';

void main() {
  group('Customer enums', () {
    test('fromWire maps every backend value', () {
      expect(CustomerType.fromWire('INDIVIDUAL'), CustomerType.individual);
      expect(CustomerType.fromWire('CORPORATE'), CustomerType.corporate);
      expect(CustomerStatus.fromWire('ACTIVE'), CustomerStatus.active);
      expect(CustomerStatus.fromWire('INACTIVE'), CustomerStatus.inactive);
    });

    test('fromWire falls back sensibly on unknown/null', () {
      expect(CustomerType.fromWire(null), CustomerType.individual);
      expect(CustomerStatus.fromWire('???'), CustomerStatus.active);
    });
  });

  group('CreateCustomerPayload', () {
    test('always sends type + trimmed name, drops blank optionals', () {
      final json = const CreateCustomerPayload(
        fullName: '  Acme Corp  ',
        customerType: CustomerType.corporate,
        email: '',
        phone: '0909',
      ).toJson();

      expect(json['fullName'], 'Acme Corp');
      expect(json['customerType'], 'CORPORATE');
      expect(json['phone'], '0909');
      expect(json.containsKey('email'), isFalse);
    });
  });

  group('UpdateCustomerPayload', () {
    test('always includes fullName (backend @NotBlank) plus set fields', () {
      final json = const UpdateCustomerPayload(
        fullName: 'Jane',
        status: CustomerStatus.inactive,
      ).toJson();

      expect(json['fullName'], 'Jane');
      expect(json['status'], 'INACTIVE');
      expect(json.containsKey('email'), isFalse);
    });
  });

  group('Customer.fromJson', () {
    test('parses a full backend CustomerResponse', () {
      final c = Customer.fromJson({
        'customerId': 'c1',
        'customerType': 'CORPORATE',
        'fullName': 'Acme',
        'status': 'ACTIVE',
        'email': 'a@acme.com',
        'assignedUserName': 'Ann',
        'createdAt': '2026-07-01T10:00:00Z',
      });
      expect(c.isCorporate, isTrue);
      expect(c.status, CustomerStatus.active);
      expect(c.assignedUserName, 'Ann');
      expect(c.createdAt, isNotNull);
    });
  });

  group('CustomerHistoryItem.fromJson', () {
    test('maps type and amount', () {
      final h = CustomerHistoryItem.fromJson({
        'type': 'BOOKING',
        'id': 'b1',
        'title': 'Deluxe suite',
        'amount': 1500000,
      });
      expect(h.type, CustomerHistoryType.booking);
      expect(h.amount, 1500000);
    });
  });
}
