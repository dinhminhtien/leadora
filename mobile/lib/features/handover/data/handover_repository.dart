import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../../core/network/pagination_response.dart';
import 'handover_models.dart';

/// Handovers Sales submits to the Front Office, via `/operational-handovers`.
///
/// The arrival desk's own endpoints (`/arrival-handovers`, including the only place
/// readiness can be changed) are FO/MANAGER/ADMIN and are deliberately not wrapped here:
/// this app is for Sales, so preparing an arrival happens on the web app. Sales still
/// reads the readiness the desk set, because a clarification request is theirs to answer.
class HandoverRepository {
  HandoverRepository(this._client);

  final ApiClient _client;

  Future<PaginationResponse<Handover>> getOperationalHandovers({
    String? search,
    HandoverStatus? status,
    int page = 0,
    int size = 20,
  }) {
    return _client.getPaged<Handover>(
      ApiPaths.operationalHandovers,
      query: {
        if (search != null && search.isNotEmpty) 'search': search,
        if (status != null) 'status': status.wire,
        'page': page,
        'size': size,
      },
      decodeItem: (item) => Handover.fromJson(item as Map<String, dynamic>),
    );
  }

  Future<Handover> getOperationalHandover(String handoverId) {
    return _client.get<Handover>(
      ApiPaths.operationalHandoverById(handoverId),
      decode: (data) => Handover.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-22 — hand a confirmed booking to the Front Office. The backend refuses a
  /// duplicate handover for the same booking.
  Future<Handover> createHandover(CreateHandoverPayload payload) {
    return _client.post<Handover>(
      ApiPaths.operationalHandovers,
      data: payload.toJson(),
      decode: (data) => Handover.fromJson(data as Map<String, dynamic>),
    );
  }

  /// Amend the notes/preferences on an existing handover.
  Future<Handover> updateHandover(String handoverId, CreateHandoverPayload payload) {
    return _client.put<Handover>(
      ApiPaths.operationalHandoverById(handoverId),
      data: payload.toJson(),
      decode: (data) => Handover.fromJson(data as Map<String, dynamic>),
    );
  }
}

final handoverRepositoryProvider = Provider<HandoverRepository>((ref) {
  return HandoverRepository(ref.watch(apiClientProvider));
});
