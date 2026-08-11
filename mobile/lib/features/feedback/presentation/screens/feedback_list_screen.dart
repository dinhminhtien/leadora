import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_filter_chip.dart';
import '../../../../shared/widgets/app_search_field.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/glass_surface.dart';
import '../../../../shared/widgets/list_skeleton.dart';
import '../../data/feedback_models.dart';
import '../providers/feedback_providers.dart';
import '../widgets/feedback_rating.dart';

/// Placeholder row for the loading skeleton — same shape as a real card so the
/// shimmer occupies the height the data will.
final _skeletonFeedback = GuestFeedback(
  feedbackId: 'skeleton',
  reviewStatus: FeedbackReviewStatus.pending,
  customerName: 'Guest name',
  bookingCode: 'BK-000000',
  salesStaffName: 'Sales staff',
  rating: 5,
  comment: 'A representative comment length for the placeholder row.',
  submittedAt: DateTime(2026),
);

/// UC-25 — Guest feedback on mobile: browse, search, filter by review status
/// and star rating, and open one for the full breakdown.
///
/// Read-only for Sales; Manager/Admin can also move the review status, which
/// happens on the detail screen rather than here (a swipe that silently
/// dismisses a guest's complaint is not a gesture worth having).
class FeedbackListScreen extends ConsumerStatefulWidget {
  const FeedbackListScreen({super.key});

  @override
  ConsumerState<FeedbackListScreen> createState() => _FeedbackListScreenState();
}

class _FeedbackListScreenState extends ConsumerState<FeedbackListScreen> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController
      ..removeListener(_onScroll)
      ..dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_scrollController.hasClients) return;
    final position = _scrollController.position;
    // 320px of runway so the next page is in flight before the user hits the
    // bottom — an empty gap at the end reads as "that's everything".
    if (position.pixels >= position.maxScrollExtent - 320) {
      ref.read(feedbackListControllerProvider.notifier).loadMore();
    }
  }

  void _onSearchChanged(String value) {
    final controller = ref.read(feedbackListControllerProvider.notifier);
    controller.applyFilters(controller.filters.copyWith(search: value));
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(feedbackListControllerProvider);
    final controller = ref.read(feedbackListControllerProvider.notifier);
    final filters = state.valueOrNull?.filters ?? const FeedbackFilters();

    return Scaffold(
      appBar: AppBar(title: const Text('Guest Feedback')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.md,
              AppSpacing.sm,
              AppSpacing.md,
              0,
            ),
            // AppSearchField debounces internally (AppDurations.debounce), so
            // this screen does not run a second timer of its own.
            child: AppSearchField(
              hintText: 'Search guest, booking code…',
              initialValue: filters.search,
              onChanged: _onSearchChanged,
            ),
          ),
          _FilterRow(
            filters: filters,
            onChanged: (next) => controller.applyFilters(next),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: controller.refresh,
              child: AsyncValueView(
                value: state,
                // Skeleton rows are the real card shape fed placeholder data,
                // so the loading state has the same height as what replaces it
                // and the list does not jump when data arrives.
                loading: ListSkeleton(
                  separatorHeight: AppSpacing.sm,
                  itemBuilder: (_) =>
                      _FeedbackCard(feedback: _skeletonFeedback, onTap: () {}),
                ),
                onRetry: controller.refresh,
                data: (data) {
                  if (data.items.isEmpty) {
                    return EmptyState(
                      icon: Icons.reviews_outlined,
                      title: filters.isEmpty
                          ? 'No feedback yet'
                          : 'No feedback matches these filters',
                      message: filters.isEmpty
                          ? 'Guest responses appear here once a feedback link is submitted.'
                          : 'Try clearing the status or rating filter.',
                    );
                  }

                  return ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.fromLTRB(
                      AppSpacing.md,
                      AppSpacing.sm,
                      AppSpacing.md,
                      AppSpacing.xl,
                    ),
                    // One extra row for the paging spinner when more is coming.
                    itemCount: data.items.length + (data.hasMore ? 1 : 0),
                    itemBuilder: (context, index) {
                      if (index >= data.items.length) {
                        return const Padding(
                          padding: EdgeInsets.symmetric(vertical: AppSpacing.lg),
                          child: Center(
                            child: SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          ),
                        );
                      }

                      final feedback = data.items[index];
                      return _FeedbackCard(
                        feedback: feedback,
                        onTap: () => context.push(
                          Routes.feedbackDetailPath(feedback.feedbackId),
                        ),
                      );
                    },
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Status + rating chips. Horizontally scrollable so a large text scale can
/// push them past the viewport width without overflowing.
class _FilterRow extends StatelessWidget {
  const _FilterRow({required this.filters, required this.onChanged});

  final FeedbackFilters filters;
  final ValueChanged<FeedbackFilters> onChanged;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.md,
        vertical: AppSpacing.sm,
      ),
      child: Row(
        children: [
          AppFilterChip(
            label: 'All',
            selected: filters.reviewStatus == null && filters.rating == null,
            onTap: () => onChanged(FeedbackFilters(search: filters.search)),
          ),
          const SizedBox(width: AppSpacing.xs),
          for (final status in FeedbackReviewStatus.values) ...[
            AppFilterChip(
              label: status.label,
              selected: filters.reviewStatus == status,
              onTap: () => onChanged(
                filters.reviewStatus == status
                    ? filters.copyWith(clearReviewStatus: true)
                    : filters.copyWith(reviewStatus: status),
              ),
            ),
            const SizedBox(width: AppSpacing.xs),
          ],
          for (final stars in const [5, 4, 3, 2, 1]) ...[
            AppFilterChip(
              label: '$stars★',
              selected: filters.rating == stars,
              onTap: () => onChanged(
                filters.rating == stars
                    ? filters.copyWith(clearRating: true)
                    : filters.copyWith(rating: stars),
              ),
            ),
            const SizedBox(width: AppSpacing.xs),
          ],
        ],
      ),
    );
  }
}

class _FeedbackCard extends StatelessWidget {
  const _FeedbackCard({required this.feedback, required this.onTap});

  final GuestFeedback feedback;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return GlassCard(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              // Flexible, not fixed: a long guest name plus a large text scale
              // must ellipsize rather than overflow the row.
              Flexible(
                child: Text(
                  feedback.customerName ?? 'Unknown guest',
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: AppSpacing.sm),
              FeedbackStatusChip(status: feedback.reviewStatus),
            ],
          ),
          if (feedback.bookingCode != null) ...[
            const SizedBox(height: 2),
            Text(
              feedback.bookingCode!,
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
          const SizedBox(height: AppSpacing.sm),
          StarRating(rating: feedback.rating),
          if (feedback.comment != null && feedback.comment!.trim().isNotEmpty) ...[
            const SizedBox(height: AppSpacing.sm),
            Text(
              feedback.comment!,
              style: theme.textTheme.bodySmall,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ],
          const SizedBox(height: AppSpacing.sm),
          Row(
            children: [
              Icon(
                Icons.person_outline,
                size: 14,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  feedback.salesStaffName ?? 'Unassigned',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (feedback.submittedAt != null)
                Text(
                  Formatters.shortDate(feedback.submittedAt!),
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
