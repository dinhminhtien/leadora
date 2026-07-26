import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../booking/data/booking_models.dart';
import 'quotation_models.dart';

/// All quotation API calls needed by the mobile app. Thin, typed wrappers
/// over [ApiClient] — no business logic, no state.
class QuotationRepository {
  QuotationRepository(this._client);

  final ApiClient _client;

  /// View Quotation Status (list) — all quotations visible to this caller.
  /// The backend endpoint is unpaged and already owner-scoped server-side
  /// (`GetQuotationListUseCase` / `QuotationAccessPolicy`): a SALES caller
  /// only gets quotations they created, while MANAGER/ADMIN get every
  /// quotation — this returns whatever that resolves to in one call.
  Future<List<Quotation>> getQuotations() {
    return _client.get<List<Quotation>>(
      ApiPaths.quotations,
      decode: (data) => (data as List)
          .map((e) => Quotation.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  /// UC-14.5 / View Quotation Status — full quotation detail.
  ///
  /// A SALES caller opening a quotation they didn't create gets a 403
  /// (`QuotationAccessPolicy.assertCanView`), surfaced by [ApiClient] as a
  /// [ForbiddenException] — the screen's `AsyncValueView` renders that like
  /// any other load error rather than crashing.
  Future<Quotation> getQuotation(String quotationId) {
    return _client.get<Quotation>(
      ApiPaths.quotationById(quotationId),
      decode: (data) => Quotation.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-14.6 — record the customer's decision on a sent quotation.
  Future<Quotation> trackCustomerResponse(
    String quotationId,
    TrackCustomerResponsePayload payload,
  ) {
    return _client.post<Quotation>(
      ApiPaths.quotationTrackResponse(quotationId),
      data: payload.toJson(),
      decode: (data) => Quotation.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-14.1 — create a quotation on a deal. Always comes back DRAFT.
  Future<Quotation> createQuotation(CreateQuotationPayload payload) {
    return _client.post<Quotation>(
      ApiPaths.quotations,
      data: payload.toJson(),
      decode: (data) => Quotation.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-14.2 — submit a DRAFT. Over the discount threshold it becomes
  /// PENDING_APPROVAL, otherwise APPROVED. Throws `NO_MANAGER_AVAILABLE` when the
  /// discount needs approval and no manager account exists.
  Future<Quotation> submitQuotation(String quotationId, SubmitQuotationPayload payload) {
    return _client.post<Quotation>(
      ApiPaths.quotationSubmit(quotationId),
      data: payload.toJson(),
      decode: (data) => Quotation.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-14.4 — send an APPROVED quotation to the customer.
  ///
  /// Gated on the Reservation team having confirmed the rooms: a 409 carries one of the
  /// `ROOM_*` codes (ROOM_NOT_REQUESTED, ROOM_PENDING_CONFIRMATION, ROOM_REJECTED,
  /// ROOM_CONFIRMATION_STALE, ROOM_HOLD_EXPIRED), which the UI turns into an
  /// explanation plus a link to the room request.
  Future<Quotation> sendQuotation(String quotationId, SendQuotationPayload payload) {
    return _client.post<Quotation>(
      ApiPaths.quotationSend(quotationId),
      data: payload.toJson(),
      decode: (data) => Quotation.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-14.5 — create the next version; the parent becomes SUPERSEDED.
  Future<Quotation> reviseQuotation(String quotationId, ReviseQuotationPayload payload) {
    return _client.post<Quotation>(
      ApiPaths.quotationRevise(quotationId),
      data: payload.toJson(),
      decode: (data) => Quotation.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-14.7 — turn an ACCEPTED quotation into a PENDING booking. Returns the booking,
  /// not the quotation. Also room-gated (see [sendQuotation]).
  Future<Booking> convertToBooking(String quotationId, ConvertToBookingPayload payload) {
    return _client.post<Booking>(
      ApiPaths.quotationConvert(quotationId),
      data: payload.toJson(),
      decode: (data) => Booking.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-14.8 — close a quotation that will not proceed.
  Future<Quotation> closeQuotation(String quotationId, CloseQuotationPayload payload) {
    return _client.post<Quotation>(
      ApiPaths.quotationClose(quotationId),
      data: payload.toJson(),
      decode: (data) => Quotation.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-14.3 — quotations waiting on this manager's decision (MANAGER only).
  Future<List<Quotation>> getPendingApprovals() {
    return _client.get<List<Quotation>>(
      ApiPaths.quotationPendingApprovals,
      decode: (data) => (data as List)
          .map((e) => Quotation.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

final quotationRepositoryProvider = Provider<QuotationRepository>((ref) {
  return QuotationRepository(ref.watch(apiClientProvider));
});
