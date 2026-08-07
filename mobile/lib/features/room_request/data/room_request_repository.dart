import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import 'room_request_models.dart';

/// Room availability requests between Sales and the Reservation team. Thin, typed
/// wrappers over [ApiClient] — no business logic, no state.
///
/// This CRM owns no room inventory: the Reservation team answers from the hotel's real
/// PMS and the answer is recorded here. Sending a quotation and converting it to a
/// booking are both gated server-side on a confirmed answer, so a 409 from those
/// endpoints carries one of the `ROOM_*` error codes.
class RoomRequestRepository {
  RoomRequestRepository(this._client);

  final ApiClient _client;


  /// Every request raised for one quotation, newest first — backs the room-confirmation
  /// card on the quotation detail screen.
  Future<List<RoomRequest>> getByQuotation(String quotationId) {
    return _client.get<List<RoomRequest>>(
      ApiPaths.roomRequestsByQuotation(quotationId),
      decode: (data) => (data as List)
          .map((e) => RoomRequest.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  /// Sales asks the Reservation team about a quotation's rooms.
  ///
  /// Throws when no active RESERVATION user exists (`NO_RESERVATION_STAFF`) — raised at
  /// request time on purpose, since Send is gated and a silent no-recipient would leave
  /// the quotation stuck with no explanation.
  Future<RoomRequest> create(CreateRoomRequestPayload payload) {
    return _client.post<RoomRequest>(
      ApiPaths.roomRequests,
      data: payload.toJson(),
      decode: (data) => RoomRequest.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-26.4 — Sales withdraws a request the Reservation team has not answered yet.
  ///
  /// Only a PENDING request can be withdrawn. The backend re-checks that under a lock
  /// and throws `ROOM_REQUEST_ALREADY_PROCESSED` (409) when Reservation answered first,
  /// so callers must surface the message rather than assume success.
  Future<RoomRequest> cancel(String requestId, {String? reason}) {
    return _client.patch<RoomRequest>(
      ApiPaths.roomRequestCancel(requestId),
      data: {if (reason != null && reason.isNotEmpty) 'reason': reason},
      decode: (data) => RoomRequest.fromJson(data as Map<String, dynamic>),
    );
  }
}

final roomRequestRepositoryProvider = Provider<RoomRequestRepository>((ref) {
  return RoomRequestRepository(ref.watch(apiClientProvider));
});
