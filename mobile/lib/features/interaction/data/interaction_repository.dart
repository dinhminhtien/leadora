import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'interaction_models.dart';

/// Interaction Timeline API calls — backs Log Customer Interaction Note,
/// Record Customer Meeting Summary, and View Interaction Timeline.
class InteractionRepository {
  InteractionRepository(this._client);

  final ApiClient _client;

  /// Interactions attached to one linked record (lead/customer/deal), newest
  /// first. Filtering and paging happen server-side (`linkedType`+`linkedId`,
  /// `Page<InteractionTimelineResponse>` ordered by `occurredAt DESC`). A single
  /// record's history is small, so we request one generous page rather than
  /// wiring infinite scroll here.
  Future<List<InteractionTimelineEntry>> getRecordInteractions({
    required String linkedType,
    required String linkedId,
    String? search,
    InteractionType? type,
    int size = 100,
  }) async {
    final page = await _client.getPaged<InteractionTimelineEntry>(
      ApiPaths.interactionTimeline,
      query: {
        'linkedType': linkedType,
        'linkedId': linkedId,
        if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
        'type': ?type?.wire,
        'page': 0,
        'size': size,
      },
      decodeItem: (item) =>
          InteractionTimelineEntry.fromJson(item as Map<String, dynamic>),
    );
    return page.items;
  }

  Future<InteractionTimelineEntry> getInteraction(String id) {
    return _client.get<InteractionTimelineEntry>(
      ApiPaths.interactionTimelineById(id),
      decode: (data) =>
          InteractionTimelineEntry.fromJson(data as Map<String, dynamic>),
    );
  }

  /// Log Customer Interaction Note / Record Customer Meeting Summary.
  Future<InteractionTimelineEntry> createInteraction(
    CreateInteractionPayload payload,
  ) {
    return _client.post<InteractionTimelineEntry>(
      ApiPaths.interactionTimeline,
      data: payload.toJson(),
      decode: (data) =>
          InteractionTimelineEntry.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<InteractionTimelineEntry> updateInteraction(
    String id,
    UpdateInteractionPayload payload,
  ) {
    return _client.put<InteractionTimelineEntry>(
      ApiPaths.interactionTimelineById(id),
      data: payload.toJson(),
      decode: (data) =>
          InteractionTimelineEntry.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<List<InteractionAuditLog>> getAuditLogs(String id) {
    return _client.get<List<InteractionAuditLog>>(
      ApiPaths.interactionTimelineAuditLogs(id),
      decode: (data) => (data as List)
          .map((e) => InteractionAuditLog.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

final interactionRepositoryProvider = Provider<InteractionRepository>((ref) {
  return InteractionRepository(ref.watch(apiClientProvider));
});
