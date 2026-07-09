import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../../core/network/pagination_response.dart';
import 'lead_models.dart';

/// All lead API calls. Thin, typed wrappers over [ApiClient] — no business
/// logic, no state. Providers/controllers own state.
class LeadRepository {
  LeadRepository(this._client);

  final ApiClient _client;

  /// UC-24.14 / UC-24.15 — paged, searchable, filterable assigned-lead list.
  /// All filter/sort/scope params come from [LeadFilters.toQuery].
  Future<PaginationResponse<Lead>> getLeads({
    LeadFilters filters = const LeadFilters(),
    int page = 0,
    int size = 15,
    CancelToken? cancelToken,
  }) {
    return _client.getPaged<Lead>(
      ApiPaths.leads,
      query: {...filters.toQuery(), 'page': page, 'size': size},
      decodeItem: (item) => Lead.fromJson(item as Map<String, dynamic>),
      cancelToken: cancelToken,
    );
  }

  /// UC-24.3 — full lead detail.
  Future<Lead> getLead(String leadId) {
    return _client.get<Lead>(
      ApiPaths.leadById(leadId),
      decode: (data) => Lead.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-24.2 — create a quick lead.
  Future<Lead> createLead(CreateLeadPayload payload) {
    return _client.post<Lead>(
      ApiPaths.leads,
      data: payload.toJson(),
      decode: (data) => Lead.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-24.4 — update lead status (via the PUT update endpoint).
  Future<Lead> updateStatus(String leadId, LeadStatus status) {
    return _client.put<Lead>(
      ApiPaths.leadById(leadId),
      data: {'status': status.wire},
      decode: (data) => Lead.fromJson(data as Map<String, dynamic>),
    );
  }
}

final leadRepositoryProvider = Provider<LeadRepository>((ref) {
  return LeadRepository(ref.watch(apiClientProvider));
});
