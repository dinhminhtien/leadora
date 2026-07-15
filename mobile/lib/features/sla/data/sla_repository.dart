import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'sla_models.dart';

/// SLA monitoring API calls. Read-only on mobile (rule CRUD stays web-only).
class SlaRepository {
  SlaRepository(this._client);

  final ApiClient _client;

  /// UC-17.3 — View SLA on Mobile. [entityType] / [displayStatus] mirror the
  /// backend's own filter params (LEAD/QUOTATION/TASK/PAYMENT/HANDOVER and
  /// WITHIN_SLA/WARNING/BREACHED); both are optional server-side filters.
  /// The result set itself is also owner-scoped server-side
  /// (`GetSlaMonitoringUseCase`): SALES only sees SLA activity on leads,
  /// quotations, and tasks they own; MANAGER/ADMIN see everything.
  Future<List<SlaTrackingEntry>> getMonitoring({
    String? entityType,
    String? displayStatus,
  }) {
    return _client.get<List<SlaTrackingEntry>>(
      ApiPaths.slaMonitoring,
      query: {'entityType': ?entityType, 'displayStatus': ?displayStatus},
      decode: (data) => (data as List)
          .map((e) => SlaTrackingEntry.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

final slaRepositoryProvider = Provider<SlaRepository>((ref) {
  return SlaRepository(ref.watch(apiClientProvider));
});
