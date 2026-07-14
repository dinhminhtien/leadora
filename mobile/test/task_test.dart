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

  group('TaskActivityType', () {
    test('fromWire maps every backend value', () {
      expect(TaskActivityType.fromWire('CALL'), TaskActivityType.call);
      expect(TaskActivityType.fromWire('EMAIL'), TaskActivityType.email);
      expect(TaskActivityType.fromWire('MEETING'), TaskActivityType.meeting);
      expect(TaskActivityType.fromWire('SITE_VISIT'), TaskActivityType.siteVisit);
      expect(TaskActivityType.fromWire('FOLLOW_UP'), TaskActivityType.followUp);
      expect(TaskActivityType.fromWire('TASK'), TaskActivityType.task);
    });

    test('a task written before the migration still renders — no null, no throw', () {
      // The whole point of the fallback: `activity_type` is NULL on rows the
      // backfill hasn't reached, and on any backend build predating the column.
      expect(TaskActivityType.fromWire(null), TaskActivityType.task);
      expect(TaskActivityType.fromWire(''), TaskActivityType.task);
      expect(TaskActivityType.fromWire('SOMETHING_NEW'), TaskActivityType.task);
    });

    test('the type comes from the field, NOT the title', () {
      // A legacy task whose title still reads "Call: …" but which the backend
      // classified as MEETING must show MEETING. Nothing parses the title now.
      final task = Task.fromJson({
        'taskId': 't1',
        'title': 'Call: Hotel ABC',
        'status': 'OPEN',
        'priority': 'MEDIUM',
        'activityType': 'MEETING',
      });
      expect(task.activityType, TaskActivityType.meeting);
      expect(task.title, 'Call: Hotel ABC'); // title left exactly as stored
    });

    test('a title that looks like a prefix no longer changes the type', () {
      final task = Task.fromJson({
        'taskId': 't2',
        'title': 'Email: draft the contract', // would once have parsed as EMAIL
        'status': 'OPEN',
        'priority': 'MEDIUM',
        // ...but the server says otherwise, and the server is the only source.
        'activityType': 'TASK',
      });
      expect(task.activityType, TaskActivityType.task);
    });

    test('withStatus carries the activity type across', () {
      final task = Task.fromJson({
        'taskId': 't3',
        'title': 'Ring the venue',
        'status': 'OPEN',
        'priority': 'HIGH',
        'activityType': 'CALL',
      });
      expect(task.withStatus(TaskStatus.completed).activityType,
          TaskActivityType.call);
    });
  });

  group('CreateTaskPayload', () {
    test('sends required fields and drops blank optionals', () {
      final json = const CreateTaskPayload(
        title: '  Follow up  ',
        assignedUserId: 'user-1',
        priority: TaskPriority.high,
        activityType: TaskActivityType.meeting,
        description: '   ',
      ).toJson();

      expect(json['title'], 'Follow up'); // trimmed
      expect(json['assignedUserId'], 'user-1');
      expect(json['priority'], 'HIGH'); // wire value, not ordinal
      expect(json['activityType'], 'MEETING'); // required — never inferred
      expect(json.containsKey('description'), isFalse); // blank dropped
      expect(json.containsKey('startAt'), isFalse);
    });

    test('serializes dates as UTC ISO-8601 (OffsetDateTime-compatible)', () {
      final json = CreateTaskPayload(
        title: 'x',
        assignedUserId: 'u',
        priority: TaskPriority.low,
        activityType: TaskActivityType.call,
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
