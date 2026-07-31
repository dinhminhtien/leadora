import 'dart:async';

import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/core/network/pagination_response.dart';
import 'package:leadora_mobile/features/lead/data/lead_models.dart';
import 'package:leadora_mobile/features/lead/data/lead_repository.dart';
import 'package:leadora_mobile/features/lead/presentation/providers/lead_providers.dart';

/// A repository whose every `getLeads` is resolved by hand, so a test can decide
/// which of two in-flight requests finishes first. That ordering is the whole
/// subject of the controller group below.
class _ScriptedLeadRepository implements LeadRepository {
  final List<Completer<PaginationResponse<Lead>>> pending = [];
  final List<({LeadFilters filters, int page})> calls = [];

  @override
  Future<PaginationResponse<Lead>> getLeads({
    LeadFilters filters = const LeadFilters(),
    int page = 0,
    int size = 15,
    CancelToken? cancelToken,
  }) {
    calls.add((filters: filters, page: page));
    final completer = Completer<PaginationResponse<Lead>>();
    pending.add(completer);
    return completer.future;
  }

  /// Completes the [index]th outstanding request with one lead per name.
  void complete(int index, List<String> names, {bool hasMore = false}) {
    pending[index].complete(_page(names, hasMore: hasMore));
  }

  void fail(int index, Object error) => pending[index].completeError(error);

  static PaginationResponse<Lead> _page(
    List<String> names, {
    bool hasMore = false,
  }) {
    return PaginationResponse<Lead>(
      items: [
        for (final name in names)
          Lead(leadId: name, fullName: name, status: LeadStatus.neww),
      ],
      page: 0,
      size: 15,
      totalElements: names.length,
      totalPages: hasMore ? 2 : 1,
      isFirst: true,
      isLast: !hasMore,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName} is not scripted');
}

/// Container wired to [repo], with the first (automatic) page-0 load already
/// issued. Returns the container and the controller.
({ProviderContainer container, LeadListController controller}) _harness(
  _ScriptedLeadRepository repo,
) {
  final container = ProviderContainer(
    overrides: [leadRepositoryProvider.overrideWithValue(repo)],
  );
  addTearDown(container.dispose);
  // Keep the autoDispose provider alive for the duration of the test.
  container.listen(leadListControllerProvider, (_, _) {}, fireImmediately: true);
  return (
    container: container,
    controller: container.read(leadListControllerProvider.notifier),
  );
}

List<String> _names(ProviderContainer container) =>
    container
        .read(leadListControllerProvider)
        .valueOrNull
        ?.items
        .map((l) => l.fullName)
        .toList() ??
    const [];

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
      expect(LeadStatus.neww.allowedTransitions, [
        LeadStatus.contacted,
        LeadStatus.lost,
      ]);
      expect(LeadStatus.contacted.allowedTransitions, [
        LeadStatus.qualified,
        LeadStatus.lost,
      ]);
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
        'dateTo': DateTime(
          2026,
          7,
          6,
          23,
          59,
          59,
          999,
        ).toUtc().toIso8601String(),
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
      expect(
        DateTime.parse(to).toLocal(),
        DateTime(2026, 7, 6, 23, 59, 59, 999),
      );
    });

    test('activeAdvancedCount ignores the inline filters', () {
      // Search, status and scope all have their own visible control on the
      // list screen; counting them in the sheet's badge would advertise a
      // hidden filter that is in fact on screen.
      const inlineOnly = LeadFilters(search: 'x', status: LeadStatus.qualified);
      expect(inlineOnly.activeAdvancedCount, 0);
      expect(
        inlineOnly.copyWith(scope: LeadScope.created).activeAdvancedCount,
        0,
      );

      final advanced = inlineOnly.copyWith(
        source: 'Web',
        isCorporate: false,
        dateFrom: DateTime(2026, 1, 1),
        sort: LeadSort.statusPriority,
        scope: LeadScope.created,
      );
      expect(advanced.activeAdvancedCount, 4);
    });

    test('copyWith clears a field when null is passed explicitly', () {
      const filters = LeadFilters(search: 'a', status: LeadStatus.lost);
      final cleared = filters.copyWith(status: null);
      expect(cleared.status, isNull);
      expect(cleared.search, 'a'); // untouched fields survive
    });

    test('resetAdvanced keeps the inline filters, scope included', () {
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
      // Clearing a date range must not also move the user out of the list they
      // are looking at.
      expect(reset.scope, LeadScope.created);
      expect(reset.activeAdvancedCount, 0);
      expect(reset.toQuery(), {
        'search': 'q',
        'status': 'NEW',
        'scope': 'created',
      });
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

  group('LeadFieldRules', () {
    test('full name is required and capped at the column width', () {
      expect(LeadFieldRules.validateFullName(''), isNotNull);
      expect(LeadFieldRules.validateFullName('   '), isNotNull);
      expect(LeadFieldRules.validateFullName('Tran Nhat Minh'), isNull);
      expect(LeadFieldRules.validateFullName('a' * 40), isNull);
      expect(LeadFieldRules.validateFullName('a' * 41), contains('40'));
    });

    test('email is optional but must parse, and fits the column', () {
      expect(LeadFieldRules.validateEmail(null), isNull);
      expect(LeadFieldRules.validateEmail(''), isNull);
      expect(LeadFieldRules.validateEmail('m@x.com'), isNull);
      expect(LeadFieldRules.validateEmail('not-an-email'), 'Invalid email format');
      // 41 characters, still a well-formed address.
      expect(LeadFieldRules.validateEmail('${'a' * 31}@example.com'), contains('40'));
    });

    test('phone accepts the shapes people actually type', () {
      // The pattern the backend enforces: 10 or 11 digits.
      expect(LeadFieldRules.validatePhone('0912345678'), isNull);
      expect(LeadFieldRules.validatePhone('09123456789'), isNull); // 11 digits
      // …and the same number written the way a human writes it.
      expect(LeadFieldRules.validatePhone('0912 345 678'), isNull);
      expect(LeadFieldRules.validatePhone('091-234-5678'), isNull);
      expect(LeadFieldRules.validatePhone('+84912345678'), isNull);
      expect(LeadFieldRules.normalizePhone('+84 912 345 678'), '0912345678');
      // A landline and a non-mobile prefix are numbers too — they used to be
      // refused for not being Vietnamese *mobiles*, which is not the same thing.
      expect(LeadFieldRules.validatePhone('0281234567'), isNull);
      expect(LeadFieldRules.validatePhone('0412345678'), isNull);
      // Wrong lengths are still refused.
      expect(LeadFieldRules.validatePhone('091234567'), isNotNull); // 9 digits
      expect(LeadFieldRules.validatePhone('091234567890'), isNotNull); // 12
      expect(LeadFieldRules.validatePhone('09123abc78'), isNotNull); // not digits
      // Blank means "no phone", which is legal on its own (see reachability).
      expect(LeadFieldRules.validatePhone('  '), isNull);
    });

    test('an organization lead must name its company', () {
      expect(LeadFieldRules.validateCompanyName('', isCorporate: false), isNull);
      expect(
        LeadFieldRules.validateCompanyName('', isCorporate: true),
        contains('required'),
      );
      expect(
        LeadFieldRules.validateCompanyName('Novax', isCorporate: true),
        isNull,
      );
      expect(
        LeadFieldRules.validateCompanyName('a' * 41, isCorporate: true),
        contains('40'),
      );
    });

    test('a lead needs a phone or an email — either one is enough', () {
      expect(LeadFieldRules.validateReachable(), isNotNull);
      expect(LeadFieldRules.validateReachable(email: '', phone: '  '), isNotNull);
      expect(LeadFieldRules.validateReachable(phone: '0912345678'), isNull);
      expect(LeadFieldRules.validateReachable(email: 'm@x.com'), isNull);
    });

    test('the long free-text fields match their columns', () {
      expect(LeadFieldRules.validateSource('a' * 40), isNull);
      expect(LeadFieldRules.validateSource('a' * 41), contains('40'));
      expect(LeadFieldRules.validateInterestedService('a' * 100), isNull);
      expect(LeadFieldRules.validateInterestedService('a' * 101), contains('100'));
    });
  });

  group('LeadFilters equality', () {
    test('same selection compares equal, so a re-tap is not a change', () {
      const a = LeadFilters(search: 'minh', status: LeadStatus.contacted);
      const b = LeadFilters(search: 'minh', status: LeadStatus.contacted);
      expect(a, b);
      expect(a.hashCode, b.hashCode);
    });

    test('every field participates', () {
      const base = LeadFilters();
      expect(base == base.copyWith(search: 'x'), isFalse);
      expect(base == base.copyWith(status: LeadStatus.lost), isFalse);
      expect(base == base.copyWith(source: 'Web'), isFalse);
      expect(base == base.copyWith(isCorporate: true), isFalse);
      expect(base == base.copyWith(dateFrom: DateTime(2026)), isFalse);
      expect(base == base.copyWith(dateTo: DateTime(2026)), isFalse);
      expect(base == base.copyWith(sort: LeadSort.nameAz), isFalse);
      expect(base == base.copyWith(scope: LeadScope.created), isFalse);
    });
  });

  group('LeadListController', () {
    test('the newest search wins, even when an older one answers later', () async {
      final repo = _ScriptedLeadRepository();
      final (:container, :controller) = _harness(repo);
      repo.complete(0, ['initial']);
      await container.read(leadListControllerProvider.future);

      // Two searches in flight: the debounce is 400ms, a mobile round trip is
      // routinely slower, so this is the ordinary case rather than the odd one.
      unawaited(controller.applyFilters(const LeadFilters(search: 'ha')));
      unawaited(controller.applyFilters(const LeadFilters(search: 'hanoi')));

      // The *later* request answers first, then the earlier one.
      repo.complete(2, ['Hanoi Grand']);
      await pumpEventQueue();
      repo.complete(1, ['Ha Long', 'Hanoi Grand']);
      await pumpEventQueue();

      expect(_names(container), ['Hanoi Grand']);
      expect(
        container.read(leadListControllerProvider).valueOrNull?.filters.search,
        'hanoi',
      );
    });

    test('a superseded search failing does not blank the current results', () async {
      final repo = _ScriptedLeadRepository();
      final (:container, :controller) = _harness(repo);
      repo.complete(0, ['initial']);
      await container.read(leadListControllerProvider.future);

      unawaited(controller.applyFilters(const LeadFilters(search: 'ha')));
      unawaited(controller.applyFilters(const LeadFilters(search: 'hanoi')));
      repo.complete(2, ['Hanoi Grand']);
      await pumpEventQueue();
      // The abandoned request errors out (this is what cancelling it looks like).
      repo.fail(1, Exception('cancelled'));
      await pumpEventQueue();

      expect(container.read(leadListControllerProvider).hasError, isFalse);
      expect(_names(container), ['Hanoi Grand']);
    });

    test('a page landing after a refresh is dropped, not appended', () async {
      final repo = _ScriptedLeadRepository();
      final (:container, :controller) = _harness(repo);
      repo.complete(0, ['a', 'b'], hasMore: true);
      await container.read(leadListControllerProvider.future);

      unawaited(controller.loadMore()); // request 1 — page 1, in flight
      unawaited(controller.refresh()); // request 2 — page 0, overtakes it
      repo.complete(2, ['a2', 'b2']);
      await pumpEventQueue();
      repo.complete(1, ['c', 'd']); // the stale page finally arrives
      await pumpEventQueue();

      // Without the generation check this read ['a', 'b', 'c', 'd'] — the
      // refreshed list overwritten by the pre-refresh one plus a page fetched
      // under the old query.
      expect(_names(container), ['a2', 'b2']);
      expect(
        container.read(leadListControllerProvider).valueOrNull?.nextPage,
        1,
      );
    });

    test('loadMore appends when nothing else moved', () async {
      final repo = _ScriptedLeadRepository();
      final (:container, :controller) = _harness(repo);
      repo.complete(0, ['a', 'b'], hasMore: true);
      await container.read(leadListControllerProvider.future);

      final done = controller.loadMore();
      repo.complete(1, ['c', 'd']);
      await done;

      expect(_names(container), ['a', 'b', 'c', 'd']);
      expect(repo.calls.last.page, 1);
      expect(
        container.read(leadListControllerProvider).valueOrNull?.nextPage,
        2,
      );
    });

    test('re-selecting the same filters costs no request', () async {
      final repo = _ScriptedLeadRepository();
      final (:container, :controller) = _harness(repo);
      repo.complete(0, ['a']);
      await container.read(leadListControllerProvider.future);
      expect(repo.calls, hasLength(1));

      await controller.applyFilters(const LeadFilters());
      expect(repo.calls, hasLength(1), reason: 'identical selection');

      unawaited(controller.applyFilters(const LeadFilters(search: 'x')));
      expect(repo.calls, hasLength(2), reason: 'a real change still refetches');
    });

    test('after a failure, the same selection retries', () async {
      final repo = _ScriptedLeadRepository();
      final (:container, :controller) = _harness(repo);
      repo.fail(0, Exception('offline'));
      await pumpEventQueue();
      expect(container.read(leadListControllerProvider).hasError, isTrue);

      unawaited(controller.applyFilters(const LeadFilters()));
      expect(repo.calls, hasLength(2));
    });
  });
}
