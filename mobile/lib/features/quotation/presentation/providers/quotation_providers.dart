import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../booking/data/booking_models.dart';
import '../../data/quotation_models.dart';
import '../../data/quotation_repository.dart';

/// Single-quotation detail, keyed by id.
final quotationDetailProvider =
    AutoDisposeFutureProvider.family<Quotation, String>((ref, quotationId) {
      return ref.watch(quotationRepositoryProvider).getQuotation(quotationId);
    });

/// All quotations visible to this caller — backs [QuotationListScreen].
final quotationListProvider = AutoDisposeFutureProvider<List<Quotation>>((ref) {
  return ref.watch(quotationRepositoryProvider).getQuotations();
});

/// Quotations waiting on a manager's decision (MANAGER only — SALES gets 403).
final pendingApprovalsProvider = AutoDisposeFutureProvider<List<Quotation>>((ref) {
  return ref.watch(quotationRepositoryProvider).getPendingApprovals();
});

/// Mutations. Kept as a controller so screens get one place to await + refresh,
/// mirroring `DealActions`.
class QuotationActions {
  QuotationActions(this._ref);

  final Ref _ref;

  QuotationRepository get _repo => _ref.read(quotationRepositoryProvider);

  /// Refreshes the list and, when known, the specific quotation a screen is showing.
  void _refresh([String? quotationId]) {
    _ref.invalidate(quotationListProvider);
    _ref.invalidate(pendingApprovalsProvider);
    if (quotationId != null) _ref.invalidate(quotationDetailProvider(quotationId));
  }

  /// UC-14.1 — always lands as DRAFT; Submit decides the next status.
  Future<Quotation> create(CreateQuotationPayload payload) async {
    final quotation = await _repo.createQuotation(payload);
    _refresh();
    return quotation;
  }

  /// UC-14.2 — DRAFT → APPROVED, or PENDING_APPROVAL when the discount is over the
  /// backend's threshold. Throws `NO_MANAGER_AVAILABLE` when approval is needed and
  /// no manager account exists.
  Future<Quotation> submit(String id, {String? byName, String? byRole}) async {
    final quotation = await _repo.submitQuotation(
      id,
      SubmitQuotationPayload(submittedByName: byName, submittedByRole: byRole),
    );
    _refresh(id);
    return quotation;
  }

  /// UC-14.4 — send to the customer. Room-gated: a 409 carries a `ROOM_*` code.
  Future<Quotation> send(String id, SendQuotationPayload payload) async {
    final quotation = await _repo.sendQuotation(id, payload);
    _refresh(id);
    return quotation;
  }

  /// UC-14.5 — new version; the parent becomes SUPERSEDED.
  Future<Quotation> revise(String id, ReviseQuotationPayload payload) async {
    final quotation = await _repo.reviseQuotation(id, payload);
    _refresh(id);
    return quotation;
  }

  /// UC-14.6 — record what the customer said.
  Future<Quotation> trackResponse(String id, TrackCustomerResponsePayload payload) async {
    final quotation = await _repo.trackCustomerResponse(id, payload);
    _refresh(id);
    return quotation;
  }

  /// UC-14.7 — ACCEPTED quotation → PENDING booking. Room-gated. Returns the booking.
  Future<Booking> convertToBooking(String id, ConvertToBookingPayload payload) async {
    final booking = await _repo.convertToBooking(id, payload);
    _refresh(id);
    return booking;
  }

  /// UC-14.8 — close a quotation that will not proceed.
  Future<Quotation> close(String id, CloseQuotationPayload payload) async {
    final quotation = await _repo.closeQuotation(id, payload);
    _refresh(id);
    return quotation;
  }
}

final quotationActionsProvider = Provider<QuotationActions>(QuotationActions.new);
