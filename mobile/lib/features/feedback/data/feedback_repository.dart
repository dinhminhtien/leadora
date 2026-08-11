import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../../core/network/pagination_response.dart';
import 'feedback_models.dart';

/// Guest feedback (UC-25). Thin typed wrappers over [ApiClient].
///
/// Only two write paths exist server-side and neither belongs to a staff device:
/// the guest submits through a public token link, and moderation is a single
/// review-status transition. There is deliberately no create/update here.
class FeedbackRepository {
  FeedbackRepository(this._client);

  final ApiClient _client;

  /// `GET /feedbacks` — paged, newest first.
  ///
  /// Visible to SALES/MANAGER/ADMIN; the server scopes a Sales user to their own
  /// customers, so no client-side owner filter is needed (or trustworthy).
  Future<PaginationResponse<GuestFeedback>> getFeedbacks({
    FeedbackFilters filters = const FeedbackFilters(),
    int page = 0,
    int size = 20,
  }) {
    final search = filters.search?.trim();
    return _client.get<PaginationResponse<GuestFeedback>>(
      ApiPaths.feedbacks,
      query: {
        'page': page,
        'size': size,
        if (search != null && search.isNotEmpty) 'search': search,
        if (filters.reviewStatus != null)
          'reviewStatus': filters.reviewStatus!.wire,
        if (filters.rating != null) 'rating': filters.rating,
      },
      decode: (data) => PaginationResponse.parse(
        data,
        (e) => GuestFeedback.fromJson(e as Map<String, dynamic>),
      ),
    );
  }

  Future<GuestFeedback> getById(String id) {
    return _client.get<GuestFeedback>(
      ApiPaths.feedbackById(id),
      decode: (data) => GuestFeedback.fromJson(data as Map<String, dynamic>),
    );
  }

  /// Moderation (MANAGER/ADMIN only — a Sales user gets 403).
  ///
  /// Returns `Void` on the wire, so the caller updates its own copy rather than
  /// waiting for a refetch.
  Future<void> updateReviewStatus(String id, FeedbackReviewStatus status) {
    return _client.patch<void>(
      ApiPaths.feedbackReviewStatus(id),
      data: {'reviewStatus': status.wire},
      decode: (_) {},
    );
  }
}

final feedbackRepositoryProvider = Provider<FeedbackRepository>((ref) {
  return FeedbackRepository(ref.watch(apiClientProvider));
});
