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

  /// List interactions visible to the current user (scoped server-side to
  /// the caller unless they're a manager). The backend has no linkedId
  /// filter, so callers filter the result client-side by linkedId.
  Future<List<InteractionTimelineEntry>> getInteractions({
    String? search,
    InteractionType? type,
    String? agentId,
  }) {
    return _client.get<List<InteractionTimelineEntry>>(
      ApiPaths.interactionTimeline,
      query: {
        if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
        'type': ?type?.wire,
        'agentId': ?agentId,
      },
      decode: (data) => (data as List)
          .map(
            (e) => InteractionTimelineEntry.fromJson(e as Map<String, dynamic>),
          )
          .toList(),
    );
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
