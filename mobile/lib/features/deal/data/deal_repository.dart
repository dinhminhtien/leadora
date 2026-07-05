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

  /// View Related Deal Detail — full deal detail.
  Future<Deal> getDeal(String dealId) {
    return _client.get<Deal>(
      ApiPaths.dealById(dealId),
      decode: (data) => Deal.fromJson(data as Map<String, dynamic>),
    );
  }
}

final dealRepositoryProvider = Provider<DealRepository>((ref) {
  return DealRepository(ref.watch(apiClientProvider));
});
