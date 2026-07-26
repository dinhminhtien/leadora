import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/pagination_response.dart';
import '../../data/handover_models.dart';
import '../../data/handover_repository.dart';

/// Handovers are Sales-facing on mobile: submit a confirmed booking to the Front Office
/// and read back how far the desk has got. Preparing the arrival (setting readiness) is a
/// Front Office job on the web app, so there are no arrival-desk providers here.

/// Status filter for the list. `null` = all.
final handoverStatusFilterProvider = StateProvider.autoDispose<HandoverStatus?>(
  (_) => null,
);

final operationalHandoverListProvider =
    AutoDisposeFutureProvider<PaginationResponse<Handover>>((ref) {
      final status = ref.watch(handoverStatusFilterProvider);
      return ref
          .watch(handoverRepositoryProvider)
          .getOperationalHandovers(status: status);
    });

final operationalHandoverDetailProvider =
    AutoDisposeFutureProvider.family<Handover, String>((ref, id) {
      return ref.watch(handoverRepositoryProvider).getOperationalHandover(id);
    });

/// Mutations. Same controller shape as `DealActions` / `QuotationActions`.
class HandoverActions {
  HandoverActions(this._ref);

  final Ref _ref;

  HandoverRepository get _repo => _ref.read(handoverRepositoryProvider);

  Future<Handover> create(CreateHandoverPayload payload) async {
    final handover = await _repo.createHandover(payload);
    _ref.invalidate(operationalHandoverListProvider);
    return handover;
  }

  Future<Handover> update(String id, CreateHandoverPayload payload) async {
    final handover = await _repo.updateHandover(id, payload);
    _ref.invalidate(operationalHandoverListProvider);
    _ref.invalidate(operationalHandoverDetailProvider(id));
    return handover;
  }
}

final handoverActionsProvider = Provider<HandoverActions>(HandoverActions.new);
