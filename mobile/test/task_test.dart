import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/features/task/data/task_models.dart';

void main() {
  group('TaskStatus / TaskPriority', () {
    test('fromWire maps every backend value', () {
      expect(TaskStatus.fromWire('OPEN'), TaskStatus.open);
      expect(TaskStatus.fromWire('COMPLETED'), TaskStatus.completed);
      expect(TaskStatus.fromWire('CANCELLED'), TaskStatus.cancelled);
      expect(TaskPriority.fromWire('LOW'), TaskPriority.low);
      expect(TaskPriority.fromWire('MEDIUM'), TaskPriority.medium);
      expect(TaskPriority.fromWire('HIGH'), TaskPriority.high);
    });

    test('fromWire falls back sensibly on unknown/null', () {
      expect(TaskStatus.fromWire(null), TaskStatus.open);
      expect(TaskPriority.fromWire('CRITICAL'), TaskPriority.medium);
    });
  });

  group('CreateTaskPayload', () {
    test('sends required fields and drops blank optionals', () {
      final json = const CreateTaskPayload(
        title: '  Follow up  ',
        assignedUserId: 'user-1',
        priority: TaskPriority.high,
        description: '   ',
      ).toJson();

      expect(json['title'], 'Follow up'); // trimmed
      expect(json['assignedUserId'], 'user-1');
      expect(json['priority'], 'HIGH'); // wire value, not ordinal
      expect(json.containsKey('description'), isFalse); // blank dropped
      expect(json.containsKey('startAt'), isFalse);
    });

    test('serializes dates as UTC ISO-8601 (OffsetDateTime-compatible)', () {
      final json = CreateTaskPayload(
        title: 'x',
        assignedUserId: 'u',
        priority: TaskPriority.low,
        endAt: DateTime.utc(2026, 7, 9, 3, 0, 0),
        customerId: 'cust-1',
      ).toJson();

      expect(json['endAt'], '2026-07-09T03:00:00.000Z');
      expect(json['customerId'], 'cust-1');
    });
  });

  group('UpdateTaskPayload', () {
    test('only emits provided fields (partial edit safe)', () {
      final json = const UpdateTaskPayload(
        status: TaskStatus.completed,
      ).toJson();
      expect(json, {'status': 'COMPLETED'});
    });
  });

  group('ResignTaskPayload', () {
    test('omits everything left null so the parent is copied server-side', () {
      expect(const ResignTaskPayload().toJson(), isEmpty);
      final json = const ResignTaskPayload(
        priority: TaskPriority.high,
        resignNote: 'handing over',
      ).toJson();
      expect(json, {'priority': 'HIGH', 'resignNote': 'handing over'});
    });
  });

  group('Task.fromJson', () {
    test('parses assignedUserId and computed isOverdue', () {
      final task = Task.fromJson({
        'taskId': 't1',
        'title': 'Call back',
        'status': 'OPEN',
        'priority': 'MEDIUM',
        'assignedUserId': 'u9',
        'assignedUserName': 'Ann',
        'customerName': 'Acme',
        'isOverdue': true,
      });
      expect(task.assignedUserId, 'u9');
      expect(task.isOverdue, isTrue);
      expect(task.relatedName, 'Acme');
    });

    test('parses web-parity fields (createdBy, contact info, updatedAt)', () {
      final task = Task.fromJson({
        'taskId': 't2',
        'title': 'Send quotation',
        'status': 'OPEN',
        'priority': 'HIGH',
        'createdById': 'u1',
        'createdByName': 'Manager',
        'leadId': 'l1',
        'leadName': 'Big Corp',
        'leadPhone': '0901234567',
        'leadEmail': 'lead@corp.vn',
        'leadCompanyName': 'Corp Ltd',
        'updatedAt': '2026-07-08T10:00:00Z',
      });
      expect(task.createdByName, 'Manager');
      expect(task.updatedAt, isNotNull);
      // Lead wins the contact accessors when leadId is present.
      expect(task.contactName, 'Big Corp');
      expect(task.contactPhone, '0901234567');
      expect(task.contactEmail, 'lead@corp.vn');
      expect(task.contactCompany, 'Corp Ltd');
    });

    test('contact accessors fall back to customer when no lead is linked', () {
      final task = Task.fromJson({
        'taskId': 't3',
        'title': 'Check in',
        'status': 'OPEN',
        'priority': 'LOW',
        'customerId': 'c1',
        'customerName': 'Ms Hoa',
        'customerPhone': '0907654321',
      });
      expect(task.contactName, 'Ms Hoa');
      expect(task.contactPhone, '0907654321');
      expect(task.contactEmail, isNull);
    });

    test('anchorDate prefers startAt, falls back to endAt (calendar key)', () {
      final both = Task.fromJson({
        'taskId': 't4',
        'title': 'x',
        'status': 'OPEN',
        'priority': 'LOW',
        'startAt': '2026-07-09T02:00:00Z',
        'endAt': '2026-07-09T03:00:00Z',
      });
      expect(both.anchorDate, DateTime.parse('2026-07-09T02:00:00Z'));

      final onlyDue = Task.fromJson({
        'taskId': 't5',
        'title': 'x',
        'status': 'OPEN',
        'priority': 'LOW',
        'endAt': '2026-07-10T03:00:00Z',
      });
      expect(onlyDue.anchorDate, DateTime.parse('2026-07-10T03:00:00Z'));
    });
  });
}
