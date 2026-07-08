import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/sla_models.dart';
import '../../data/sla_repository.dart';

/// SLA monitoring list, keyed by the selected `displayStatus` filter chip
/// (`null` = All). Server-filtered — see [SlaRepository.getMonitoring].
final slaMonitoringProvider =
    AutoDisposeFutureProvider.family<List<SlaTrackingEntry>, String?>((ref, displayStatus) {
  return ref.watch(slaRepositoryProvider).getMonitoring(displayStatus: displayStatus);
});
