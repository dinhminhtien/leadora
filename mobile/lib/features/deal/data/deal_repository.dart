import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'deal_models.dart';

/// All deal API calls needed by the mobile app. Thin, typed wrapper over
/// [ApiClient] — no business logic, no state.
class DealRepository {
  DealRepository(this._client);

  final ApiClient _client;

  /// `GET /deals` returns a plain array, not a Spring `Page` — there is no
  /// server-side paging on this endpoint. The list screen filters, sorts and
  /// pages the result client-side.
  Future<List<Deal>> getDeals({
    String? search,
    String? ownerId,
    CancelToken? cancelToken,
  }) {
    return _client.get<List<Deal>>(
      ApiPaths.deals,
      query: {
        if (search != null && search.isNotEmpty) 'search': search,
        'ownerId': ?ownerId,
      },
      decode: (data) => (data as List<dynamic>)
          .map((e) => Deal.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      cancelToken: cancelToken,
    );
  }

  /// Full deal detail.
  Future<Deal> getDeal(String dealId) {
    return _client.get<Deal>(
      ApiPaths.dealById(dealId),
      decode: (data) => Deal.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<Deal> createDeal(DealPayload payload) {
    return _client.post<Deal>(
      ApiPaths.deals,
      data: payload.toJson(),
      decode: (data) => Deal.fromJson(data as Map<String, dynamic>),
    );
  }

  /// Partial update — omitted fields are left unchanged server-side. Advancing
  /// `stage` runs the backend's stage-transition validation, which can reject
  /// the call with a `BusinessRuleException` (e.g. Proposal needs a deal value).
  Future<Deal> updateDeal(String dealId, DealPayload payload) {
    return _client.put<Deal>(
      ApiPaths.dealById(dealId),
      data: payload.toJson(),
      decode: (data) => Deal.fromJson(data as Map<String, dynamic>),
    );
  }

  /// Close an open deal as won or lost. Skips stage-transition validation, so
  /// marking a deal lost does not require an estimated close date.
  Future<Deal> updateDealStatus(String dealId, DealStatus status) {
    return _client.patch<Deal>(
      ApiPaths.dealStatus(dealId),
      data: {'status': status.wire},
      decode: (data) => Deal.fromJson(data as Map<String, dynamic>),
    );
  }
}

final dealRepositoryProvider = Provider<DealRepository>((ref) {
  return DealRepository(ref.watch(apiClientProvider));
});
