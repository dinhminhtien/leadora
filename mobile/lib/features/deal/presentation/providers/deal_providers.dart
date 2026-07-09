import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/deal_models.dart';
import '../../data/deal_repository.dart';

/// Single-deal detail, keyed by id.
final dealDetailProvider = AutoDisposeFutureProvider.family<Deal, String>((
  ref,
  dealId,
) {
  return ref.watch(dealRepositoryProvider).getDeal(dealId);
});

/// How the deal list is ordered. Applied client-side — `GET /deals` takes no
/// sort param.
enum DealSort {
  newest('Newest'),
  closingSoon('Closing soon'),
  valueHigh('Value: high → low'),
  valueLow('Value: low → high'),
  title('Title A–Z');

  const DealSort(this.label);
  final String label;
}

/// The active search term. Debounced by the search field, not here.
final dealSearchProvider = StateProvider.autoDispose<String>((_) => '');

/// `null` = the "All" tab.
final dealStageFilterProvider = StateProvider.autoDispose<DealStage?>(
  (_) => null,
);

final dealSortProvider = StateProvider.autoDispose<DealSort>(
  (_) => DealSort.newest,
);

/// Every deal visible to the caller. Search runs server-side (the endpoint
/// accepts `?search=`), so this refetches when the term changes; stage filtering
/// and sorting are local.
final dealListProvider = AutoDisposeFutureProvider<List<Deal>>((ref) async {
  final search = ref.watch(dealSearchProvider);

  // Hold the response briefly so flipping between stage tabs (which does not
  // touch this provider) never re-hits the network.
  final link = ref.keepAlive();
  final timer = Timer(const Duration(minutes: 2), link.close);
  ref.onDispose(timer.cancel);

  return ref.watch(dealRepositoryProvider).getDeals(search: search);
});

/// The list after the stage tab and sort are applied.
final visibleDealsProvider = AutoDisposeProvider<AsyncValue<List<Deal>>>((ref) {
  final stage = ref.watch(dealStageFilterProvider);
  final sort = ref.watch(dealSortProvider);

  return ref.watch(dealListProvider).whenData((deals) {
    final filtered = stage == null
        ? [...deals]
        : deals.where((d) => d.stage == stage).toList();

    // `compareTo` on a nullable needs a total order: nulls sink to the bottom
    // in every ordering so a deal with no value/date never displaces a real one.
    int byNullableNum(num? a, num? b, {required bool descending}) {
      if (a == null && b == null) return 0;
      if (a == null) return 1;
      if (b == null) return -1;
      return descending ? b.compareTo(a) : a.compareTo(b);
    }

    int byNullableDate(DateTime? a, DateTime? b, {required bool descending}) {
      if (a == null && b == null) return 0;
      if (a == null) return 1;
      if (b == null) return -1;
      return descending ? b.compareTo(a) : a.compareTo(b);
    }

    switch (sort) {
      case DealSort.newest:
        filtered.sort(
          (a, b) => byNullableDate(a.createdAt, b.createdAt, descending: true),
        );
      case DealSort.closingSoon:
        filtered.sort(
          (a, b) =>
              byNullableDate(a.expectedClose, b.expectedClose, descending: false),
        );
      case DealSort.valueHigh:
        filtered.sort((a, b) => byNullableNum(a.value, b.value, descending: true));
      case DealSort.valueLow:
        filtered.sort(
          (a, b) => byNullableNum(a.value, b.value, descending: false),
        );
      case DealSort.title:
        filtered.sort(
          (a, b) => a.title.toLowerCase().compareTo(b.title.toLowerCase()),
        );
    }
    return filtered;
  });
});

/// Deal count per stage, for the tab badges. Derived from the unfiltered list so
/// the badges do not change when a tab is selected.
final dealStageCountsProvider = AutoDisposeProvider<Map<DealStage, int>>((ref) {
  final deals = ref.watch(dealListProvider).valueOrNull ?? const <Deal>[];
  final counts = <DealStage, int>{};
  for (final deal in deals) {
    final stage = deal.stage;
    if (stage != null) counts[stage] = (counts[stage] ?? 0) + 1;
  }
  return counts;
});

/// Mutations. Kept as a controller so screens get one place to await + refresh.
class DealActions {
  DealActions(this._ref);

  final Ref _ref;

  DealRepository get _repo => _ref.read(dealRepositoryProvider);

  Future<Deal> create(DealPayload payload) async {
    final deal = await _repo.createDeal(payload);
    _ref.invalidate(dealListProvider);
    return deal;
  }

  Future<Deal> update(String id, DealPayload payload) async {
    final deal = await _repo.updateDeal(id, payload);
    _ref.invalidate(dealListProvider);
    _ref.invalidate(dealDetailProvider(id));
    return deal;
  }

  Future<Deal> setStatus(String id, DealStatus status) async {
    final deal = await _repo.updateDealStatus(id, status);
    _ref.invalidate(dealListProvider);
    _ref.invalidate(dealDetailProvider(id));
    return deal;
  }

  /// Advance one step down the funnel. Returns the updated deal, or throws the
  /// backend's `BusinessRuleException` message when the stage gate is unmet.
  Future<Deal> advanceStage(Deal deal) {
    final next = deal.stage?.next;
    if (next == null) {
      throw StateError('Deal ${deal.id} is already at a terminal stage.');
    }
    return update(
      deal.id,
      DealPayload(
        title: deal.title,
        contactName: deal.contactName ?? '',
        stage: next,
      ),
    );
  }
}

final dealActionsProvider = Provider<DealActions>(DealActions.new);
