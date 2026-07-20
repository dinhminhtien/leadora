import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../../core/network/pagination_response.dart';
import 'booking_models.dart';

/// Booking API calls the mobile app consumes.
///
/// `POST /bookings` (submit request) and `PUT /bookings/{id}/process` are not
/// wrapped yet — the submit DTO carries a room-line array that has no mobile
/// composer screen, and process is a reservation-desk action.
class BookingRepository {
  BookingRepository(this._client);

  final ApiClient _client;

  Future<PaginationResponse<Booking>> getBookings({
    BookingFilters filters = const BookingFilters(),
    int page = 0,
    int size = 15,
    CancelToken? cancelToken,
  }) {
    return _client.getPaged<Booking>(
      ApiPaths.bookings,
      query: {...filters.toQuery(), 'page': page, 'size': size},
      decodeItem: (item) => Booking.fromJson(item as Map<String, dynamic>),
      cancelToken: cancelToken,
    );
  }

  Future<Booking> getBooking(String bookingId) {
    return _client.get<Booking>(
      ApiPaths.bookingById(bookingId),
      decode: (data) => Booking.fromJson(data as Map<String, dynamic>),
    );
  }
}

final bookingRepositoryProvider = Provider<BookingRepository>((ref) {
  return BookingRepository(ref.watch(apiClientProvider));
});
