import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/deal_models.dart';
import '../../data/deal_repository.dart';

/// Single-deal detail, keyed by id.
final dealDetailProvider =
    AutoDisposeFutureProvider.family<Deal, String>((ref, dealId) {
  return ref.watch(dealRepositoryProvider).getDeal(dealId);
});
