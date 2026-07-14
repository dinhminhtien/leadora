import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../../core/network/pagination_response.dart';
import 'customer_models.dart';

/// All customer-profile API calls. Mirrors the web `customerProfileService`.
class CustomerRepository {
  CustomerRepository(this._client);

  final ApiClient _client;

  /// Full paginated list for the Customer Profiles screen — `GET /customers/list`.
  Future<PaginationResponse<Customer>> getCustomers({
    String? search,
    CustomerType? customerType,
    CustomerStatus? status,
    String sortBy = 'createdAt',
    String sortDir = 'desc',
    int page = 0,
    int size = 10,
  }) {
    return _client.getPaged<Customer>(
      ApiPaths.customersList,
      query: {
        if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
        'customerType': ?customerType?.wire,
        'status': ?status?.wire,
        'sortBy': sortBy,
        'sortDir': sortDir,
        'page': page,
        'size': size,
      },
      decodeItem: (item) => Customer.fromJson(item as Map<String, dynamic>),
    );
  }

  /// Customer detail — `GET /customers/{id}`.
  Future<Customer> getCustomer(String customerId) {
    return _client.get<Customer>(
      ApiPaths.customerById(customerId),
      decode: (data) => Customer.fromJson(data as Map<String, dynamic>),
    );
  }

  /// Global counts, unaffected by search/filter — `GET /customers/stats`.
  Future<CustomerStats> getStats() {
    return _client.get<CustomerStats>(
      ApiPaths.customersStats,
      decode: (data) => CustomerStats.fromJson(data as Map<String, dynamic>),
    );
  }

  /// Activity history (deals, bookings, quotations) — `GET /customers/{id}/history`.
  Future<List<CustomerHistoryItem>> getHistory(String customerId) {
    return _client.get<List<CustomerHistoryItem>>(
      ApiPaths.customerHistory(customerId),
      decode: (data) => (data as List<dynamic>)
          .map((e) => CustomerHistoryItem.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  /// Create a customer profile — `POST /customers`.
  Future<Customer> createCustomer(CreateCustomerPayload payload) {
    return _client.post<Customer>(
      ApiPaths.customers,
      data: payload.toJson(),
      decode: (data) => Customer.fromJson(data as Map<String, dynamic>),
    );
  }

  /// Update a customer profile — `PUT /customers/{id}`.
  Future<Customer> updateCustomer(
    String customerId,
    UpdateCustomerPayload payload,
  ) {
    return _client.put<Customer>(
      ApiPaths.customerById(customerId),
      data: payload.toJson(),
      decode: (data) => Customer.fromJson(data as Map<String, dynamic>),
    );
  }
}

final customerRepositoryProvider = Provider<CustomerRepository>((ref) {
  return CustomerRepository(ref.watch(apiClientProvider));
});
