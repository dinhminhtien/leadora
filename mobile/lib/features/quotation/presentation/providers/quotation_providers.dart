import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/quotation_models.dart';
import '../../data/quotation_repository.dart';

/// Single-quotation detail, keyed by id.
final quotationDetailProvider =
    AutoDisposeFutureProvider.family<Quotation, String>((ref, quotationId) {
      return ref.watch(quotationRepositoryProvider).getQuotation(quotationId);
    });

/// All quotations visible to this caller — backs [QuotationListScreen].
final quotationListProvider = AutoDisposeFutureProvider<List<Quotation>>((ref) {
  return ref.watch(quotationRepositoryProvider).getQuotations();
});
