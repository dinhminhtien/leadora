import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/interaction_models.dart';
import '../../data/interaction_repository.dart';

/// Timeline for one linked record (lead/customer/deal), keyed by linkedId.
/// The backend list endpoint has no linkedId filter, so this fetches the
/// visible list and filters + sorts (most recent first) client-side.
final interactionTimelineProvider =
    AutoDisposeFutureProvider.family<List<InteractionTimelineEntry>, String>(
        (ref, linkedId) async {
  final all = await ref.watch(interactionRepositoryProvider).getInteractions();
  final matches = all.where((e) => e.linkedId == linkedId).toList()
    ..sort((a, b) => (b.occurredAt ?? b.createdAt ?? DateTime(0))
        .compareTo(a.occurredAt ?? a.createdAt ?? DateTime(0)));
  return matches;
});

final interactionAuditLogsProvider =
    AutoDisposeFutureProvider.family<List<InteractionAuditLog>, String>((ref, interactionId) {
  return ref.watch(interactionRepositoryProvider).getAuditLogs(interactionId);
});