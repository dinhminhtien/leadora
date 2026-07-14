import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'dashboard_models.dart';

/// Dashboard KPI aggregation — all computed server-side (UC-24.1).
class DashboardRepository {
  DashboardRepository(this._client);

  final ApiClient _client;

  Future<DashboardSummary> getSummary() {
    return _client.get<DashboardSummary>(
      ApiPaths.dashboardSummary,
      decode: (data) => DashboardSummary.fromJson(data as Map<String, dynamic>),
    );
  }
}

final dashboardRepositoryProvider = Provider<DashboardRepository>((ref) {
  return DashboardRepository(ref.watch(apiClientProvider));
});

final dashboardSummaryProvider = FutureProvider.autoDispose<DashboardSummary>((
  ref,
) {
  return ref.watch(dashboardRepositoryProvider).getSummary();
});
