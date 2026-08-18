import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/room_request_models.dart';
import '../../data/room_request_repository.dart';

/// Room requests are Sales-facing on mobile: a rep asks the Reservation team about a
/// quotation's rooms and reads the answer. Answering them is a Reservation job and lives
/// on the web app only, so there is no inbox provider here.

/// Every room request raised for one quotation, newest first.
final roomRequestsByQuotationProvider =
    AutoDisposeFutureProvider.family<List<RoomRequest>, String>((ref, quotationId) {
      return ref.watch(roomRequestRepositoryProvider).getByQuotation(quotationId);
    });

/// The request that currently speaks for a quotation, or null when never asked.
final currentRoomRequestProvider =
    AutoDisposeFutureProvider.family<RoomRequest?, String>((ref, quotationId) async {
      final requests = await ref.watch(roomRequestsByQuotationProvider(quotationId).future);
      return currentRoomRequest(requests);
    });
