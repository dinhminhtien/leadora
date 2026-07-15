import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
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
}

final quotationRepositoryProvider = Provider<QuotationRepository>((ref) {
  return QuotationRepository(ref.watch(apiClientProvider));
});
