import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../../core/network/pagination_response.dart';
import 'payment_models.dart';

/// All payment API calls. Thin, typed wrappers over [ApiClient] — no business
/// logic, no state.
///
/// Every endpoint here is `@PreAuthorize("hasAnyRole('SALES','RESERVATION','ADMIN')")`.
/// A caller without one of those roles gets a 403, which the UI renders as a
/// "No access" state rather than an empty screen.
class PaymentRepository {
  PaymentRepository(this._client);

  final ApiClient _client;

  /// UC-21.3 — paged, searchable, filterable payment list.
  Future<PaginationResponse<Payment>> getPayments({
    PaymentFilters filters = const PaymentFilters(),
    int page = 0,
    int size = 15,
    CancelToken? cancelToken,
  }) {
    return _client.getPaged<Payment>(
      ApiPaths.payments,
      query: {...filters.toQuery(), 'page': page, 'size': size},
      decodeItem: (item) => Payment.fromJson(item as Map<String, dynamic>),
      cancelToken: cancelToken,
    );
  }

  Future<Payment> getPayment(String paymentId) {
    return _client.get<Payment>(
      ApiPaths.paymentById(paymentId),
      decode: (data) => Payment.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-21.1 — generate a payment request against a booking.
  Future<Payment> generate(GeneratePaymentPayload payload) {
    return _client.post<Payment>(
      ApiPaths.payments,
      data: payload.toJson(),
      decode: (data) => Payment.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-21.4 — set the status manually.
  ///
  /// BR-29: the backend rejects a transition to PAID without a
  /// [verificationNote], so the UI must collect one before calling this.
  Future<Payment> updateStatus(
    String paymentId,
    PaymentStatus status, {
    String? verificationNote,
  }) {
    return _client.patch<Payment>(
      ApiPaths.paymentStatus(paymentId),
      data: {'status': status.wire, 'verificationNote': ?verificationNote},
      decode: (data) => Payment.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-21.5 — cancel a pending payment request. No body; a PAID payment is
  /// rejected server-side ("Payment has already been processed").
  Future<Payment> cancel(String paymentId) {
    return _client.patch<Payment>(
      ApiPaths.paymentCancel(paymentId),
      decode: (data) => Payment.fromJson(data as Map<String, dynamic>),
    );
  }
}

final paymentRepositoryProvider = Provider<PaymentRepository>((ref) {
  return PaymentRepository(ref.watch(apiClientProvider));
});
