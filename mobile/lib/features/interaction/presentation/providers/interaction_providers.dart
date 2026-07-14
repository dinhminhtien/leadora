import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/interaction_models.dart';
import '../../data/interaction_repository.dart';

/// Identifies one linked record's timeline. Both parts are needed so the list
/// can be filtered server-side (`linkedType` + `linkedId`) instead of pulling
/// the whole visible history and filtering on the client.
typedef InteractionTimelineArg = ({String linkedType, String linkedId});

/// Timeline for one linked record (lead/customer/deal). The backend filters by
/// `linkedType`+`linkedId` and returns newest-first, so no client-side sorting
/// or filtering is required here.
final interactionTimelineProvider =
    AutoDisposeFutureProvider.family<
      List<InteractionTimelineEntry>,
      InteractionTimelineArg
    >((ref, arg) {
      return ref
          .watch(interactionRepositoryProvider)
          .getRecordInteractions(
            linkedType: arg.linkedType,
            linkedId: arg.linkedId,
          );
    });

/// A single interaction's detail, keyed by interaction id — backs the
/// Interaction Detail screen (View Interaction Detail).
final interactionDetailProvider =
    AutoDisposeFutureProvider.family<InteractionTimelineEntry, String>((
      ref,
      interactionId,
    ) {
      return ref
          .watch(interactionRepositoryProvider)
          .getInteraction(interactionId);
    });

final interactionAuditLogsProvider =
    AutoDisposeFutureProvider.family<List<InteractionAuditLog>, String>((
      ref,
      interactionId,
    ) {
      return ref
          .watch(interactionRepositoryProvider)
          .getAuditLogs(interactionId);
    });
