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
  /// The backend endpoint is unfiltered/unpaged (`GET /quotations` →
  /// `findAll()`), so this returns everything in one call.
  Future<List<Quotation>> getQuotations() {
    return _client.get<List<Quotation>>(
      ApiPaths.quotations,
      decode: (data) => (data as List)
          .map((e) => Quotation.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  /// UC-14.5 / View Quotation Status — full quotation detail.
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
