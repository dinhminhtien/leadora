/// Guest feedback captured after a stay (UC-25).
///
/// Read-only on mobile apart from the review-status transition: the feedback
/// itself is written by the guest through a public secure link (BR-30/BR-31),
/// never by a staff device.
library;

/// Moderation state. Mirrors backend `ReviewStatus`.
enum FeedbackReviewStatus {
  pending,
  reviewed,
  dismissed;

  static FeedbackReviewStatus fromJson(String? raw) {
    switch ((raw ?? '').toUpperCase()) {
      case 'REVIEWED':
        return FeedbackReviewStatus.reviewed;
      case 'DISMISSED':
        return FeedbackReviewStatus.dismissed;
      default:
        return FeedbackReviewStatus.pending;
    }
  }

  String get wire => switch (this) {
    FeedbackReviewStatus.pending => 'PENDING',
    FeedbackReviewStatus.reviewed => 'REVIEWED',
    FeedbackReviewStatus.dismissed => 'DISMISSED',
  };

  String get label => switch (this) {
    FeedbackReviewStatus.pending => 'Pending review',
    FeedbackReviewStatus.reviewed => 'Reviewed',
    FeedbackReviewStatus.dismissed => 'Dismissed',
  };
}

/// One guest's response.
///
/// Ratings are `Short` on the wire and **nullable**: a guest can submit a
/// comment without scoring, and the three sub-scores are optional even when the
/// overall one is given. Every rating field is therefore `int?`, and the UI must
/// treat null as "not rated" rather than zero — a zero star row would read as
/// the worst possible score.
class GuestFeedback {
  const GuestFeedback({
    required this.feedbackId,
    required this.reviewStatus,
    this.customerName,
    this.bookingCode,
    this.salesStaffName,
    this.rating,
    this.ratingAttitude,
    this.ratingSpeed,
    this.ratingAccuracy,
    this.comment,
    this.submittedAt,
    this.reviewedByName,
    this.reviewedAt,
    this.createdAt,
  });

  final String feedbackId;
  final FeedbackReviewStatus reviewStatus;
  final String? customerName;
  final String? bookingCode;
  final String? salesStaffName;
  final int? rating;
  final int? ratingAttitude;
  final int? ratingSpeed;
  final int? ratingAccuracy;
  final String? comment;
  final DateTime? submittedAt;
  final String? reviewedByName;
  final DateTime? reviewedAt;
  final DateTime? createdAt;

  bool get hasRating => rating != null && rating! > 0;

  /// Whether the guest scored any of the three service dimensions.
  bool get hasBreakdown =>
      ratingAttitude != null || ratingSpeed != null || ratingAccuracy != null;

  static DateTime? _date(dynamic v) =>
      v == null ? null : DateTime.tryParse(v as String)?.toLocal();

  static int? _int(dynamic v) => v == null ? null : (v as num).toInt();

  factory GuestFeedback.fromJson(Map<String, dynamic> json) {
    return GuestFeedback(
      feedbackId: json['feedbackId'] as String,
      reviewStatus: FeedbackReviewStatus.fromJson(
        json['reviewStatus'] as String?,
      ),
      customerName: json['customerName'] as String?,
      bookingCode: json['bookingCode'] as String?,
      salesStaffName: json['salesStaffName'] as String?,
      rating: _int(json['rating']),
      ratingAttitude: _int(json['ratingAttitude']),
      ratingSpeed: _int(json['ratingSpeed']),
      ratingAccuracy: _int(json['ratingAccuracy']),
      comment: json['comment'] as String?,
      submittedAt: _date(json['submittedAt']),
      reviewedByName: json['reviewedByName'] as String?,
      reviewedAt: _date(json['reviewedAt']),
      createdAt: _date(json['createdAt']),
    );
  }

  GuestFeedback withReviewStatus(FeedbackReviewStatus next, {String? reviewerName}) {
    return GuestFeedback(
      feedbackId: feedbackId,
      reviewStatus: next,
      customerName: customerName,
      bookingCode: bookingCode,
      salesStaffName: salesStaffName,
      rating: rating,
      ratingAttitude: ratingAttitude,
      ratingSpeed: ratingSpeed,
      ratingAccuracy: ratingAccuracy,
      comment: comment,
      submittedAt: submittedAt,
      reviewedByName: reviewerName ?? reviewedByName,
      reviewedAt: DateTime.now(),
      createdAt: createdAt,
    );
  }
}

/// Server-side filters for `GET /feedbacks`.
class FeedbackFilters {
  const FeedbackFilters({this.reviewStatus, this.rating, this.search});

  final FeedbackReviewStatus? reviewStatus;
  final int? rating;
  final String? search;

  bool get isEmpty =>
      reviewStatus == null &&
      rating == null &&
      (search == null || search!.trim().isEmpty);

  FeedbackFilters copyWith({
    FeedbackReviewStatus? reviewStatus,
    int? rating,
    String? search,
    bool clearReviewStatus = false,
    bool clearRating = false,
  }) {
    return FeedbackFilters(
      reviewStatus: clearReviewStatus
          ? null
          : (reviewStatus ?? this.reviewStatus),
      rating: clearRating ? null : (rating ?? this.rating),
      search: search ?? this.search,
    );
  }
}
