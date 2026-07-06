import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/features/lead/data/lead_models.dart';

void main() {
  group('LeadStatus', () {
    test('fromWire maps every backend value', () {
      expect(LeadStatus.fromWire('NEW'), LeadStatus.neww);
      expect(LeadStatus.fromWire('CONTACTED'), LeadStatus.contacted);
      expect(LeadStatus.fromWire('QUALIFIED'), LeadStatus.qualified);
      expect(LeadStatus.fromWire('CONVERTED'), LeadStatus.converted);
      expect(LeadStatus.fromWire('LOST'), LeadStatus.lost);
    });

    test('fromWire falls back to NEW on unknown/null', () {
      expect(LeadStatus.fromWire('SOMETHING_ELSE'), LeadStatus.neww);
      expect(LeadStatus.fromWire(null), LeadStatus.neww);
    });

    test('allowedTransitions mirror the backend one-step-forward rule', () {
      expect(LeadStatus.neww.allowedTransitions,
          [LeadStatus.contacted, LeadStatus.lost]);
      expect(LeadStatus.contacted.allowedTransitions,
          [LeadStatus.qualified, LeadStatus.lost]);
      // QUALIFIED can only be lost via status update — CONVERTED goes through
      // the conversion flow, never a plain status change.
      expect(LeadStatus.qualified.allowedTransitions, [LeadStatus.lost]);
      expect(LeadStatus.converted.allowedTransitions, isEmpty);
      expect(LeadStatus.lost.allowedTransitions, isEmpty);
    });

    test('terminal statuses are CONVERTED and LOST', () {
      expect(LeadStatus.converted.isTerminal, isTrue);
      expect(LeadStatus.lost.isTerminal, isTrue);
      expect(LeadStatus.neww.isTerminal, isFalse);
      expect(LeadStatus.contacted.isTerminal, isFalse);
      expect(LeadStatus.qualified.isTerminal, isFalse);
    });
  });

  group('CreateLeadPayload', () {
    test('omits null and blank optional fields', () {
      const payload = CreateLeadPayload(
        fullName: 'Nguyen Van A',
        email: '',
        phone: '  ',
        companyName: null,
        isCorporate: false,
      );
      expect(payload.toJson(), {
        'fullName': 'Nguyen Van A',
        'isCorporate': false,
      });
    });

    test('keeps provided values', () {
      const payload = CreateLeadPayload(
        fullName: 'ACME',
        email: 'a@b.com',
        isCorporate: true,
        companyName: 'ACME Corp',
      );
      expect(payload.toJson(), {
        'fullName': 'ACME',
        'email': 'a@b.com',
        'isCorporate': true,
        'companyName': 'ACME Corp',
      });
    });
  });

  group('LeadFilters', () {
    test('defaults produce no query params', () {
      expect(const LeadFilters().toQuery(), isEmpty);
      expect(const LeadFilters().activeAdvancedCount, 0);
    });

    test('toQuery serializes every param the backend accepts', () {
      final filters = LeadFilters(
        search: ' minh ',
        status: LeadStatus.contacted,
        source: 'Referral',
        isCorporate: true,
        dateFrom: DateTime(2026, 7, 1),
        dateTo: DateTime(2026, 7, 6),
        sort: LeadSort.nameAz,
        scope: LeadScope.created,
      );
      expect(filters.toQuery(), {
        'search': 'minh',
        'status': 'CONTACTED',
        'source': 'Referral',
        'isCorporate': true,
        'dateFrom': DateTime(2026, 7, 1).toUtc().toIso8601String(),
        'dateTo':
            DateTime(2026, 7, 6, 23, 59, 59, 999).toUtc().toIso8601String(),
        'sortBy': 'fullName',
        'sortDir': 'asc',
        'scope': 'created',
      });
    });

    test('date bounds are UTC instants covering the LOCAL calendar day', () {
      final from = LeadFilters.utcStartOfLocalDay(DateTime(2026, 7, 6, 15, 30));
      final to = LeadFilters.utcEndOfLocalDay(DateTime(2026, 7, 6, 15, 30));

      // Z-suffixed ISO instants — no '+' that a query string would mangle.
      expect(from, endsWith('Z'));
      expect(to, endsWith('Z'));

      // Round-tripping gives back exactly the local day's boundaries, so a
      // lead created 00:30 local time is inside the window even when that
      // moment falls on the previous UTC calendar day.
      expect(DateTime.parse(from).toLocal(), DateTime(2026, 7, 6));
      expect(DateTime.parse(to).toLocal(),
          DateTime(2026, 7, 6, 23, 59, 59, 999));
    });

    test('activeAdvancedCount ignores search and status (inline filters)', () {
      const inlineOnly =
          LeadFilters(search: 'x', status: LeadStatus.qualified);
      expect(inlineOnly.activeAdvancedCount, 0);

      final advanced = inlineOnly.copyWith(
        source: 'Web',
        isCorporate: false,
        dateFrom: DateTime(2026, 1, 1),
        sort: LeadSort.statusPriority,
        scope: LeadScope.created,
      );
      expect(advanced.activeAdvancedCount, 5);
    });

    test('copyWith clears a field when null is passed explicitly', () {
      const filters = LeadFilters(search: 'a', status: LeadStatus.lost);
      final cleared = filters.copyWith(status: null);
      expect(cleared.status, isNull);
      expect(cleared.search, 'a'); // untouched fields survive
    });

    test('resetAdvanced keeps search and status only', () {
      final filters = LeadFilters(
        search: 'q',
        status: LeadStatus.neww,
        source: 'Web',
        isCorporate: true,
        dateFrom: DateTime(2026, 1, 1),
        sort: LeadSort.oldestFirst,
        scope: LeadScope.created,
      );
      final reset = filters.resetAdvanced();
      expect(reset.search, 'q');
      expect(reset.status, LeadStatus.neww);
      expect(reset.activeAdvancedCount, 0);
      expect(reset.toQuery(), {'search': 'q', 'status': 'NEW'});
    });
  });

  group('Lead.fromJson', () {
    test('parses a full backend LeadResponse', () {
      final lead = Lead.fromJson({
        'leadId': 'id-1',
        'fullName': 'Tran Nhat Minh',
        'status': 'QUALIFIED',
        'email': 'm@x.com',
        'phone': '0900000000',
        'companyName': 'Novax',
        'isCorporate': true,
        'source': 'Referral',
        'createdAt': '2026-07-01T10:00:00Z',
      });
      expect(lead.leadId, 'id-1');
      expect(lead.status, LeadStatus.qualified);
      expect(lead.isCorporate, isTrue);
      expect(lead.createdAt, DateTime.parse('2026-07-01T10:00:00Z'));
    });

    test('tolerates missing optionals', () {
      final lead = Lead.fromJson({'leadId': 'id-2', 'fullName': 'A'});
      expect(lead.status, LeadStatus.neww);
      expect(lead.isCorporate, isFalse);
      expect(lead.email, isNull);
      expect(lead.createdAt, isNull);
    });
  });
}
